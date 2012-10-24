/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import com.db4o.ObjectContainer;
import freenet.client.*;
import freenet.client.InsertContext.CompatibilityMode;
import freenet.client.async.ClientContext;
import freenet.client.async.DBJob;
import freenet.client.async.DatabaseDisabledException;
import freenet.client.filter.ContentFilter;
import freenet.client.filter.KnownUnsafeContentTypeException;
import freenet.client.filter.MIMEType;
import freenet.clients.http.uielements.*;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.RequestStarter;
import freenet.node.SecurityLevels.PHYSICAL_THREAT_LEVEL;
import freenet.node.fcp.*;
import freenet.node.fcp.ClientPut.COMPRESS_STATE;
import freenet.node.useralerts.StoringUserEvent;
import freenet.node.useralerts.UserAlert;
import freenet.support.*;
import freenet.support.Logger.LogLevel;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.api.HTTPUploadedFile;
import freenet.support.io.*;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.*;

public class QueueToadlet extends Toadlet implements RequestCompletionCallback, LinkEnabledCallback {

	public enum QueueColumn {
		IDENTIFIER,
		SIZE,
		MIME_TYPE,
		PERSISTENCE,
		KEY,
		FILENAME,
		PRIORITY,
		FILES,
		TOTAL_SIZE,
		PROGRESS,
		REASON,
		LAST_ACTIVITY,
		COMPAT_MODE
	}

	private static final int MAX_IDENTIFIER_LENGTH = 1024*1024;
	static final int MAX_FILENAME_LENGTH = 1024*1024;
	private static final int MAX_TYPE_LENGTH = 1024;
	static final int MAX_KEY_LENGTH = 1024*1024;

	private NodeClientCore core;
	final FCPServer fcp;
	private FileInsertWizardToadlet fiw;

	private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	void setFIW(FileInsertWizardToadlet fiw) {
		this.fiw = fiw;
	}

	private boolean isReversed = false;
	private final boolean uploads;

	public QueueToadlet(NodeClientCore core, FCPServer fcp, HighLevelSimpleClient client, boolean uploads) {
		super(client);
		this.core = core;
		this.fcp = fcp;
		this.uploads = uploads;
		if(fcp == null) throw new NullPointerException();
		fcp.setCompletionCallback(this);
		try {
			loadCompletedIdentifiers();
		} catch (DatabaseDisabledException e) {
			// The user will know soon enough
		}
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, final ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {

		if(container.publicGatewayMode() && !ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"), NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}

		try {
			// Browse... button on upload page
			if (request.isPartSet("insert-local")) {
				
				FreenetURI insertURI;
				String keyType = request.getPartAsStringFailsafe("keytype", 10);
				if ("CHK".equals(keyType)) {
					insertURI = new FreenetURI("CHK@");
					if(fiw != null)
						fiw.reportCanonicalInsert();
				} else if("SSK".equals(keyType)) {
					insertURI = new FreenetURI("SSK@");
					if(fiw != null)
						fiw.reportRandomInsert();
				} else if("specify".equals(keyType)) {
					try {
						String u = request.getPartAsStringFailsafe("key", MAX_KEY_LENGTH);
						insertURI = new FreenetURI(u);
						if(logMINOR)
							Logger.minor(this, "Inserting key: "+insertURI+" ("+u+")");
					} catch (MalformedURLException mue1) {
						writeError(l10n("errorInvalidURI"),
						           l10n("errorInvalidURIToU"), ctx, false, true);
						return;
					}
				} else {
					writeError(l10n("errorMustSpecifyKeyTypeTitle"),
					           l10n("errorMustSpecifyKeyType"), ctx, false, true);
					return;
				}
				MultiValueTable<String, String> responseHeaders = new MultiValueTable<String, String>();
				responseHeaders.put("Location", LocalFileInsertToadlet.PATH+"?key="+insertURI.toASCIIString()+
				        "&compress="+String.valueOf(request.getPartAsStringFailsafe("compress", 128).length() > 0)+
				        "&compatibilityMode="+request.getPartAsStringFailsafe("compatibilityMode", 100)+
				        "&overrideSplitfileKey="+request.getPartAsStringFailsafe("overrideSplitfileKey", 65));
				ctx.sendReplyHeaders(302, "Found", responseHeaders, null, 0);
				return;
			} else if (request.isPartSet("select-location")) {
				try {
					throw new RedirectException(LocalDirectoryConfigToadlet.basePath()+"/downloads/");
				} catch (URISyntaxException e) {
					//Shouldn't happen, path is defined as such.
				}
			}
			String pass = request.getPartAsStringFailsafe("formPassword", 32);
			if ((pass.length() == 0) || !pass.equals(core.formPassword)) {
				MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
				headers.put("Location", path());
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				if(logMINOR) Logger.minor(this, "No formPassword: "+pass);
				return;
			}
			if (request.isPartSet("delete_request") &&
				(request.getPartAsStringFailsafe("delete_request", 128).length() > 0)) {
				// Confirm box
				PageNode page = ctx.getPageMaker().getPageNode(l10n("confirmDeleteTitle"), ctx);
				HTMLNode inner = page.content;
				InfoboxWidget ConfirmDelete =
					inner.addInfobox(InfoboxWidget.Type.WARNING, Identifier.CONFIRMDELETETITLE,
						l10n("confirmDeleteTitle"));
				HTMLNode deleteForm =
					ctx.addFormChild(ConfirmDelete.addBlockText(), path(), "queueDeleteForm");
				OutputList infoList = deleteForm.addList();
				for (String part : request.getParts()) {
					if (! part.startsWith("identifier-")) {
						continue;
					}
					part = part.substring("identifier-".length());
					if (part.length() > 50) {
						continue; // It's just a number
					}
					String identifier = request.getPartAsStringFailsafe("identifier-" + part,
						MAX_IDENTIFIER_LENGTH);
					if (identifier == null) {
						continue;
					}
					String filename = request.getPartAsStringFailsafe("filename-" + part,
						MAX_FILENAME_LENGTH);
					String keyString =
						request.getPartAsStringFailsafe("key-" + part, MAX_KEY_LENGTH);
					String type = request.getPartAsStringFailsafe("type-" + part,
						MAX_TYPE_LENGTH);
					String size = request.getPartAsStringFailsafe("size-" + part, 50);
					if (filename != null) {
						Item line = infoList.addItem();
						line.addText(
							NodeL10n.getBase().getString("FProxyToadlet.filenameLabel") +
								" ");
						if (keyString != null) {
							line.addLink("/" + keyString, filename);
						} else {
							line.addText(filename);
						}
					}
					if (type != null && ! type.equals("")) {
						boolean finalized = request.isPartSet("finalizedType");
						infoList.addItem().addText(NodeL10n.getBase().getString(
							"FProxyToadlet." +
								(finalized ? "mimeType" : "expectedMimeType"),
							new String[]{"mime"}, new String[]{type}));
					}
					if (size != null) {
						infoList.addItem().addText(
							NodeL10n.getBase().getString("FProxyToadlet.sizeLabel") + "" +
								" " +
								size);
					}
					infoList.addText(l10n("deleteFileFromTemp"));
					infoList.addChild("input", new String[]{"type", "name", "value", "checked"},
						new String[]{"checkbox", "identifier-" + part, identifier,
							"checked"});
				}
				ConfirmDelete.body.addBlockText(l10n("confirmDelete"));
				deleteForm.addChild("input",
					new String[]{"type", "name", "value"},
					new String[]{"submit", "remove_request",
						NodeL10n.getBase().getString("Toadlet.yes")});
				deleteForm.addChild("input",
					new String[]{"type", "name", "value"},
					new String[]{"submit", "cancel", NodeL10n.getBase().getString("Toadlet.no")});
				this.writeHTMLReply(ctx, 200, "OK", page.outer.generate());
				return;
			} else if(request.isPartSet("remove_request") && (request.getPartAsStringFailsafe("remove_request", 128).length() > 0)) {
				
				// FIXME optimise into a single database job.
				
				String identifier = "";
				try {
					for(String part : request.getParts()) {
						if(!part.startsWith("identifier-")) continue;
						identifier = part.substring("identifier-".length());
						if(identifier.length() > 50) continue;
						identifier = request.getPartAsStringFailsafe(part, MAX_IDENTIFIER_LENGTH);
						if(logMINOR) Logger.minor(this, "Removing "+identifier);
						fcp.removeGlobalRequestBlocking(identifier);
					}
				} catch (MessageInvalidException e) {
					this.sendErrorPage(ctx, 200,
							l10n("failedToRemoveRequest"),
							l10n("failedToRemove",
							        new String[]{ "id", "message" },
							        new String[]{ identifier, e.getMessage()}
							));
					return;
				} catch (DatabaseDisabledException e) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
			} else if(request.isPartSet("remove_finished_downloads_request") && (request.getPartAsStringFailsafe("remove_finished_downloads_request", 128).length() > 0)) {
				String identifier = "";
				try {
					RequestStatus[] reqs;
					reqs = fcp.getGlobalRequests();
					
					boolean hasIdentifier = false;
					for(String part : request.getParts()) {
						if(!part.startsWith("identifier-")) continue;
						hasIdentifier = true;
						identifier = part.substring("identifier-".length());
						if(identifier.length() > 50) continue;
						identifier = request.getPartAsStringFailsafe(part, MAX_IDENTIFIER_LENGTH);
						if(logMINOR) Logger.minor(this, "Removing "+identifier);
						fcp.removeGlobalRequestBlocking(identifier);
					}
					
					if(!hasIdentifier) { // delete all, because no identifier is given
						for(RequestStatus r : reqs) {
							if(r instanceof DownloadRequestStatus) {
								DownloadRequestStatus download = (DownloadRequestStatus)r;
								if(download.isPersistent() && download.hasSucceeded() && download.isTotalFinalized() && !download.toTempSpace()) {
									identifier = download.getIdentifier();
									fcp.removeGlobalRequestBlocking(identifier);
								}
							}
						}
					}
				} catch (MessageInvalidException e) {
					this.sendErrorPage(ctx, 200,
							l10n("failedToRemoveRequest"),
							l10n("failedToRemove",
							        new String[]{ "id", "message" },
							        new String[]{ identifier, e.getMessage()}
							));
					return;
				} catch (DatabaseDisabledException e) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
			}
			else if(request.isPartSet("restart_request") && (request.getPartAsStringFailsafe("restart_request", 128).length() > 0)) {
				boolean disableFilterData = request.isPartSet("disableFilterData");
				
				
				String identifier = "";
				for(String part : request.getParts()) {
					if(!part.startsWith("identifier-")) continue;
					identifier = part.substring("identifier-".length());
					if(identifier.length() > 50) continue;
					identifier = request.getPartAsStringFailsafe(part, MAX_IDENTIFIER_LENGTH);
					if(logMINOR) Logger.minor(this, "Restarting "+identifier);
					try {
						fcp.restartBlocking(identifier, disableFilterData);
					} catch (DatabaseDisabledException e) {
						sendPersistenceDisabledError(ctx);
						return;
					}
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
			} else if(request.isPartSet("panic") && (request.getPartAsStringFailsafe("panic", 128).length() > 0)) {
				if(SimpleToadletServer.noConfirmPanic) {
					core.node.killMasterKeysFile();
					core.node.panic();
					sendPanicingPage(ctx);
					core.node.finishPanic();
					return;
				} else {
					sendConfirmPanicPage(ctx);
					return;
				}
			} else if(request.isPartSet("confirmpanic") && (request.getPartAsStringFailsafe("confirmpanic", 128).length() > 0)) {
				core.node.killMasterKeysFile();
				core.node.panic();
				sendPanicingPage(ctx);
				core.node.finishPanic();
				return;
			} else if(request.isPartSet("download")) {
				// Queue a download
				if(!request.isPartSet("key")) {
					writeError(l10n("errorNoKey"), l10n("errorNoKeyToD"), ctx);
					return;
				}
				String expectedMIMEType = null;
				if(request.isPartSet("type")) {
					expectedMIMEType = request.getPartAsStringFailsafe("type", MAX_TYPE_LENGTH);
				}
				FreenetURI fetchURI;
				try {
					fetchURI = new FreenetURI(request.getPartAsStringFailsafe("key", MAX_KEY_LENGTH));
				} catch (MalformedURLException e) {
					writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToD"), ctx);
					return;
				}
				String persistence = request.getPartAsStringFailsafe("persistence", 32);
				String returnType = request.getPartAsStringFailsafe("return-type", 32);
				boolean filterData = request.isPartSet("filterData");
				String downloadPath;
				File downloadsDir = null;
				//Download to disk disabled and initialized.
				if (request.isPartSet("path") && !core.isDownloadDisabled()) {
					downloadPath = request.getPartAsStringFailsafe("path", MAX_FILENAME_LENGTH);
					try {
						downloadsDir = getDownloadsDir(downloadPath);
					} catch (NotAllowedException e) {
						downloadDisallowedPage(e, downloadPath, ctx);
						return;
					}
				//Downloading to disk not initialized and/or disabled.
				} else returnType = "direct";
				try {
					fcp.makePersistentGlobalRequestBlocking(fetchURI, filterData, expectedMIMEType, persistence, returnType, false, downloadsDir);
				} catch (NotAllowedException e) {
					this.writeError(l10n("QueueToadlet.errorDToDisk"), l10n("QueueToadlet.errorDToDiskConfig"), ctx);
					return;
				} catch (DatabaseDisabledException e) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
			} else if(request.isPartSet("bulkDownloads")) {
				String bulkDownloadsAsString = request.getPartAsStringFailsafe("bulkDownloads", 262144);
				String[] keys = bulkDownloadsAsString.split("\n");
				if(("".equals(bulkDownloadsAsString)) || (keys.length < 1)) {
					writePermanentRedirect(ctx, "Done", path());
					return;
				}
				LinkedList<String> success = new LinkedList<String>(), failure = new LinkedList<String>();
				boolean filterData = request.isPartSet("filterData");
				String target = request.getPartAsStringFailsafe("target", 128);
				if(target == null) target = "direct";
				String downloadPath;
				File downloadsDir = null;
				if (request.isPartSet("path") && !core.isDownloadDisabled()) {
					downloadPath = request.getPartAsStringFailsafe("path", MAX_FILENAME_LENGTH);
					try {
						downloadsDir = getDownloadsDir(downloadPath);
					} catch (NotAllowedException e) {
						downloadDisallowedPage(e, downloadPath, ctx);
						return;
					}
				} else target = "direct";
				for(int i=0; i<keys.length; i++) {
					String currentKey = keys[i];
					// trim leading/trailing space
					currentKey = currentKey.trim();
					if (currentKey.length() == 0)
						continue;
					try {
						FreenetURI fetchURI = new FreenetURI(currentKey);
						fcp.makePersistentGlobalRequestBlocking(fetchURI, filterData, null,
						        "forever", target, false, downloadsDir);
						success.add(fetchURI.toString(true, false));
					} catch (Exception e) {
						failure.add(currentKey);
						Logger.error(this,
						        "An error occured while attempting to download key("+i+") : "+
						        currentKey+ " : "+e.getMessage());
					}
				}
				boolean displayFailureBox = failure.size() > 0;
				boolean displaySuccessBox = success.size() > 0;
				PageNode page = ctx.getPageMaker().getPageNode(l10n("downloadFiles"), ctx);
				HTMLNode pageNode = page.outer;
				HTMLNode contentNode = page.content;
				InfoboxWidget alertContent = contentNode.addInfobox(
					(displayFailureBox ? InfoboxWidget.Type.WARNING :
						InfoboxWidget.Type.INFORMATION), Identifier.GROUPEDDOWNLOAD,
					l10n("downloadFiles"));
				Iterator<String> it;
				if (displaySuccessBox) {
					alertContent.body.addText(
						l10n("enqueuedSuccessfully", "number", String.valueOf(success.size
							())));
					OutputList successfulKeys = alertContent.body.addList();
					it = success.iterator();
					while (it.hasNext()) {
						successfulKeys.addItem((it.next()));
					}
				}
				if (displayFailureBox) {
					alertContent.body.addText(
						l10n("enqueuedFailure", "number", String.valueOf(failure.size())));
					OutputList failedKeys = alertContent.body.addList();
					it = failure.iterator();
					while (it.hasNext()) {
						failedKeys.addItem(it.next());
					}
				}
				alertContent.body.addLink(path(),
					NodeL10n.getBase().getString("Toadlet.returnToQueuepage"));
				writeHTMLReply(ctx, 200, "OK", pageNode.generate());
				return;
			} else if (request.isPartSet("change_priority")) {
				short newPriority = Short.parseShort(request.getPartAsStringFailsafe("priority", 32));
				String identifier = "";
				for(String part : request.getParts()) {
					if(!part.startsWith("identifier-")) continue;
					identifier = part.substring("identifier-".length());
					if(identifier.length() > 50) continue;
					identifier = request.getPartAsStringFailsafe(part, MAX_IDENTIFIER_LENGTH);
					try {
						fcp.modifyGlobalRequestBlocking(identifier, null, newPriority);
					} catch (DatabaseDisabledException e) {
						sendPersistenceDisabledError(ctx);
						return;
					}
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
				// FIXME factor out the next 3 items, they are very messy!
			} else if (request.getPartAsStringFailsafe("insert", 128).length() > 0) {
				final FreenetURI insertURI;
				String keyType = request.getPartAsStringFailsafe("keytype", 10);
				if ("CHK".equals(keyType)) {
					insertURI = new FreenetURI("CHK@");
					if(fiw != null)
						fiw.reportCanonicalInsert();
				} else if("SSK".equals(keyType)) {
					insertURI = new FreenetURI("SSK@");
					if(fiw != null)
						fiw.reportRandomInsert();
				} else if("specify".equals(keyType)) {
					try {
						String u = request.getPartAsStringFailsafe("key", MAX_KEY_LENGTH);
						insertURI = new FreenetURI(u);
						if(logMINOR)
							Logger.minor(this, "Inserting key: "+insertURI+" ("+u+")");
					} catch (MalformedURLException mue1) {
						writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx, false, true);
						return;
					}
				} else {
					writeError(l10n("errorMustSpecifyKeyTypeTitle"),
					           l10n("errorMustSpecifyKeyType"), ctx, false, true);
					return;
				}
				final HTTPUploadedFile file = request.getUploadedFile("filename");
				if (file == null || file.getFilename().trim().length() == 0) {
					writeError(l10n("errorNoFileSelected"), l10n("errorNoFileSelectedU"), ctx, false, true);
					return;
				}
				final boolean compress = request.getPartAsStringFailsafe("compress", 128).length() > 0;
				final String identifier = file.getFilename() + "-fred-" + System.currentTimeMillis();
				final String compatibilityMode = request.getPartAsStringFailsafe("compatibilityMode", 100);
				final CompatibilityMode cmode;
				if(compatibilityMode.equals(""))
					cmode = CompatibilityMode.COMPAT_CURRENT;
				else
					cmode = CompatibilityMode.valueOf(compatibilityMode);
				String s = request.getPartAsStringFailsafe("overrideSplitfileKey", 65);
				final byte[] overrideSplitfileKey;
				if(s != null && !s.equals(""))
					overrideSplitfileKey = HexUtil.hexToBytes(s);
				else
					overrideSplitfileKey = null;
				final String fnam;
				if(insertURI.getKeyType().equals("CHK") || keyType.equals("SSK"))
					fnam = file.getFilename();
				else
					fnam = null;
				/* copy bucket data */
				final Bucket copiedBucket = core.persistentTempBucketFactory.makeBucket(file.getData().size());
				BucketTools.copy(file.getData(), copiedBucket);
				final MutableBoolean done = new MutableBoolean();
				try {
					core.queue(new DBJob() {

						@Override
						public String toString() {
							return "QueueToadlet StartInsert";
						}

						@Override
						public boolean run(ObjectContainer container, ClientContext context) {
							try {
							final ClientPut clientPut;
							try {
								clientPut = new ClientPut(fcp.getGlobalForeverClient(), insertURI, identifier, Integer.MAX_VALUE, null, RequestStarter.BULK_SPLITFILE_PRIORITY_CLASS, ClientRequest.PERSIST_FOREVER, null, false, !compress, -1, ClientPutMessage.UPLOAD_FROM_DIRECT, null, file.getContentType(), copiedBucket, null, fnam, false, false, Node.FORK_ON_CACHEABLE_DEFAULT, HighLevelSimpleClientImpl.EXTRA_INSERTS_SINGLE_BLOCK, HighLevelSimpleClientImpl.EXTRA_INSERTS_SPLITFILE_HEADER, false, cmode, overrideSplitfileKey, fcp, container);
								if(clientPut != null)
									try {
										fcp.startBlocking(clientPut, container, context);
									} catch (IdentifierCollisionException e) {
										Logger.error(this, "Cannot put same file twice in same millisecond");
										writePermanentRedirect(ctx, "Done", path());
										return false;
									}
								writePermanentRedirect(ctx, "Done", path());
								return true;
							} catch (IdentifierCollisionException e) {
								Logger.error(this, "Cannot put same file twice in same millisecond");
								writePermanentRedirect(ctx, "Done", path());
								return false;
							} catch (NotAllowedException e) {
								writeError(l10n("errorAccessDenied"), l10n("errorAccessDeniedFile", "file", file.getFilename()), ctx, false, true);
								return false;
							} catch (FileNotFoundException e) {
								writeError(l10n("errorNoFileOrCannotRead"), l10n("errorAccessDeniedFile", "file", file.getFilename()), ctx, false, true);
								return false;
							} catch (MalformedURLException mue1) {
								writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx, false, true);
								return false;
							} catch (MetadataUnresolvedException e) {
								Logger.error(this, "Unresolved metadata in starting insert from data uploaded from browser: "+e, e);
								writePermanentRedirect(ctx, "Done", path());
								return false;
								// FIXME should this be a proper localised message? It shouldn't happen... but we'd like to get reports if it does.
							} catch (Throwable t) {
								writeInternalError(t, ctx);
								return false;
							} finally {
								synchronized(done) {
									done.value = true;
									done.notifyAll();
								}
							}
							} catch (IOException e) {
								// Ignore
								return false;
							} catch (ToadletContextClosedException e) {
								// Ignore
								return false;
							}
						}

					}, NativeThread.HIGH_PRIORITY+1, false);
				} catch (DatabaseDisabledException e1) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				synchronized(done) {
					while(!done.value)
						try {
							done.wait();
						} catch (InterruptedException e) {
							// Ignore
						}
				}
				return;
			} else if (request.isPartSet(LocalFileBrowserToadlet.selectFile)) {
				final String filename = request.getPartAsStringFailsafe("filename", MAX_FILENAME_LENGTH);
				if(logMINOR) Logger.minor(this, "Inserting local file: "+filename);
				final File file = new File(filename);
				final String identifier = file.getName() + "-fred-" + System.currentTimeMillis();
				final String contentType = DefaultMIMETypes.guessMIMEType(filename, false);
				final FreenetURI furi;
				final String key = request.getPartAsStringFailsafe("key", MAX_KEY_LENGTH);
				final boolean compress = request.isPartSet("compress");
				final String compatibilityMode = request.getPartAsStringFailsafe("compatibilityMode", 100);
				final CompatibilityMode cmode;
				if(compatibilityMode.equals(""))
					cmode = CompatibilityMode.COMPAT_CURRENT;
				else
					cmode = CompatibilityMode.valueOf(compatibilityMode);
				String s = request.getPartAsStringFailsafe("overrideSplitfileKey", 65);
				final byte[] overrideSplitfileKey;
				if(s != null && !s.equals(""))
					overrideSplitfileKey = HexUtil.hexToBytes(s);
				else
					overrideSplitfileKey = null;
				if(key != null) {
					try {
						furi = new FreenetURI(key);
					} catch (MalformedURLException e) {
						writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx);
						return;
					}
				} else {
					furi = new FreenetURI("CHK@");
				}
				final String target;
				if(furi.getDocName() != null)
					target = null;
				else
					target = file.getName();
				final MutableBoolean done = new MutableBoolean();
				try {
					core.queue(new DBJob() {

						@Override
						public String toString() {
							return "QueueToadlet StartLocalFileInsert";
						}

						@Override
						public boolean run(ObjectContainer container, ClientContext context) {
							final ClientPut clientPut;
							try {
							try {
								clientPut = new ClientPut(fcp.getGlobalForeverClient(), furi, identifier, Integer.MAX_VALUE, null, RequestStarter.BULK_SPLITFILE_PRIORITY_CLASS, ClientRequest.PERSIST_FOREVER, null, false, !compress, -1, ClientPutMessage.UPLOAD_FROM_DISK, file, contentType, new FileBucket(file, true, false, false, false, false), null, target, false, false, Node.FORK_ON_CACHEABLE_DEFAULT, HighLevelSimpleClientImpl.EXTRA_INSERTS_SINGLE_BLOCK, HighLevelSimpleClientImpl.EXTRA_INSERTS_SPLITFILE_HEADER, false, cmode, overrideSplitfileKey, fcp, container);
								if(logMINOR) Logger.minor(this, "Started global request to insert "+file+" to CHK@ as "+identifier);
								if(clientPut != null)
									try {
										fcp.startBlocking(clientPut, container, context);
									} catch (IdentifierCollisionException e) {
										Logger.error(this, "Cannot put same file twice in same millisecond");
										writePermanentRedirect(ctx, "Done", path());
										return false;
									} catch (DatabaseDisabledException e) {
										// Impossible???
									}
								writePermanentRedirect(ctx, "Done", path());
								return true;
							} catch (IdentifierCollisionException e) {
								Logger.error(this, "Cannot put same file twice in same millisecond");
								writePermanentRedirect(ctx, "Done", path());
								return false;
							} catch (MalformedURLException e) {
								writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx);
								return false;
							} catch (FileNotFoundException e) {
								writeError(l10n("errorNoFileOrCannotRead"), l10n("errorAccessDeniedFile", "file", target), ctx);
								return false;
							} catch (NotAllowedException e) {
								writeError(l10n("errorAccessDenied"), l10n("errorAccessDeniedFile", new String[]{ "file" }, new String[]{ file.getName() }), ctx);
								return false;
							} catch (MetadataUnresolvedException e) {
								Logger.error(this, "Unresolved metadata in starting insert from data from file: "+e, e);
								writePermanentRedirect(ctx, "Done", path());
								return false;
								// FIXME should this be a proper localised message? It shouldn't happen... but we'd like to get reports if it does.
							} finally {
								synchronized(done) {
									done.value = true;
									done.notifyAll();
								}
							}
							} catch (IOException e) {
								// Ignore
								return false;
							} catch (ToadletContextClosedException e) {
								// Ignore
								return false;
							}
						}

					}, NativeThread.HIGH_PRIORITY+1, false);
				} catch (DatabaseDisabledException e1) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				synchronized(done) {
					while(!done.value)
						try {
							done.wait();
						} catch (InterruptedException e) {
							// Ignore
						}
				}
				return;
			} else if (request.isPartSet(LocalFileBrowserToadlet.selectDir)) {
				final String filename = request.getPartAsStringFailsafe("filename", MAX_FILENAME_LENGTH);
				if(logMINOR) Logger.minor(this, "Inserting local directory: "+filename);
				final File file = new File(filename);
				final String identifier = file.getName() + "-fred-" + System.currentTimeMillis();
				final FreenetURI furi;
				final String key = request.getPartAsStringFailsafe("key", MAX_KEY_LENGTH);
				final boolean compress = request.isPartSet("compress");
				String s = request.getPartAsStringFailsafe("overrideSplitfileKey", 65);
				final byte[] overrideSplitfileKey;
				if(s != null && !s.equals(""))
					overrideSplitfileKey = HexUtil.hexToBytes(s);
				else
					overrideSplitfileKey = null;
				if(key != null) {
					try {
						furi = new FreenetURI(key);
					} catch (MalformedURLException e) {
						writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx);
						return;
					}
				} else {
					furi = new FreenetURI("CHK@");
				}
				final MutableBoolean done = new MutableBoolean();
				try {
					core.queue(new DBJob() {

						@Override
						public String toString() {
							return "QueueToadlet StartLocalDirInsert";
						}

						@Override
						public boolean run(ObjectContainer container, ClientContext context) {
							ClientPutDir clientPutDir;
							try {
							try {
								clientPutDir = new ClientPutDir(fcp.getGlobalForeverClient(), furi, identifier, Integer.MAX_VALUE, RequestStarter.BULK_SPLITFILE_PRIORITY_CLASS, ClientRequest.PERSIST_FOREVER, null, false, !compress, -1, file, null, false, true, false, false, Node.FORK_ON_CACHEABLE_DEFAULT, HighLevelSimpleClientImpl.EXTRA_INSERTS_SINGLE_BLOCK, HighLevelSimpleClientImpl.EXTRA_INSERTS_SPLITFILE_HEADER, false, overrideSplitfileKey, fcp, container);
								if(logMINOR) Logger.minor(this, "Started global request to insert dir "+file+" to "+furi+" as "+identifier);
								if(clientPutDir != null)
									try {
										fcp.startBlocking(clientPutDir, container, context);
									} catch (IdentifierCollisionException e) {
										Logger.error(this, "Cannot put same file twice in same millisecond");
										writePermanentRedirect(ctx, "Done", path());
										return false;
									} catch (DatabaseDisabledException e) {
										sendPersistenceDisabledError(ctx);
										return false;
									}
								writePermanentRedirect(ctx, "Done", path());
								return true;
							} catch (IdentifierCollisionException e) {
								Logger.error(this, "Cannot put same directory twice in same millisecond");
								writePermanentRedirect(ctx, "Done", path());
								return false;
							} catch (MalformedURLException e) {
								writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx);
								return false;
							} catch (FileNotFoundException e) {
								writeError(l10n("errorNoFileOrCannotRead"), l10n("QueueToadlet.errorAccessDeniedFile", "file", file.toString()), ctx);
								return false;
							} finally {
								synchronized(done) {
									done.value = true;
									done.notifyAll();
								}
							}
							} catch (IOException e) {
								// Ignore
								return false;
							} catch (ToadletContextClosedException e) {
								// Ignore
								return false;
							}
						}
					}, NativeThread.HIGH_PRIORITY+1, false);
				} catch (DatabaseDisabledException e1) {
					sendPersistenceDisabledError(ctx);
					return;
				}
				synchronized(done) {
					while(!done.value)
						try {
							done.wait();
						} catch (InterruptedException e) {
							// Ignore
						}
				}
				return;
			} else if (request.isPartSet("recommend_request")) {
				PageNode page = ctx.getPageMaker().getPageNode(l10n("recommendAFileToFriends"), ctx);
				HTMLNode pageNode = page.outer;
				HTMLNode contentNode = page.content;
				InfoboxWidget RecommendFile = contentNode
					.addInfobox(InfoboxWidget.Type.WTF, Identifier.RECOMMENDFILE,
						l10n("recommendAFileToFriends"));
				HTMLNode form = ctx.addFormChild(RecommendFile.body, path(), "recommendForm2");
				int x = 0;
				for (String part : request.getParts()) {
					if (! part.startsWith("identifier-")) {
						continue;
					}
					String key = request.getPartAsStringFailsafe(
						"key-" + part.substring("identifier-".length()), MAX_KEY_LENGTH);
					if (key == null || key.equals("")) {
						continue;
					}
					form.addText(l10n("key") + ":");
					form.addLineBreak();
					form.addText(key);
					form.addLineBreak();
					form.addChild("input", new String[]{"type", "name", "value"},
						new String[]{"hidden", "key-" + x, key});
				}
				form.addChild("label", "for", "descB", (l10n("recommendDescription") + ' '));
				form.addLineBreak();
				form.addChild("textarea",
					new String[]{"id", "name", "row", "cols"},
					new String[]{"descB", "description", "3", "70"});
				form.addLineBreak();
				Table peerTable = form.addTable(Category.DARKNETCONNECTIONS);
				peerTable.addRow().addHeader(2, l10n("recommendToFriends"));
				for (DarknetPeerNode peer : core.node.getDarknetConnections()) {
					Row peerRow = peerTable.addRow(Category.DARKNETCONNECTIONSNORMAL);
					peerRow.addCell(Category.PEERMARKER).addChild("input",
						new String[]{"type", "name"},
						new String[]{"checkbox", "node_" + peer.hashCode()});
					peerRow.addCell(Category.PEERNAME).addText(peer.getName());
				}
				form.addChild("input",
					new String[]{"type", "name", "value"},
					new String[]{"submit", "recommend_uri", l10n("recommend")});
				this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
				return;
			} else if(request.isPartSet("recommend_uri") && request.isPartSet("URI")) {
				String description = request.getPartAsStringFailsafe("description", 32768);
				ArrayList<FreenetURI> uris = new ArrayList<FreenetURI>();
				for(String part : request.getParts()) {
					if(!part.startsWith("key-")) continue;
					String key = request.getPartAsStringFailsafe(part, MAX_KEY_LENGTH);
					try {
						FreenetURI furi = new FreenetURI(key);
						uris.add(furi);
					} catch (MalformedURLException e) {
						writeError(l10n("errorInvalidURI"), l10n("errorInvalidURIToU"), ctx);
						return;
					}
				}
				
				for(DarknetPeerNode peer : core.node.getDarknetConnections()) {
					if(request.isPartSet("node_" + peer.hashCode())) {
						for(FreenetURI furi : uris)
							peer.sendDownloadFeed(furi, description);
					}
				}
				writePermanentRedirect(ctx, "Done", path());
				return;
			}
		} finally {
			request.freeParts();
		}
		this.handleMethodGET(uri, new HTTPRequestImpl(uri, "GET"), ctx);
	}

	private void downloadDisallowedPage(NotAllowedException e, String downloadPath, ToadletContext ctx)
		throws IOException, ToadletContextClosedException {
		PageNode page = ctx.getPageMaker().getPageNode(l10n("downloadFiles"), ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;
		Logger.warning(this, e.toString());
		InfoboxWidget alert = contentNode.addInfobox(InfoboxWidget.Type.ALERT, Identifier.GROUPEDDOWNLOAD,
			l10n("downloadFiles"));
		alert.addBlockText(l10n("downloadDisallowed", "directory", downloadPath));
		alert.addLink(path(), NodeL10n.getBase().getString("Toadlet.returnToQueuepage"));
		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	private File getDownloadsDir (String downloadPath) throws NotAllowedException {
		File downloadsDir = new File(downloadPath);
		//Invalid if it's disallowed, doesn't exist, isn't a directory, or can't be created.
		if(!core.allowDownloadTo(downloadsDir) || !((downloadsDir.exists() && 
				downloadsDir.isDirectory()) || !downloadsDir.mkdirs())) {
			throw new NotAllowedException();
		}
		return downloadsDir;
	}

	private void sendPanicingPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		writeHTMLReply(ctx, 200, "OK", WelcomeToadlet.sendRestartingPageInner(ctx).generate());
	}

	private void sendConfirmPanicPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode page = ctx.getPageMaker().getPageNode(l10n("confirmPanicButtonPageTitle"), ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;
		InfoboxWidget ConfirmPanic = contentNode.addInfobox(InfoboxWidget.Type.ERROR, Identifier.PANICCONFIRM,
			l10n("confirmPanicButtonPageTitle"));
		ConfirmPanic.body.addChild(new BlockText(l10n("confirmPanicButton")));
		HTMLNode form = ctx.addFormChild(ConfirmPanic.body, path(), "confirmPanicButton");
		form.addChild(new BlockText()).addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "confirmpanic", l10n("confirmPanicButtonYes")});
		form.addChild(new BlockText()).addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "noconfirmpanic", l10n("confirmPanicButtonNo")});
		if (uploads) {
			ConfirmPanic.body.addBlockText().addLink(path(), l10n("backToUploadsPage"));
		} else {
			ConfirmPanic.body.addBlockText().addLink(path(), l10n("backToDownloadsPage"));
		}
		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	private void sendPersistenceDisabledError(ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		String title = l10n("awaitingPasswordTitle" + (uploads ? "Uploads" : "Downloads"));
		if (core.node.awaitingPassword()) {
			PageNode page = ctx.getPageMaker().getPageNode(title, ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			InfoboxWidget PasswordPrompt = contentNode.addInfobox(InfoboxWidget.Type.ERROR, title);
			SecurityLevelsToadlet
				.generatePasswordFormPage(false, container, PasswordPrompt.body, false, false, false,
					null, path());
			addHomepageLink(PasswordPrompt.body);
			writeHTMLReply(ctx, 500, "Internal Server Error", pageNode.generate());
			return;
		}
		if (core.node.isStopping()) {
			sendErrorPage(ctx, 200,
				l10n("shuttingDownTitle"),
				l10n("shuttingDown"));
		} else {
			sendErrorPage(ctx, 200,
				l10n("persistenceBrokenTitle"),
				l10n("persistenceBroken",
					new String[]{"TEMPDIR", "DBFILE"},
					new String[]{FileUtil.getCanonicalFile(core.getPersistentTempDir())
						.toString() +
						File.separator,
						core.node.userDir().file("node.db4o").getCanonicalPath()}
				));
		}
	}

	private void writeError(String header, String message, ToadletContext context) throws ToadletContextClosedException, IOException {
		writeError(header, message, context, true, false);
	}

	private void writeError(String header, String message, ToadletContext context, boolean returnToQueuePage,
	                        boolean returnToInsertPage) throws ToadletContextClosedException, IOException {
		PageMaker pageMaker = context.getPageMaker();
		PageNode page = pageMaker.getPageNode(header, context);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;
		if (context.isAllowedFullAccess()) {
			contentNode.addChild(core.alerts.createSummary());
		}
		InfoboxWidget infoboxContent =
			contentNode.addInfobox(InfoboxWidget.Type.ERROR, Category.QUEUEERROR, header);
		infoboxContent.addText(message);
		if (returnToQueuePage) {
			NodeL10n.getBase()
				.addL10nSubstitution(infoboxContent.body.addBox(), "QueueToadlet.returnToQueuePage",
					new String[]{"link"}, new HTMLNode[]{new Link(path())});
		} else if (returnToInsertPage) {
			NodeL10n.getBase().addL10nSubstitution(infoboxContent.body.addBox(),
				"QueueToadlet.tryAgainUploadFilePage", new String[]{"link"},
				new HTMLNode[]{new Link(FileInsertWizardToadlet.PATH)});
		}
		writeHTMLReply(context, 400, "Bad request", pageNode.generate());
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, final ToadletContext ctx)
	throws ToadletContextClosedException, IOException, RedirectException {

		// We ensure that we have a FCP server running
		if(!fcp.enabled){
			writeError(l10n("fcpIsMissing"), l10n("pleaseEnableFCP"), ctx, false, false);
			return;
		}

		if(container.publicGatewayMode() && !ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"), NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}

		final String requestPath = request.getPath().substring(path().length());

		boolean countRequests = false;
		boolean listFetchKeys = false;

		if (requestPath.length() > 0) {
			if(requestPath.equals("countRequests.html") || requestPath.equals("/countRequests.html")) {
				countRequests = true;
			} else if(requestPath.equals("listFetchKeys.txt")) {
				listFetchKeys = true;
			}
		}

		class OutputWrapper {
			boolean done;
			HTMLNode pageNode;
			String plainText;
		}

		final OutputWrapper ow = new OutputWrapper();

		final PageMaker pageMaker = ctx.getPageMaker();

		final boolean count = countRequests;
		final boolean keys = listFetchKeys;
		
		if(!(count || keys)) {
			try {
				RequestStatus[] reqs = fcp.getGlobalRequests();
				MultiValueTable<String, String> pageHeaders = new MultiValueTable<String, String>();
				HTMLNode pageNode = handleGetInner(pageMaker, reqs, core.clientContext, request, ctx);
				writeHTMLReply(ctx, 200, "OK", pageHeaders, pageNode.generate());
				return;
			} catch (DatabaseDisabledException e) {
				sendPersistenceDisabledError(ctx);
				return;
			}
		}

		try {
			core.clientContext.jobRunner.queue(new DBJob() {

				@Override
				public String toString() {
					return "QueueToadlet ShowQueue";
				}

				@Override
				public boolean run(ObjectContainer container, ClientContext context) {
					HTMLNode pageNode = null;
					String plainText = null;
					try {
						if (count) {
							long queued = core.requestStarters.chkFetchSchedulerBulk
								.countPersistentWaitingKeys(container) +
								core.requestStarters.chkFetchSchedulerRT
									.countPersistentWaitingKeys(container);
							Logger.minor(this, "Total waiting CHKs: " + queued);
							long reallyQueued = core.requestStarters.chkFetchSchedulerBulk
								.countPersistentQueuedRequests(container) +
								core.requestStarters.chkFetchSchedulerRT
									.countPersistentQueuedRequests(container);
							Logger.minor(this,
								"Total queued CHK requests: " + reallyQueued);
							PageNode page = pageMaker.getPageNode(l10n("title"), ctx);
							pageNode = page.outer;
							HTMLNode contentNode = page.content;
							/* add alert summary box */
							if (ctx.isAllowedFullAccess())
								contentNode.addChild(core.alerts.createSummary());
							InfoboxWidget RequestStatus = contentNode.addInfobox(
								InfoboxWidget.Type.INFORMATION,
								"Queued requests status");
							RequestStatus.body.addBlockText(
								"Total awaiting CHKs: " + queued);
							RequestStatus.body.addBlockText(
								"Total queued CHK requests: " + reallyQueued);
							return false;
						} else if (keys) {
							try {
								plainText = makeFetchKeysList(context);
							} catch (DatabaseDisabledException e) {
								plainText = null;
							}
							return false;
						} else {
							try {
								RequestStatus[] reqs = fcp.getGlobalRequests();
								pageNode = handleGetInner(pageMaker, reqs, context,
									request, ctx);
							} catch (DatabaseDisabledException e) {
								pageNode = null;
							}
							return false;
						}
					} finally {
						synchronized (ow) {
							ow.done = true;
							ow.pageNode = pageNode;
							ow.plainText = plainText;
							ow.notifyAll();
						}
					}
				}
				// Do not use maximal priority: There may be exceptional cases which have higher priority than the UI, to get rid of excessive garbage for example.
			}, NativeThread.HIGH_PRIORITY, false);
		} catch (DatabaseDisabledException e1) {
			sendPersistenceDisabledError(ctx);
			return;
		}

		HTMLNode pageNode;
		String plainText;
		synchronized(ow) {
			while(true) {
				if(ow.done) {
					pageNode = ow.pageNode;
					plainText = ow.plainText;
					break;
				}
				try {
					ow.wait();
				} catch (InterruptedException e) {
					// Ignore
				}
			}
		}

		MultiValueTable<String, String> pageHeaders = new MultiValueTable<String, String>();
		if(pageNode != null)
			writeHTMLReply(ctx, 200, "OK", pageHeaders, pageNode.generate());
		else if(plainText != null)
			this.writeReply(ctx, 200, "text/plain", "OK", plainText);
		else {
			if(core.killedDatabase())
				sendPersistenceDisabledError(ctx);
			else
				this.writeError("Internal error", "Internal error", ctx);
		}

	}

	protected String makeFetchKeysList(ClientContext context) throws DatabaseDisabledException {
		RequestStatus[] reqs = fcp.getGlobalRequests();

		StringBuffer sb = new StringBuffer();

		for(int i=0;i<reqs.length;i++) {
			RequestStatus req = reqs[i];
			if(req instanceof DownloadRequestStatus) {
				DownloadRequestStatus get = (DownloadRequestStatus)req;
				FreenetURI uri = get.getURI();
				sb.append(uri.toString());
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	private HTMLNode handleGetInner(PageMaker pageMaker, RequestStatus[] reqs, ClientContext context, final HTTPRequest request, ToadletContext ctx) throws DatabaseDisabledException {

		// First, get the queued requests, and separate them into different types.
		LinkedList<DownloadRequestStatus> completedDownloadToDisk = new LinkedList<DownloadRequestStatus>();
		LinkedList<DownloadRequestStatus> completedDownloadToTemp = new LinkedList<DownloadRequestStatus>();
		LinkedList<UploadFileRequestStatus> completedUpload = new LinkedList<UploadFileRequestStatus>();
		LinkedList<UploadDirRequestStatus> completedDirUpload = new LinkedList<UploadDirRequestStatus>();

		LinkedList<DownloadRequestStatus> failedDownload = new LinkedList<DownloadRequestStatus>();
		LinkedList<UploadFileRequestStatus> failedUpload = new LinkedList<UploadFileRequestStatus>();
		LinkedList<UploadDirRequestStatus> failedDirUpload = new LinkedList<UploadDirRequestStatus>();

		LinkedList<DownloadRequestStatus> uncompletedDownload = new LinkedList<DownloadRequestStatus>();
		LinkedList<UploadFileRequestStatus> uncompletedUpload = new LinkedList<UploadFileRequestStatus>();
		LinkedList<UploadDirRequestStatus> uncompletedDirUpload = new LinkedList<UploadDirRequestStatus>();

		Map<String, LinkedList<DownloadRequestStatus>> failedUnknownMIMEType = new HashMap<String, LinkedList<DownloadRequestStatus>>();
		Map<String, LinkedList<DownloadRequestStatus>> failedBadMIMEType = new HashMap<String, LinkedList<DownloadRequestStatus>>();

		if(logMINOR)
			Logger.minor(this, "Request count: "+reqs.length);

		if (reqs.length < 1) {
			PageNode page = pageMaker.getPageNode(l10n("title" + (uploads ? "Uploads" : "Downloads")),
				ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			/* add alert summary box */
			if (ctx.isAllowedFullAccess()) {
				contentNode.addChild(core.alerts.createSummary());
			}
			contentNode.addInfobox(InfoboxWidget.Type.INFORMATION, Identifier.QUEUEEMPTY,
				l10n("globalQueueIsEmpty"), l10n("noTaskOnGlobalQueue"));
			if (! uploads) {
				contentNode.addInfobox(createBulkDownloadForm(ctx));
			}
			return pageNode;
		}
		short lowestQueuedPrio = RequestStarter.MINIMUM_PRIORITY_CLASS;
		long totalQueuedDownloadSize = 0;
		long totalQueuedUploadSize = 0;
		for(int i=0;i<reqs.length;i++) {
			RequestStatus req = reqs[i];
			if(req instanceof DownloadRequestStatus && !uploads) {
				DownloadRequestStatus download = (DownloadRequestStatus)req;
				if(download.hasSucceeded()) {
					if(download.toTempSpace())
						completedDownloadToTemp.add(download);
					else // to disk
						completedDownloadToDisk.add(download);
				} else if(download.hasFinished()) {
					int failureCode = download.getFailureCode();
					if(failureCode == FetchException.CONTENT_VALIDATION_UNKNOWN_MIME) {
						String mimeType = download.getMIMEType();
						mimeType = ContentFilter.stripMIMEType(mimeType);
						LinkedList<DownloadRequestStatus> list = failedUnknownMIMEType.get(mimeType);
						if(list == null) {
							list = new LinkedList<DownloadRequestStatus>();
							failedUnknownMIMEType.put(mimeType, list);
						}
						list.add(download);
					} else if(failureCode == FetchException.CONTENT_VALIDATION_BAD_MIME) {
						String mimeType = download.getMIMEType();
						mimeType = ContentFilter.stripMIMEType(mimeType);
						MIMEType type = ContentFilter.getMIMEType(mimeType);
						LinkedList<DownloadRequestStatus> list;
						if(type == null) {
							Logger.error(this, "Bad MIME failure code yet MIME is "+mimeType+" which does not have a handler!");
							list = failedUnknownMIMEType.get(mimeType);
							if(list == null) {
								list = new LinkedList<DownloadRequestStatus>();
								failedUnknownMIMEType.put(mimeType, list);
							}
						} else {
							list = failedBadMIMEType.get(mimeType);
							if(list == null) {
								list = new LinkedList<DownloadRequestStatus>();
								failedBadMIMEType.put(mimeType, list);
							}
						}
						list.add(download);
					} else {
						failedDownload.add(download);
					}
				} else {
					short prio = download.getPriority();
					if(prio < lowestQueuedPrio)
						lowestQueuedPrio = prio;
					uncompletedDownload.add(download);
					long size = download.getDataSize();
					if(size > 0)
						totalQueuedDownloadSize += size;
				}
			} else if(req instanceof UploadFileRequestStatus && uploads) {
				UploadFileRequestStatus upload = (UploadFileRequestStatus)req;
				if(upload.hasSucceeded()) {
					completedUpload.add(upload);
				} else if(upload.hasFinished()) {
					failedUpload.add(upload);
				} else {
					short prio = upload.getPriority();
					if(prio < lowestQueuedPrio)
						lowestQueuedPrio = prio;
					uncompletedUpload.add(upload);
				}
				long size = upload.getDataSize();
				if(size > 0)
					totalQueuedUploadSize += size;
			} else if(req instanceof UploadDirRequestStatus && uploads) {
				UploadDirRequestStatus upload = (UploadDirRequestStatus)req;
				if(upload.hasSucceeded()) {
					completedDirUpload.add(upload);
				} else if(upload.hasFinished()) {
					failedDirUpload.add(upload);
				} else {
					short prio = upload.getPriority();
					if(prio < lowestQueuedPrio)
						lowestQueuedPrio = prio;
					uncompletedDirUpload.add(upload);
				}
				long size = upload.getTotalDataSize();
				if(size > 0)
					totalQueuedUploadSize += size;
			}
		}
		Logger.minor(this, "Total queued downloads: "+SizeUtil.formatSize(totalQueuedDownloadSize));
		Logger.minor(this, "Total queued uploads: "+SizeUtil.formatSize(totalQueuedUploadSize));

		Comparator<RequestStatus> jobComparator = new Comparator<RequestStatus>() {
			@Override
			public int compare(RequestStatus firstRequest, RequestStatus secondRequest) {
				int result = 0;
				boolean isSet = true;

				if(request.isParameterSet("sortBy")){
					final String sortBy = request.getParam("sortBy");

					if(sortBy.equals("id")){
						result = firstRequest.getIdentifier().compareToIgnoreCase(secondRequest.getIdentifier());
					}else if(sortBy.equals("size")){
						result = (firstRequest.getTotalBlocks() - secondRequest.getTotalBlocks()) < 0 ? -1 : 1;
					}else if(sortBy.equals("progress")){
						boolean firstFinalized = firstRequest.isTotalFinalized();
						boolean secondFinalized = secondRequest.isTotalFinalized();
						if(firstFinalized && !secondFinalized)
							result = 1;
						else if(secondFinalized && !firstFinalized)
							result = -1;
						else
							result = (((double)firstRequest.getFetchedBlocks()) / ((double)firstRequest.getMinBlocks()) - ((double)secondRequest.getFetchedBlocks()) / ((double)secondRequest.getMinBlocks())) < 0 ? -1 : 1;
					} else if (sortBy.equals("lastActivity")) {
						result = (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, firstRequest.getLastActivity() - secondRequest.getLastActivity()));
					}else
						isSet=false;
				}else
					isSet=false;

				if(!isSet){
					int priorityDifference =  firstRequest.getPriority() - secondRequest.getPriority();
					if (priorityDifference != 0)
						result = (priorityDifference < 0 ? -1 : 1);
					else
						result = firstRequest.getIdentifier().compareTo(secondRequest.getIdentifier());
				}

				if(result == 0){
					return 0;
				}else if(request.isParameterSet("reversed")){
					isReversed = true;
					return result > 0 ? -1 : 1;
				}else{
					isReversed = false;
					return result < 0 ? -1 : 1;
				}
			}
		};

		Collections.sort(completedDownloadToDisk, jobComparator);
		Collections.sort(completedDownloadToTemp, jobComparator);
		Collections.sort(completedUpload, jobComparator);
		Collections.sort(completedDirUpload, jobComparator);
		Collections.sort(failedDownload, jobComparator);
		Collections.sort(failedUpload, jobComparator);
		Collections.sort(failedDirUpload, jobComparator);
		Collections.sort(uncompletedDownload, jobComparator);
		Collections.sort(uncompletedUpload, jobComparator);
		Collections.sort(uncompletedDirUpload, jobComparator);

		String pageName;
		if(uploads)
			pageName =
				"(" + (uncompletedDirUpload.size() + uncompletedUpload.size()) +
				'/' + (failedDirUpload.size() + failedUpload.size()) +
				'/' + (completedDirUpload.size() + completedUpload.size()) +
				") "+l10n("titleUploads");
		else
			pageName =
				"(" + uncompletedDownload.size() +
				'/' + failedDownload.size() +
				'/' + (completedDownloadToDisk.size() + completedDownloadToTemp.size()) +
				") "+l10n("titleDownloads");

		PageNode page = pageMaker.getPageNode(pageName, ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;

		/* add alert summary box */
		if(ctx.isAllowedFullAccess())
			contentNode.addChild(core.alerts.createSummary());

		/* navigation bar */
		InfoboxWidget navigationBar = new InfoboxWidget(InfoboxWidget.Type.NAVBAR, l10n("requestNavigation"));
		OutputList navigationContent = navigationBar.body.addList();
		boolean includeNavigationBar = false;
		if (! completedDownloadToTemp.isEmpty()) {
			navigationContent.addItem().addLink("#completedDownloadToTemp",
				l10n("completedDtoTemp", new String[]{"size"},
					new String[]{String.valueOf(completedDownloadToTemp.size())}));
			includeNavigationBar = true;
		}
		if (! completedDownloadToDisk.isEmpty()) {
			navigationContent.addItem().addLink("#completedDownloadToDisk",
				l10n("completedDtoDisk", new String[]{"size"},
					new String[]{String.valueOf(completedDownloadToDisk.size())}));
			includeNavigationBar = true;
		}
		if (! completedUpload.isEmpty()) {
			navigationContent.addItem().addLink("#completedUpload", l10n("completedU",
				new String[]{"size"},
				new String[]{String.valueOf(completedUpload.size())}));
			includeNavigationBar = true;
		}
		if (! completedDirUpload.isEmpty()) {
			navigationContent.addItem().addLink("#completedDirUpload",
				l10n("completedDU", new String[]{"size"},
					new String[]{String.valueOf(completedDirUpload.size())}));
			includeNavigationBar = true;
		}
		if (! failedDownload.isEmpty()) {
			navigationContent.addItem().addLink("#failedDownload", l10n("failedD", new String[]{"size"},
				new String[]{String.valueOf(failedDownload.size())}));
			includeNavigationBar = true;
		}
		if (! failedUpload.isEmpty()) {
			navigationContent.addItem().addLink("#failedUpload", l10n("failedU", new String[]{"size"},
				new String[]{String.valueOf(failedUpload.size())}));
			includeNavigationBar = true;
		}
		if (! failedDirUpload.isEmpty()) {
			navigationContent.addItem().addLink("#failedDirUpload", l10n("failedDU", new String[]{"size"},
				new String[]{String.valueOf(failedDirUpload.size())}));
			includeNavigationBar = true;
		}
		if (failedUnknownMIMEType.size() > 0) {
			String[] types =
				failedUnknownMIMEType.keySet().toArray(new String[failedUnknownMIMEType.size()]);
			Arrays.sort(types);
			for (String type : types) {
				String atype = type.replace("-", "--").replace('/', '-');
				navigationContent.addItem().addLink("#failedDownload-unknowntype-" + atype,
					l10n("failedDUnknownMIME", new String[]{"size", "type"},
						new String[]{String.valueOf(failedUnknownMIMEType.get(type).size()),
							type}));
			}
		}
		if (failedBadMIMEType.size() > 0) {
			String[] types = failedBadMIMEType.keySet().toArray(new String[failedBadMIMEType.size()]);
			Arrays.sort(types);
			for (String type : types) {
				String atype = type.replace("-", "--").replace('/', '-');
				navigationContent.addItem().addLink("#failedDownload-badtype-" + atype,
					l10n("failedDBadMIME", new String[]{"size", "type"},
						new String[]{String.valueOf(failedBadMIMEType.get(type).size()),
							type}));
			}
		}
		if (! uncompletedDownload.isEmpty()) {
			navigationContent.addItem().addLink("#uncompletedDownload",
				l10n("DinProgress", new String[]{"size"},
					new String[]{String.valueOf(uncompletedDownload.size())}));
			includeNavigationBar = true;
		}
		if (! uncompletedUpload.isEmpty()) {
			navigationContent.addItem().addLink("#uncompletedUpload",
				l10n("UinProgress", new String[]{"size"},
					new String[]{String.valueOf(uncompletedUpload.size())}));
			includeNavigationBar = true;
		}
		if (! uncompletedDirUpload.isEmpty()) {
			navigationContent.addItem().addLink("#uncompletedDirUpload",
				l10n("DUinProgress", new String[]{"size"},
					new String[]{String.valueOf(uncompletedDirUpload.size())}));
			includeNavigationBar = true;
		}
		if (totalQueuedDownloadSize > 0) {
			navigationContent.addItem(
				l10n("totalQueuedDownloads", "size", SizeUtil.formatSize(totalQueuedDownloadSize)));
			includeNavigationBar = true;
		}
		if (totalQueuedUploadSize > 0) {
			navigationContent.addItem(
				l10n("totalQueuedUploads", "size", SizeUtil.formatSize(totalQueuedUploadSize)));
			includeNavigationBar = true;
		}
		if (includeNavigationBar) {
			contentNode.addInfobox(navigationBar);
		}

		final String[] priorityClasses = new String[] {
				l10n("priority0"),
				l10n("priority1"),
				l10n("priority2"),
				l10n("priority3"),
				l10n("priority4"),
				l10n("priority5"),
				l10n("priority6")
		};

		boolean advancedModeEnabled = pageMaker.advancedMode(request, this.container);
		if (advancedModeEnabled) {
			Table legendTable =
				contentNode.addInfobox(InfoboxWidget.Type.LEGEND, Identifier.QUEUELEGEND, l10n("legend"))
					.addTable(Category.QUEUE);
			Row legendRow = legendTable.addRow();
			Cell cell;
			for (int i = 0; i < 7; i++) {
				if (i > RequestStarter.INTERACTIVE_PRIORITY_CLASS || advancedModeEnabled ||
					i <= lowestQueuedPrio) {
					cell = legendRow.addCell(priorityClasses[i]);
					cell.addAttribute("class", "priority" + i);
				}
			}
		}

		if (reqs.length > 1 && SimpleToadletServer.isPanicButtonToBeShown) {
			contentNode.addInfobox(createPanicBox(ctx));
		}

		final QueueColumn[] advancedModeFailure = new QueueColumn[] {
		        QueueColumn.IDENTIFIER,
		        QueueColumn.FILENAME,
		        QueueColumn.SIZE,
		        QueueColumn.MIME_TYPE,
		        QueueColumn.PROGRESS,
		        QueueColumn.REASON,
		        QueueColumn.PERSISTENCE,
		        QueueColumn.KEY };
		
		final QueueColumn[] simpleModeFailure = new QueueColumn[] {
		        QueueColumn.FILENAME,
		        QueueColumn.SIZE,
		        QueueColumn.PROGRESS,
		        QueueColumn.REASON,
		        QueueColumn.KEY };

		if (! completedDownloadToTemp.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.COMPLETEDDOWNLOADTOTEMP);
			InfoboxWidget completedDownloadsToTemp = contentNode
				.addInfobox(InfoboxWidget.Type.REQUESTCOMPLETE, Category.REQUESTCOMPLETED,
					l10n("completedDinTempDirectory", new String[]{"size"},
						new String[]{String.valueOf(completedDownloadToTemp.size())}));
			if (advancedModeEnabled) {
				completedDownloadsToTemp.body.addChild(
					createRequestTable(pageMaker, ctx, completedDownloadToTemp,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.SIZE,
							QueueColumn.MIME_TYPE, QueueColumn.PERSISTENCE,
							QueueColumn.KEY,
							QueueColumn.COMPAT_MODE}, priorityClasses,
						advancedModeEnabled,
						false, "completed-temp", true, true));
			} else {
				completedDownloadsToTemp.body.addChild(
					createRequestTable(pageMaker, ctx, completedDownloadToTemp,
						new QueueColumn[]{QueueColumn.SIZE, QueueColumn.KEY}, priorityClasses,
						advancedModeEnabled, false, "completed-temp", true, true));
			}
		}

		if (! completedDownloadToDisk.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.COMPLETEDDOWNLOADTODISK);
			InfoboxWidget completedToDiskInfoboxContent = contentNode
				.addInfobox(InfoboxWidget.Type.REQUESTCOMPLETE, Category.REQUESTCOMPLETED,
					l10n("completedDinDownloadDirectory", new String[]{"size"},
						new String[]{String.valueOf(completedDownloadToDisk.size())}));
			if (advancedModeEnabled) {
				completedToDiskInfoboxContent.body.addChild(
					createRequestTable(pageMaker, ctx, completedDownloadToDisk,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILENAME,
							QueueColumn.SIZE, QueueColumn.MIME_TYPE,
							QueueColumn.PERSISTENCE, QueueColumn.KEY,
							QueueColumn.COMPAT_MODE}, priorityClasses,
						advancedModeEnabled,
						false, "completed-disk", false, true));
			} else {
				completedToDiskInfoboxContent.body.addChild(
					createRequestTable(pageMaker, ctx, completedDownloadToDisk,
						new QueueColumn[]{QueueColumn.FILENAME, QueueColumn.SIZE,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, false,
						"completed-disk", false, true));
			}
		}

		if (! completedUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.COMPLETEDUPLOAD);
			InfoboxWidget completedUploadInfobox = contentNode
				.addInfobox(InfoboxWidget.Type.REQUESTCOMPLETE, Category.DOWNLOADCOMPLETE,
					l10n("completedU", new String[]{"size"},
						new String[]{String.valueOf(completedUpload.size())}));
			if (advancedModeEnabled) {
				completedUploadInfobox.body.addChild(createRequestTable(pageMaker, ctx,
					completedUpload,
					new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILENAME,
						QueueColumn.SIZE, QueueColumn.MIME_TYPE, QueueColumn.PERSISTENCE,
						QueueColumn.KEY}, priorityClasses, advancedModeEnabled, true,
					"completed-upload-file", false, true));
			} else {
				completedUploadInfobox.body.addChild(createRequestTable(pageMaker, ctx,
					completedUpload,
					new QueueColumn[]{QueueColumn.FILENAME, QueueColumn.SIZE, QueueColumn.KEY},
					priorityClasses, advancedModeEnabled, true, "completed-upload-file", false,
					true));
			}
		}

		if (! completedDirUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.COMPLETEDDIRUPLOAD);
			InfoboxWidget completedUploadDir = contentNode
				.addInfobox(InfoboxWidget.Type.REQUESTCOMPLETE, Category.DOWNLOADCOMPLETE,
					l10n("completedUDirectory", new String[]{"size"},
						new String[]{String.valueOf(completedDirUpload.size())}));
			if (advancedModeEnabled) {
				completedUploadDir.body.addChild(
					createRequestTable(pageMaker, ctx, completedDirUpload,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILES,
							QueueColumn.TOTAL_SIZE, QueueColumn.PERSISTENCE,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, true,
						"completed-upload-dir", false, true));
			} else {
				completedUploadDir.body.addChild(
					createRequestTable(pageMaker, ctx, completedDirUpload,
						new QueueColumn[]{QueueColumn.FILES, QueueColumn.TOTAL_SIZE,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, true,
						"completed-upload-dir", false, true));
			}
		}

		if (! failedDownload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.FAILEDDOWNLOAD);
			InfoboxWidget failedContent = contentNode.addInfobox(InfoboxWidget.Type.FAILEDREQUESTS,
				Category.DOWNLOADFAILED, l10n("failedD", new String[]{"size"},
				new String[]{String.valueOf(failedDownload.size())}));
			if (advancedModeEnabled) {
				failedContent.body.addChild(
					createRequestTable(pageMaker, ctx, failedDownload, advancedModeFailure,
						priorityClasses, advancedModeEnabled, false, "failed-download", false,
						true, false, false, null));
			} else {
				failedContent.body.addChild(
					createRequestTable(pageMaker, ctx, failedDownload, simpleModeFailure,
						priorityClasses, advancedModeEnabled, false, "failed-download", false,
						true, false, false, null));
			}
		}

		if (! failedUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.FAILEDUPLOAD);
			InfoboxWidget failedContent = contentNode.addInfobox(InfoboxWidget.Type.FAILEDREQUESTS,
				Category.UPLOADFAILED, l10n("failedU", new String[]{"size"},
				new String[]{String.valueOf(failedUpload.size())}));
			if (advancedModeEnabled) {
				failedContent.body.addChild(
					createRequestTable(pageMaker, ctx, failedUpload, advancedModeFailure,
						priorityClasses, advancedModeEnabled, true, "failed-upload-file",
						false, true, false, false, null));
			} else {
				failedContent.body.addChild(
					createRequestTable(pageMaker, ctx, failedUpload, simpleModeFailure,
						priorityClasses, advancedModeEnabled, true, "failed-upload-file",
						false, true, false, false, null));
			}
		}

		if (! failedDirUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.FAILEDDIRUPLOAD);
			InfoboxWidget failedContent = contentNode.addInfobox(InfoboxWidget.Type.FAILEDREQUESTS,
				Category.UPLOADFAILED, l10n("failedU", new String[]{"size"},
				new String[]{String.valueOf(failedDirUpload.size())}));
			if (advancedModeEnabled) {
				failedContent.body.addChild(createRequestTable(pageMaker, ctx, failedDirUpload,
					new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILES,
						QueueColumn.TOTAL_SIZE, QueueColumn.PROGRESS, QueueColumn.REASON,
						QueueColumn.PERSISTENCE, QueueColumn.KEY}, priorityClasses,
					advancedModeEnabled, true, "failed-upload-dir", false, true, false, false,
					null));
			} else {
				failedContent.body.addChild(createRequestTable(pageMaker, ctx, failedDirUpload,
					new QueueColumn[]{QueueColumn.FILES, QueueColumn.TOTAL_SIZE,
						QueueColumn.PROGRESS, QueueColumn.REASON, QueueColumn.KEY},
					priorityClasses, advancedModeEnabled, true, "failed-upload-dir", false, true,
					false, false, null));
			}
		}

		if (! failedBadMIMEType.isEmpty()) {
			String[] types = failedBadMIMEType.keySet().toArray(new String[failedBadMIMEType.size()]);
			Arrays.sort(types);
			for (String type : types) {
				LinkedList<DownloadRequestStatus> getters = failedBadMIMEType.get(type);
				String atype = type.replace("-", "--").replace('/', '-');
				contentNode.addLink(Link.Type.ANCHOR, "failedDownload-badtype-" + atype);
				MIMEType typeHandler = ContentFilter.getMIMEType(type);
				InfoboxWidget failedContent = contentNode.addInfobox(InfoboxWidget.Type
					.FAILEDREQUESTS,
					l10n("failedDBadMIME", new String[]{"size", "type"},
						new String[]{String.valueOf(getters.size()), type}));
				failedContent.addClass("download-failed-" + atype);
				// FIXME add a class for easier styling.
				KnownUnsafeContentTypeException e = new KnownUnsafeContentTypeException(typeHandler);
				failedContent.body.addChild(new BlockText(l10n("badMIMETypeIntro", "type", type)));
				List<String> detail = e.details();
				if (detail != null && ! detail.isEmpty()) {
					OutputList list = new OutputList();
					failedContent.body.addChild(list);
					for (String s : detail) {
						list.addItem(s);
					}
				}
				failedContent.body.addChild(new BlockText(l10n("mimeProblemFetchAnyway")));
				Collections.sort(getters, jobComparator);
				if (advancedModeEnabled) {
					failedContent.body.addChild(createRequestTable(pageMaker, ctx, getters,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILENAME,
							QueueColumn.SIZE, QueueColumn.PERSISTENCE, QueueColumn.KEY},
						priorityClasses, advancedModeEnabled, false,
						"failed-download-file-badmime", false, true, true, false, type));
				} else {
					failedContent.body.addChild(createRequestTable(pageMaker, ctx, getters,
						new QueueColumn[]{QueueColumn.FILENAME, QueueColumn.SIZE,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, false,
						"failed-download-file-badmime", false, true, true, false, type));
				}
			}
		}

		if (! failedUnknownMIMEType.isEmpty()) {
			String[] types =
				failedUnknownMIMEType.keySet().toArray(new String[failedUnknownMIMEType.size()]);
			Arrays.sort(types);
			for (String type : types) {
				LinkedList<DownloadRequestStatus> getters = failedUnknownMIMEType.get(type);
				String atype = type.replace("-", "--").replace('/', '-');
				contentNode.addLink(Link.Type.ANCHOR, "failedDownload-unknowntype-" + atype);
				InfoboxWidget failedContent = contentNode.addInfobox(InfoboxWidget.Type
					.FAILEDREQUESTS,
					l10n("failedDUnknownMIME", new String[]{"size", "type"},
						new String[]{String.valueOf(getters.size()), type}));
				failedContent.addClass("failedDownload-unknowntype-" + atype);
				// FIXME add a class for easier styling.
				failedContent.body.addChild(new BlockText(NodeL10n.getBase()
					.getString("UnknownContentTypeException.explanation", "type", type)));
				failedContent.body.addChild(new BlockText(l10n("mimeProblemFetchAnyway")));
				Collections.sort(getters, jobComparator);
				if (advancedModeEnabled) {
					failedContent.body.addChild(createRequestTable(pageMaker, ctx, getters,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILENAME,
							QueueColumn.SIZE, QueueColumn.PERSISTENCE, QueueColumn.KEY},
						priorityClasses, advancedModeEnabled, false,
						"failed-download-file-unknownmime", false, true, true, false, type));
				} else {
					failedContent.body.addChild(createRequestTable(pageMaker, ctx, getters,
						new QueueColumn[]{QueueColumn.FILENAME, QueueColumn.SIZE,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, false,
						"failed-download-file-unknownmime", false, true, true, false, type));
				}
			}
		}

		if (! uncompletedDownload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.UNCOMPLETEDDOWNLOAD);
			InfoboxWidget uncompletedContent =
				contentNode.addInfobox(InfoboxWidget.Type.PROGRESSING, Category.DOWNLOADPROGRESSING,
					l10n("wipD", new String[]{"size"},
						new String[]{String.valueOf(uncompletedDownload.size())}));
			if (advancedModeEnabled) {
				uncompletedContent.body.addChild(createRequestTable(pageMaker, ctx,
					uncompletedDownload,
					new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.PRIORITY,
						QueueColumn.SIZE, QueueColumn.MIME_TYPE, QueueColumn.PROGRESS,
						QueueColumn.LAST_ACTIVITY, QueueColumn.PERSISTENCE,
						QueueColumn.FILENAME, QueueColumn.KEY, QueueColumn.COMPAT_MODE},
					priorityClasses, advancedModeEnabled, false, "uncompleted-download", false,
					false));
			} else {
				uncompletedContent.body.addChild(createRequestTable(pageMaker, ctx,
					uncompletedDownload,
					new QueueColumn[]{QueueColumn.SIZE, QueueColumn.PROGRESS,
						QueueColumn.LAST_ACTIVITY, QueueColumn.KEY}, priorityClasses,
					advancedModeEnabled, false, "uncompleted-download", false, false));
			}
		}

		if (! uncompletedUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.UNCOMPLETEDUPLOAD);
			InfoboxWidget uncompletedContent =
				contentNode.addInfobox(InfoboxWidget.Type.PROGRESSING, Category.UPLOADPROGRESSING,
					l10n("wipU", new String[]{"size"},
						new String[]{String.valueOf(uncompletedUpload.size())}));
			uncompletedContent.addClass(Category.DOWNLOADPROGRESSING);
			if (advancedModeEnabled) {
				uncompletedContent.body.addChild(createRequestTable(pageMaker, ctx, uncompletedUpload,
					new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.PRIORITY,
						QueueColumn.SIZE, QueueColumn.MIME_TYPE, QueueColumn.PROGRESS,
						QueueColumn.LAST_ACTIVITY, QueueColumn.PERSISTENCE,
						QueueColumn.FILENAME, QueueColumn.KEY}, priorityClasses,
					advancedModeEnabled, true, "uncompleted-upload-file", false, false));
			} else {
				uncompletedContent.body.addChild(createRequestTable(pageMaker, ctx, uncompletedUpload,
					new QueueColumn[]{QueueColumn.FILENAME, QueueColumn.SIZE,
						QueueColumn.PROGRESS,
						QueueColumn.LAST_ACTIVITY, QueueColumn.KEY}, priorityClasses,
					advancedModeEnabled, true, "uncompleted-upload-file", false, false));
			}
		}

		if (! uncompletedDirUpload.isEmpty()) {
			contentNode.addLink(Link.Type.ANCHOR, Identifier.UNCOMPLETEDDIRUPLOAD);
			InfoboxWidget uncompletedContent =
				contentNode.addInfobox(InfoboxWidget.Type.PROGRESSING, Category.DOWNLOADPROGRESSING,
					l10n("wipDU", new String[]{"size"},
						new String[]{String.valueOf(uncompletedDirUpload.size())}));
			if (advancedModeEnabled) {
				uncompletedContent.body.addChild(
					createRequestTable(pageMaker, ctx, uncompletedDirUpload,
						new QueueColumn[]{QueueColumn.IDENTIFIER, QueueColumn.FILES,
							QueueColumn.PRIORITY, QueueColumn.TOTAL_SIZE,
							QueueColumn.PROGRESS, QueueColumn.LAST_ACTIVITY,
							QueueColumn.PERSISTENCE, QueueColumn.KEY}, priorityClasses,
						advancedModeEnabled, true, "uncompleted-upload-dir", false, false));
			} else {
				uncompletedContent.body.addChild(
					createRequestTable(pageMaker, ctx, uncompletedDirUpload,
						new QueueColumn[]{QueueColumn.FILES, QueueColumn.TOTAL_SIZE,
							QueueColumn.PROGRESS, QueueColumn.LAST_ACTIVITY,
							QueueColumn.KEY}, priorityClasses, advancedModeEnabled, true,
						"uncompleted-upload-dir", false, false));
			}
		}

		if(!uploads) {
			contentNode.addInfobox(createBulkDownloadForm(ctx));
		}

		return pageNode;
	}

	private Cell createReasonCell(String failureReason) {
		Cell reasonCell = new Cell(Category.REQUESTREASON);
		if (failureReason == null) {
			reasonCell.addInlineBox(Category.FAILUREREASONUNKNOWN, l10n("unknown"));
		} else {
			reasonCell.addInlineBox(Category.FAILUREREASONKNOWN, failureReason);
		}
		return reasonCell;
	}

	private HTMLNode createProgressCell(boolean started, COMPRESS_STATE compressing, int fetched, int failed, int fatallyFailed, int min, int total, boolean finalized, boolean upload) {
		boolean advancedMode = core.isAdvancedModeEnabled();
		return createProgressCell(advancedMode, started, compressing, fetched, failed, fatallyFailed, min, total, finalized, upload);
	}

	public static Cell createProgressCell(boolean advancedMode, boolean started, COMPRESS_STATE compressing, int fetched, int failed, int fatallyFailed, int min, int total, boolean finalized, boolean upload) {
		Cell progressCell = new Cell(Category.REQUESTPROGRESS);
		if (!started) {
			progressCell.addText(l10n("starting"));
			return progressCell;
		}
		if(compressing == COMPRESS_STATE.WAITING && advancedMode) {
			progressCell.addText(l10n("awaitingCompression"));
			return progressCell;
		}
		if(compressing != COMPRESS_STATE.WORKING) {
			progressCell.addText(l10n("compressing"));
			return progressCell;
		}

		//double frac = p.getSuccessFraction();
		if (!advancedMode || total < min /* FIXME why? */) {
			total = min;
		}

		if ((fetched < 0) || (total <= 0)) {
			progressCell.addInlineBox(Category.PROGRESSFRACTIONUNKNOWN, l10n("unknown"));
		} else {
			int fetchedPercent = (int) (fetched / (double) total * 100);
			int failedPercent = (int) (failed / (double) total * 100);
			int fatallyFailedPercent = (int) (fatallyFailed / (double) total * 100);
			int minPercent = (int) (min / (double) total * 100);
			HTMLNode progressBar = progressCell.addChild(new Box(Category.PROGRESSBAR));
			Box completed = new Box(Category.PROGRESSBARDONE);
			completed.addAttribute("style", "width: " + fetchedPercent + "%;" );
			progressBar.addChild(completed);

			if (failed > 0) {
				Box subbar = new Box(Category.PROGRESSBARFAILED);
				subbar.addAttribute("style", "width: " + failedPercent + "%;");
				progressBar.addChild(subbar);
			}
			if (fatallyFailed > 0) {
				Box subbar = new Box(Category.PROGRESSBARFAILED2);
				subbar.addAttribute("style", "width: " + fatallyFailedPercent + "%;");
				progressBar.addChild(subbar);
			}
			if ((fetched + failed + fatallyFailed) < min) {
				Box subbar = new Box(Category.PROGRESSBARMIN);
				subbar.addAttribute("style", "width: " + (minPercent - fetchedPercent) + "%;");
				progressBar.addChild(subbar);
			}

			NumberFormat nf = NumberFormat.getInstance();
			nf.setMaximumFractionDigits(1);
			String prefix = '('+Integer.toString(fetched) + "/ " + Integer.toString(min)+"): ";
			if (finalized) {
				Box fractionFinalized = new Box(Category.PROGRESSBARFINAL, nf.format((int) ((fetched / (double) min) * 1000) / 10.0) + '%');
				fractionFinalized.addAttribute("title", prefix + l10n("progressbarAccurate"));
				progressBar.addChild(fractionFinalized);
			} else {
				String text = nf.format((int) ((fetched / (double) min) * 1000) / 10.0)+ '%';
				if(!finalized) {
					text = "" + fetched + " ("+text+"??)";
				}
				Box fractionNotFinalized = new Box(Category.PROGRESSBARNOTFINAL, text);
				fractionNotFinalized.addAttribute("title", prefix + NodeL10n.getBase().getString(upload ? "QueueToadlet.uploadProgressbarNotAccurate" : "QueueToadlet.progressbarNotAccurate") );
				progressBar.addChild(fractionNotFinalized);


			}
		}
		return progressCell;
	}

	private Cell createNumberCell(int numberOfFiles) {
		Cell numberCell = new Cell(Category.REQUESTFILES);
		numberCell.addInlineBox(Category.NUMBEROFFILES, String.valueOf(numberOfFiles));
		return numberCell;
	}

	private Cell createFilenameCell(File filename) {
		Cell filenameCell = new Cell(Category.REQUESTFILENAME);
		if (filename != null) {
			filenameCell.addInlineBox(Category.FILENAMEIS, filename.toString());
		} else {
			filenameCell.addInlineBox(Category.FILENAMENONE, l10n("none"));
		}
		return filenameCell;
	}

	private Cell createPriorityCell(short priorityClass, String[] priorityClasses) {
		Cell priorityCell = new Cell(Category.REQUESTPRIORITY);
		if(priorityClass < 0 || priorityClass >= priorityClasses.length) {
			priorityCell.addInlineBox(Category.PRIORITYUNKNOWN, l10n("unknown"));
		} else {
			priorityCell.addInlineBox(Category.PRIORITYKNOWN, priorityClasses[priorityClass]);
		}
		return priorityCell;
	}

	private HTMLNode createPriorityControl(PageMaker pageMaker, ToadletContext ctx, short priorityClass, String[] priorityClasses, boolean advancedModeEnabled, boolean isUpload) {
		Box priorityBox = new Box(Category.REQUESTPRIORITY);
		priorityBox.addClass(Category.NOWRAP);
		priorityBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "change_priority", NodeL10n.getBase().getString(isUpload ? "QueueToadlet.changeUploadPriorities" : "QueueToadlet.changeDownloadPriorities") });
		HTMLNode prioritySelect = priorityBox.addChild("select", "name", "priority");
		for (int p = 0; p < RequestStarter.NUMBER_OF_PRIORITY_CLASSES; p++) {
			if(p <= RequestStarter.INTERACTIVE_PRIORITY_CLASS && !advancedModeEnabled) continue;
			if (p == priorityClass) {
				prioritySelect.addChild("option", new String[] { "value", "selected" }, new String[] { String.valueOf(p), "selected" }, priorityClasses[p]);
			} else {
				prioritySelect.addChild("option", "value", String.valueOf(p), priorityClasses[p]);
			}
		}
		return priorityBox;
	}
	
	private HTMLNode createRecommendControl(PageMaker pageMaker, ToadletContext ctx) {
		Box recommendBox = new Box(Category.REQUESTRECOMMEND);
		recommendBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "recommend_request", l10n("recommendFilesToFriends") });
		return recommendBox;
	}

	private HTMLNode createRemoveFinishedDownloadsControl( PageMaker pageMaker, ToadletContext ctx ) {
		Box deleteBox = new Box(Category.REQUESTDELETEFINISHEDDOWNLOADS);
		deleteBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "remove_finished_downloads_request", l10n("removeFinishedDownloads") });
		return deleteBox;
	}
	
	/** Create a delete or restart control at the top of a table. It applies to whichever requests are checked in the table below. */
	private HTMLNode createDeleteControl(PageMaker pageMaker, ToadletContext ctx, boolean isDownloadToTemp, boolean canRestart, boolean disableFilterChecked, boolean isUpload, String mimeType) {
		Box deleteBox = new Box(Category.REQUESTDELETE);
		if(isDownloadToTemp) {
			deleteBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "delete_request", l10n("deleteFilesFromTemp") });
		} else {
			deleteBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "remove_request", l10n("removeFilesFromList") });
		}
		if(canRestart) {
			deleteBox.addLineBreak();
			// FIXME: Split stuff with a permanent redirect to a separate grouping and use QueueToadlet.follow here?
			String restartName = NodeL10n.getBase().getString(/*followRedirect ? "QueueToadlet.follow" : */ isUpload ? "QueueToadlet.restartUploads" : "QueueToadlet.restartDownloads");
			deleteBox.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "restart_request", restartName });
			if(mimeType != null) {
				HTMLNode input = deleteBox.addChild("input", new String[] { "type", "name", "value" }, new String[] {"checkbox", "disableFilterData", "disableFilterData" });
				if(disableFilterChecked) {
					input.addAttribute("checked", "checked");
				}
				deleteBox.addText(l10n("disableFilter", "type", mimeType));
			}
		}
		return deleteBox;
	}

	private InfoboxWidget createPanicBox(ToadletContext ctx) {
		InfoboxWidget infobox =
			new InfoboxWidget(InfoboxWidget.Type.ALERT, Identifier.PANICBUTTON, l10n("panicButtonTitle"));
		HTMLNode panicForm = ctx.addFormChild(infobox.body, path(), "queuePanicForm");
		panicForm.addText((SimpleToadletServer.noConfirmPanic ? l10n("panicButtonNoConfirmation") :
			l10n("panicButtonWithConfirmation")) + ' ');
		panicForm.addChild("input", new String[]{"type", "name", "value"},
			new String[]{"submit", "panic", l10n("panicButton")});
		return infobox;
	}

	private Cell createIdentifierCell(FreenetURI uri, String identifier, boolean directory) {
		Cell identifierCell = new Cell(Category.REQUESTIDENTIFIER);
		if (uri != null) {
			identifierCell.addInlineBox(Category.IDENTIFIERWITHURI).addLink("/" + uri + (directory ? "/" : ""), identifier);
		} else {
			identifierCell.addInlineBox(Category.IDENTIFIERWITHOUTURI, identifier);
		}
		return identifierCell;
	}

	private Cell createPersistenceCell(boolean persistent, boolean persistentForever) {
		Cell persistenceCell = new Cell(Category.REQUESTPERSISTENCE);
		if (persistentForever) {
			persistenceCell.addInlineBox(Category.PERSISTENCEFOREVER, l10n("persistenceForever"));
		} else if (persistent) {
			persistenceCell.addInlineBox(Category.PERSISTENCEREBOOT, l10n("persistenceReboot"));
		} else {
			persistenceCell.addInlineBox(Category.PERSISTENCENONE, l10n("persistenceNone"));
		}
		return persistenceCell;
	}

	private Cell createTypeCell(String type) {
		Cell typeCell = new Cell(Category.REQUESTTYPE);
		if (type != null) {
			typeCell.addInlineBox(Category.MIMETYPEKNOWN, type);
		} else {
			typeCell.addInlineBox(Category.MIMETYPEUNKNOWN, l10n("unknown"));
		}
		return typeCell;
	}

	private Cell createSizeCell(long dataSize, boolean confirmed, boolean advancedModeEnabled) {
		Cell sizeCell = new Cell(Category.REQUESTSIZE);
		if (dataSize > 0 && (confirmed || advancedModeEnabled)) {
			sizeCell.addInlineBox(Category.FILESIZEKNOWN, (confirmed ? "" : ">= ") + SizeUtil.formatSize(dataSize) + (confirmed ? "" : " ??"));
		} else {
			sizeCell.addInlineBox(Category.FILESIZEUNKNOWN, l10n("unknown"));
		}
		return sizeCell;
	}

	private Cell createKeyCell(FreenetURI uri, boolean addSlash) {
		Cell keyCell = new Cell(Category.REQUESTKEY);
		if (uri != null) {
			keyCell.addInlineBox(Category.KEYIS).addLink('/' + uri.toString() + (addSlash ? "/" : ""), uri.toShortString() + (addSlash ? "/" : ""));
		} else {
			keyCell.addInlineBox(Category.KEYUNKNOWN, l10n("unknown"));
		}
		return keyCell;
	}

	private InfoboxWidget createBulkDownloadForm(ToadletContext ctx) {
		InfoboxWidget GroupedDownloads =
			new InfoboxWidget(InfoboxWidget.Type.NONE, Identifier.GROUPEDDOWNLOAD, l10n("downloadFiles"));
		HTMLNode downloadForm = ctx.addFormChild(GroupedDownloads.body, path(), "queueDownloadForm");
		downloadForm.addText(l10n("downloadFilesInstructions"));
		downloadForm.addLineBreak();
		downloadForm.addChild("textarea",
			new String[]{"id", "name", "cols", "rows"},
			new String[]{"bulkDownloads", "bulkDownloads", "120", "8"});
		downloadForm.addLineBreak();
		PHYSICAL_THREAT_LEVEL threatLevel = core.node.securityLevels.getPhysicalThreatLevel();
		//Force downloading to encrypted space if high/maximum threat level or if the user has disabled
		//downloading to disk.
		if (threatLevel == PHYSICAL_THREAT_LEVEL.HIGH || threatLevel == PHYSICAL_THREAT_LEVEL.MAXIMUM ||
			core.isDownloadDisabled()) {
			downloadForm.addChild("input",
				new String[]{"type", "name", "value"},
				new String[]{"hidden", "target", "direct"});
		} else if (threatLevel == PHYSICAL_THREAT_LEVEL.LOW) {
			downloadForm.addChild("input",
				new String[]{"type", "name", "value"},
				new String[]{"hidden", "target", "disk"});
			selectLocation(downloadForm);
		} else {
			downloadForm.addLineBreak();
			downloadForm.addChild("input",
				new String[]{"type", "value", "name"},
				new String[]{"radio", "disk", "target"},
				//Nicer spacing for radio button
				' ' + l10n("bulkDownloadSelectOptionDisk") + ' ');
			selectLocation(downloadForm);
			downloadForm.addLineBreak();
			downloadForm.addChild("input",
				new String[]{"type", "value", "name", "checked"},
				new String[]{"radio", "direct", "target", "checked"},
				' ' + l10n("bulkDownloadSelectOptionDirect") + ' ');
		}
		HTMLNode filterControl = downloadForm.addChild(new Box(Category.NONE, l10n("filterData")));
		filterControl.addChild("input",
			new String[]{"type", "name", "value", "checked"},
			new String[]{"checkbox", "filterData", "filterData", "checked"});
		filterControl.addText(l10n("filterDataMessage"));
		downloadForm.addLineBreak();
		downloadForm.addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "insert", l10n("download")});
		return GroupedDownloads;
	}

	private void selectLocation(HTMLNode node) {
		String downloadLocation = core.getDownloadsDir().getAbsolutePath();
		//If the download directory isn't allowed, yet downloading is, at least one directory must
		//have been explicitly defined, so take the first one.
		if (!core.allowDownloadTo(core.getDownloadsDir())) {
			downloadLocation = core.getAllowedDownloadDirs()[0].getAbsolutePath();
		}
		node.addChild("input",
		        new String[] { "type", "name", "value", "maxlength", "size" },
		        new String[] { "text", "path", downloadLocation, Integer.toString(MAX_FILENAME_LENGTH),
		                String.valueOf(downloadLocation.length())});
		node.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "select-location", l10n("browseToChange")+"..." });
	}

	/**
	 * Creates a table cell that contains the time of the last activity, as per
	 * {@link TimeUtil#formatTime(long)}.
	 *
	 * @param now
	 *            The current time (for a unified point of reference for the
	 *            whole page)
	 * @param lastActivity
	 *            The last activity of the request
	 * @return The created table cell HTML node
	 */
	private Cell createLastActivityCell(long now, long lastActivity) {
		Cell lastActivityCell = new Cell(Category.REQUESTLASTACTIVITY);
		if (lastActivity == 0) {
			lastActivityCell.addI(l10n("lastActivity.unknown"));
		} else {
			lastActivityCell.addText(l10n("lastActivity.ago", "time", TimeUtil.formatTime(now - lastActivity)));
		}
		return lastActivityCell;
	}

	private HTMLNode createRequestTable(PageMaker pageMaker, ToadletContext ctx, List<? extends RequestStatus> requests, QueueColumn[] columns, String[] priorityClasses, boolean advancedModeEnabled, boolean isUpload, String id, boolean isDownloadToTemp, boolean isCompleted) {
		return createRequestTable(pageMaker, ctx, requests, columns, priorityClasses, advancedModeEnabled, isUpload, id, isDownloadToTemp, false, false, isCompleted, null);
	}
	
	private HTMLNode createRequestTable(PageMaker pageMaker, ToadletContext ctx, List<? extends RequestStatus> requests, QueueColumn[] columns, String[] priorityClasses, boolean advancedModeEnabled, boolean isUpload, String id, boolean isDownloadToTemp, boolean isFailed, boolean isDisableFilterChecked, boolean isCompleted, String mimeType) {
		boolean hasFriends = core.node.getDarknetConnections().length > 0;
		boolean isFinishedDiskDownloads = isCompleted && !isUpload && !isDownloadToTemp && !isFailed;
		long now = System.currentTimeMillis();
		
		Box formBox = new Box(Category.REQUESTTABLEFORM);
		HTMLNode form = ctx.addFormChild(formBox, path(), "request-table-form-"+id+(advancedModeEnabled?"-advanced":"-simple"));
		
		if( isFinishedDiskDownloads ) {
			form.addChild(createRemoveFinishedDownloadsControl(pageMaker, ctx));
		} else {
			form.addChild(createDeleteControl(pageMaker, ctx, isDownloadToTemp, isFailed, isDisableFilterChecked, isUpload, mimeType));
		}
		if(hasFriends && !(isUpload && isFailed))
			form.addChild(createRecommendControl(pageMaker, ctx));
		if(advancedModeEnabled && !(isFailed || isCompleted))
			form.addChild(createPriorityControl(pageMaker, ctx, RequestStarter.BULK_SPLITFILE_PRIORITY_CLASS, priorityClasses, advancedModeEnabled, isUpload));

		Table table = new Table(Category.REQUESTS);
		form.addChild(table);
		Row headerRow = table.addRow(Category.TABLEHEADER);

		// Checkbox header
		headerRow.addHeader(); // No description

		//Add a header for each column.
		for (QueueColumn column : columns) {
			switch (column) {
				case IDENTIFIER:
					headerRow.addHeader().addLink((isReversed ? "?sortBy=id" : "?sortBy=id&reversed")).addText(l10n("identifier"));
					break;
				case SIZE:
					headerRow.addHeader().addLink((isReversed ? "?sortBy=size" : "?sortBy=size&reversed")).addText(l10n("size"));
					break;
				case MIME_TYPE:
					headerRow.addHeader(l10n("mimeType"));
					break;
				case PERSISTENCE:
					headerRow.addHeader(l10n("persistence"));
					break;
				case KEY:
					headerRow.addHeader(l10n("key"));
					break;
				case FILENAME:
					headerRow.addHeader(l10n("fileName"));
					break;
				case PRIORITY:
					headerRow.addHeader(l10n("priority"));
					break;
				case FILES:
					headerRow.addHeader(l10n("files"));
					break;
				case TOTAL_SIZE:
					headerRow.addHeader(l10n("totalSize"));
					break;
				case PROGRESS:
					headerRow.addHeader().addLink((isReversed ? "?sortBy=progress" : "?sortBy=progress&reversed")).addText(l10n("progress"));
					break;
				case REASON:
					headerRow.addHeader(l10n("reason"));
					break;
				case LAST_ACTIVITY:
					headerRow.addHeader().addLink((isReversed ? "?sortBy=lastActivity" : "?sortBy=lastActivity&reversed"), l10n("lastActivity"));
					break;
				case COMPAT_MODE:
					headerRow.addHeader(l10n("compatibilityMode"));
					break;
			}
		}
		//Add a row with a checkbox for each request.
		int x = 0;
		for (RequestStatus clientRequest : requests) {
			Row requestRow = table.addRow();
			requestRow.addAttribute("style", "priority" + clientRequest.getPriority());
			requestRow.addChild(createCheckboxCell(clientRequest, x++));

			for (QueueColumn column : columns) {
				switch (column) {
					case IDENTIFIER:
						requestRow.addChild(createIdentifierCell(clientRequest.getURI(), clientRequest.getIdentifier(), clientRequest instanceof UploadDirRequestStatus));
						break;
					case SIZE:
						boolean isFinal = true;
						if(clientRequest instanceof DownloadRequestStatus)
							isFinal = ((DownloadRequestStatus)clientRequest).isTotalFinalized();
						requestRow.addChild(createSizeCell(clientRequest.getDataSize(), isFinal, advancedModeEnabled));
						break;
					case MIME_TYPE:
						if (clientRequest instanceof DownloadRequestStatus) {
							requestRow.addChild(createTypeCell(((DownloadRequestStatus) clientRequest).getMIMEType()));
						} else if (clientRequest instanceof UploadFileRequestStatus) {
							requestRow.addChild(createTypeCell(((UploadFileRequestStatus) clientRequest).getMIMEType()));
						}
						break;
					case PERSISTENCE:
						requestRow.addChild(createPersistenceCell(clientRequest.isPersistent(), clientRequest.isPersistentForever()));
						break;
					case KEY:
						if (clientRequest instanceof DownloadRequestStatus) {
							requestRow.addChild(createKeyCell(((DownloadRequestStatus) clientRequest).getURI(), false));
						} else if (clientRequest instanceof UploadFileRequestStatus) {
							requestRow.addChild(createKeyCell(((UploadFileRequestStatus) clientRequest).getFinalURI(), false));
						}else {
							requestRow.addChild(createKeyCell(((UploadDirRequestStatus) clientRequest).getFinalURI(), true));
						}
						break;
					case FILENAME:
						if (clientRequest instanceof DownloadRequestStatus) {
							requestRow.addChild(createFilenameCell(((DownloadRequestStatus) clientRequest).getDestFilename()));
						} else if (clientRequest instanceof UploadFileRequestStatus) {
							requestRow.addChild(createFilenameCell(((UploadFileRequestStatus) clientRequest).getOrigFilename()));
						}
						break;
					case PRIORITY:
						requestRow.addChild(createPriorityCell(clientRequest.getPriority(), priorityClasses));
						break;
					case FILES:
						requestRow.addChild(createNumberCell(((UploadDirRequestStatus) clientRequest).getNumberOfFiles()));
						break;
					case TOTAL_SIZE:
						requestRow.addChild(createSizeCell(((UploadDirRequestStatus) clientRequest).getTotalDataSize(), true, advancedModeEnabled));
						break;
					case PROGRESS:
						if(clientRequest instanceof UploadFileRequestStatus)
							requestRow.addChild(createProgressCell(clientRequest.isStarted(), ((UploadFileRequestStatus)clientRequest).isCompressing(), clientRequest.getFetchedBlocks(), clientRequest.getFailedBlocks(), clientRequest.getFatalyFailedBlocks(), clientRequest.getMinBlocks(), clientRequest.getTotalBlocks(), clientRequest.isTotalFinalized() || clientRequest instanceof UploadFileRequestStatus, isUpload));
						else
							requestRow.addChild(createProgressCell(clientRequest.isStarted(), COMPRESS_STATE.WORKING, clientRequest.getFetchedBlocks(), clientRequest.getFailedBlocks(), clientRequest.getFatalyFailedBlocks(), clientRequest.getMinBlocks(), clientRequest.getTotalBlocks(), clientRequest.isTotalFinalized() || clientRequest instanceof UploadFileRequestStatus, isUpload));
						break;
					case REASON:
						requestRow.addChild(createReasonCell(clientRequest.getFailureReason(false)));
						break;
					case LAST_ACTIVITY:
						requestRow.addChild(createLastActivityCell(now, clientRequest.getLastActivity()));
						break;
					case COMPAT_MODE:
						if(clientRequest instanceof DownloadRequestStatus) {
							requestRow.addChild(createCompatModeCell((DownloadRequestStatus)clientRequest));
						} else {
							requestRow.addCell();
						}
						break;
				}
			}
		}
		return formBox;
	}

	private Cell createCheckboxCell(RequestStatus clientRequest, int counter) {
		Cell cell = new Cell(Category.CHECKBOXCELL);
		String identifier = clientRequest.getIdentifier();
		cell.addChild("input", new String[] { "type", "name", "value" },
				new String[] { "checkbox", "identifier-"+counter, identifier } );
		FreenetURI uri;
		long size = -1;
		String filename = null;
		if(clientRequest instanceof DownloadRequestStatus) {
			uri = clientRequest.getURI();
			size = clientRequest.getDataSize();
		} else if(clientRequest instanceof UploadRequestStatus) {
			uri = ((UploadRequestStatus)clientRequest).getFinalURI();
			size = clientRequest.getDataSize();
		} else {
			uri = null;
		}
		if(uri != null) {
			cell.addChild("input", new String[] { "type", "name", "value" },
					new String[] { "hidden", "key-"+counter, uri.toASCIIString() });
			filename = uri.getPreferredFilename();
		}
		if(size != -1)
			cell.addChild("input", new String[] { "type", "name", "value" },
					new String[] { "hidden", "size-"+counter, Long.toString(size) });
		if(filename != null)
			cell.addChild("input", new String[] { "type", "name", "value" },
					new String[] { "hidden", "filename-"+counter, filename });
		return cell;
	}

	private Cell createCompatModeCell(DownloadRequestStatus get) {
		Cell compatCell = new Cell(Category.REQUESTCOMPATMODE);
		InsertContext.CompatibilityMode[] compat = get.getCompatibilityMode();
		if(!(compat[0] == InsertContext.CompatibilityMode.COMPAT_UNKNOWN && compat[1] == InsertContext.CompatibilityMode.COMPAT_UNKNOWN)) {
			if(compat[0] == compat[1])
				compatCell.addText(NodeL10n.getBase().getString("InsertContext.CompatibilityMode."+compat[0].name())); // FIXME l10n
			else
				compatCell.addText(NodeL10n.getBase().getString("InsertContext.CompatibilityMode."+compat[0].name())+" - "+NodeL10n.getBase().getString("InsertContext.CompatibilityMode."+compat[1].name())); // FIXME l10n
			byte[] overrideCryptoKey = get.getOverriddenSplitfileCryptoKey();
			if(overrideCryptoKey != null)
				compatCell.addText(" - "+l10n("overriddenCryptoKeyInCompatCell")+": "+HexUtil.bytesToHex(overrideCryptoKey));
			if(get.detectedDontCompress())
				compatCell.addText(" ("+l10n("dontCompressInCompatCell")+")");
		}
		return compatCell;
	}

	/**
	 * List of completed request identifiers which the user hasn't acknowledged yet.
	 */
	private final HashSet<String> completedRequestIdentifiers = new HashSet<String>();

	private final Map<String, GetCompletedEvent> completedGets = new LinkedHashMap<String, GetCompletedEvent>();
	private final Map<String, PutCompletedEvent> completedPuts = new LinkedHashMap<String, PutCompletedEvent>();
	private final Map<String, PutDirCompletedEvent> completedPutDirs = new LinkedHashMap<String, PutDirCompletedEvent>();

	@Override
	public void notifyFailure(ClientRequest req, ObjectContainer container) {
		// FIXME do something???
	}

	@Override
	public void notifySuccess(ClientRequest req, ObjectContainer container) {
		if(uploads == req instanceof ClientGet) return;
		synchronized(completedRequestIdentifiers) {
			completedRequestIdentifiers.add(req.getIdentifier());
		}
		registerAlert(req, container); // should be safe here
		saveCompletedIdentifiersOffThread();
	}

	private void saveCompletedIdentifiersOffThread() {
		core.getExecutor().execute(new Runnable() {
			@Override
			public void run() {
				saveCompletedIdentifiers();
			}
		}, "Save completed identifiers");
	}

	private void loadCompletedIdentifiers() throws DatabaseDisabledException {
		String dl = uploads ? "uploads" : "downloads";
		File completedIdentifiersList = core.node.userDir().file("completed.list."+dl);
		File completedIdentifiersListNew = core.node.userDir().file("completed.list."+dl+".bak");
		File oldCompletedIdentifiersList = core.node.userDir().file("completed.list");
		boolean migrated = false;
		if(!readCompletedIdentifiers(completedIdentifiersList)) {
			if(!readCompletedIdentifiers(completedIdentifiersListNew)) {
				readCompletedIdentifiers(oldCompletedIdentifiersList);
				migrated = true;
			}
		} else
			oldCompletedIdentifiersList.delete();
		final boolean writeAnyway = migrated;
		core.clientContext.jobRunner.queue(new DBJob() {

			@Override
			public String toString() {
				return "QueueToadlet LoadCompletedIdentifiers";
			}

			@Override
			public boolean run(ObjectContainer container, ClientContext context) {
				String[] identifiers;
				boolean changed = writeAnyway;
				synchronized(completedRequestIdentifiers) {
					identifiers = completedRequestIdentifiers.toArray(new String[completedRequestIdentifiers.size()]);
				}
				for(int i=0;i<identifiers.length;i++) {
					ClientRequest req = fcp.getGlobalRequest(identifiers[i], container);
					if(req == null || req instanceof ClientGet == uploads) {
						synchronized(completedRequestIdentifiers) {
							completedRequestIdentifiers.remove(identifiers[i]);
						}
						changed = true;
						continue;
					}
					registerAlert(req, container);
				}
				if(changed) saveCompletedIdentifiers();
				return false;
			}

		}, NativeThread.HIGH_PRIORITY, false);
	}

	private boolean readCompletedIdentifiers(File file) {
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis);
			InputStreamReader isr = new InputStreamReader(bis, "UTF-8");
			BufferedReader br = new BufferedReader(isr);
			synchronized(completedRequestIdentifiers) {
				completedRequestIdentifiers.clear();
				while(true) {
					String identifier = br.readLine();
					if(identifier == null) return true;
					completedRequestIdentifiers.add(identifier);
				}
			}
		} catch (EOFException e) {
			// Normal
			return true;
		} catch (FileNotFoundException e) {
			// Normal
			return false;
		} catch (UnsupportedEncodingException e) {
			throw new Error("Impossible: JVM doesn't support UTF-8: " + e, e);
		} catch (IOException e) {
			Logger.error(this, "Could not read completed identifiers list from "+file);
			return false;
		} finally {
			Closer.close(fis);
		}
	}

	private void saveCompletedIdentifiers() {
		FileOutputStream fos = null;
		BufferedWriter bw = null;
		String dl = uploads ? "uploads" : "downloads";
		File completedIdentifiersList = core.node.userDir().file("completed.list."+dl);
		File completedIdentifiersListNew = core.node.userDir().file("completed.list."+dl+".bak");
		File temp;
		try {
			temp = File.createTempFile("completed.list", ".tmp", core.node.getUserDir());
			temp.deleteOnExit();
			fos = new FileOutputStream(temp);
			OutputStreamWriter osw = new OutputStreamWriter(fos, "UTF-8");
			bw = new BufferedWriter(osw);
			String[] identifiers;
			synchronized(completedRequestIdentifiers) {
				identifiers = completedRequestIdentifiers.toArray(new String[completedRequestIdentifiers.size()]);
			}
			for(int i=0;i<identifiers.length;i++)
				bw.write(identifiers[i]+'\n');
		} catch (FileNotFoundException e) {
			Logger.error(this, "Unable to save completed requests list (can't find node directory?!!?): "+e, e);
			return;
		} catch (IOException e) {
			Logger.error(this, "Unable to save completed requests list: "+e, e);
			return;
		} finally {
			if(bw != null) {
				try {
					bw.close();
				} catch (IOException e) {
					try {
						fos.close();
					} catch (IOException e1) {
						// Ignore
					}
				}
			} else {
				try {
					fos.close();
				} catch (IOException e1) {
					// Ignore
				}
			}
		}
		completedIdentifiersListNew.delete();
		temp.renameTo(completedIdentifiersListNew);
		if(!completedIdentifiersListNew.renameTo(completedIdentifiersList)) {
			completedIdentifiersList.delete();
			if(!completedIdentifiersListNew.renameTo(completedIdentifiersList)) {
				Logger.error(this, "Unable to store completed identifiers list because unable to rename "+completedIdentifiersListNew+" to "+completedIdentifiersList);
			}
		}
	}

	private void registerAlert(ClientRequest req, ObjectContainer container) {
		final String identifier = req.getIdentifier();
		if(logMINOR)
			Logger.minor(this, "Registering alert for "+identifier);
		if(!req.hasFinished()) {
			if(logMINOR)
				Logger.minor(this, "Request hasn't finished: "+req+" for "+identifier, new Exception("debug"));
			return;
		}
		if(req instanceof ClientGet) {
			FreenetURI uri = ((ClientGet)req).getURI(container);
			if(req.isPersistentForever() && uri != null)
				container.activate(uri, 5);
			if(uri == null) {
				Logger.error(this, "No URI for supposedly finished request "+req);
				return;
			}
			long size = ((ClientGet)req).getDataSize(container);
			GetCompletedEvent event = new GetCompletedEvent(identifier, uri, size);
			synchronized(completedGets) {
				completedGets.put(identifier, event);
			}
			core.alerts.register(event);
		} else if(req instanceof ClientPut) {
			FreenetURI uri = ((ClientPut)req).getFinalURI(container);
			if(req.isPersistentForever() && uri != null)
				container.activate(uri, 5);
			if(uri == null) {
				Logger.error(this, "No URI for supposedly finished request "+req);
				return;
			}
			long size = ((ClientPut)req).getDataSize(container);
			PutCompletedEvent event = new PutCompletedEvent(identifier, uri, size);
			synchronized(completedPuts) {
				completedPuts.put(identifier, event);
			}
			core.alerts.register(event);
		} else if(req instanceof ClientPutDir) {
			FreenetURI uri = ((ClientPutDir)req).getFinalURI(container);
			if(req.isPersistentForever() && uri != null)
				container.activate(uri, 5);
			if(uri == null) {
				Logger.error(this, "No URI for supposedly finished request "+req);
				return;
			}
			long size = ((ClientPutDir)req).getTotalDataSize();
			int files = ((ClientPutDir)req).getNumberOfFiles();
			PutDirCompletedEvent event = new PutDirCompletedEvent(identifier, uri, size, files);
			synchronized(completedPutDirs) {
				completedPutDirs.put(identifier, event);
			}
			core.alerts.register(event);
		}
	}

	static String l10n(String key) {
		return NodeL10n.getBase().getString("QueueToadlet."+key);
	}

	static String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("QueueToadlet."+key, pattern, value);
	}

	static String l10n(String key, String[] pattern, String[] value) {
		return NodeL10n.getBase().getString("QueueToadlet."+key, pattern, value);
	}

	@Override
	public void onRemove(ClientRequest req, ObjectContainer container) {
		String identifier = req.getIdentifier();
		synchronized(completedRequestIdentifiers) {
			completedRequestIdentifiers.remove(identifier);
		}
		if(req instanceof ClientGet)
			synchronized(completedGets) {
				completedGets.remove(identifier);
			}
		else if(req instanceof ClientPut)
			synchronized(completedPuts) {
				completedPuts.remove(identifier);
			}
		else if(req instanceof ClientPutDir)
			synchronized(completedPutDirs) {
				completedPutDirs.remove(identifier);
			}
		saveCompletedIdentifiersOffThread();
	}

	@Override
	public boolean isEnabled(ToadletContext ctx) {
		return (!container.publicGatewayMode()) || ((ctx != null) && ctx.isAllowedFullAccess());
	}

	static final String PATH_UPLOADS = "/uploads/";
	static final String PATH_DOWNLOADS = "/downloads/";
	
	static final HTMLNode DOWNLOADS_LINK = 
		new Link(PATH_DOWNLOADS).setReadOnly();
	static final HTMLNode UPLOADS_LINK =
		new Link(PATH_UPLOADS).setReadOnly();

	@Override
	public String path() {
		if(uploads)
			return PATH_UPLOADS;
		else
			return PATH_DOWNLOADS;
	}

	private class GetCompletedEvent extends StoringUserEvent<GetCompletedEvent> {

		private final String identifier;
		private final FreenetURI uri;
		private final long size;

		public GetCompletedEvent(String identifier, FreenetURI uri, long size) {
			super(Type.GetCompleted, true, null, null, null, null, UserAlert.MINOR, true, NodeL10n.getBase().getString("UserAlert.hide"), true, null, completedGets);
			this.identifier = identifier;
			this.uri = uri;
			this.size = size;
		}

		@Override
		public void onDismiss() {
			super.onDismiss();
			saveCompletedIdentifiersOffThread();
		}

		@Override
		public void onEventDismiss() {
			synchronized(completedRequestIdentifiers) {
				completedRequestIdentifiers.remove(identifier);
			}
		}

		@Override
		public HTMLNode getEventHTMLText() {
			Box text = new Box();
			NodeL10n.getBase().addL10nSubstitution(text, "QueueToadlet.downloadSucceeded",
					new String[] { "link", "origlink", "filename", "size" },
					new HTMLNode[] { new Link("/"+uri.toASCIIString()+"?max-size="+size), new Link("/"+uri.toASCIIString()), new Text(uri.getPreferredFilename()), new Text(SizeUtil.formatSize(size))});
			return text;
		}

		@Override
		public String getTitle() {
			String title = null;
			synchronized(events) {
				if(events.size() == 1)
					title = l10n("downloadSucceededTitle", "filename", uri.getPreferredFilename());
				else
					title = l10n("downloadsSucceededTitle", "nr", Integer.toString(events.size()));
			}
			return title;
		}

		@Override
		public String getShortText() {
			return getTitle();
		}

		@Override
		public String getEventText() {
			return l10n("downloadSucceededTitle", "filename", uri.getPreferredFilename());
		}

	}

	private class PutCompletedEvent extends StoringUserEvent<PutCompletedEvent> {

		private final String identifier;
		private final FreenetURI uri;
		private final long size;

		public PutCompletedEvent(String identifier, FreenetURI uri, long size) {
			super(Type.PutCompleted, true, null, null, null, null, UserAlert.MINOR, true, NodeL10n.getBase().getString("UserAlert.hide"), true, null, completedPuts);
			this.identifier = identifier;
			this.uri = uri;
			this.size = size;
		}

		@Override
		public void onDismiss() {
			super.onDismiss();
			saveCompletedIdentifiersOffThread();
		}

		@Override
		public void onEventDismiss() {
			synchronized(completedRequestIdentifiers) {
				completedRequestIdentifiers.remove(identifier);
			}
		}

		@Override
		public HTMLNode getEventHTMLText() {
			Box text = new Box();
			NodeL10n.getBase().addL10nSubstitution(text, "QueueToadlet.uploadSucceeded",
					new String[] { "link", "filename", "size" },
					new HTMLNode[] { new Link("/"+uri.toASCIIString()), new Text(uri.getPreferredFilename()), new Text(SizeUtil.formatSize(size))});
			return text;
		}

		@Override
		public String getTitle() {
			String title = null;
			synchronized(events) {
				if(events.size() == 1)
					title = l10n("uploadSucceededTitle", "filename", uri.getPreferredFilename());
				else
					title = l10n("uploadsSucceededTitle", "nr", Integer.toString(events.size()));
			}
			return title;
		}

		@Override
		public String getShortText() {
			return getTitle();
		}

		@Override
		public String getEventText() {
			return l10n("uploadSucceededTitle", "filename", uri.getPreferredFilename());
		}

	}

	private class PutDirCompletedEvent extends StoringUserEvent<PutDirCompletedEvent> {

		private final String identifier;
		private final FreenetURI uri;
		private final long size;
		private final int files;

		public PutDirCompletedEvent(String identifier, FreenetURI uri, long size, int files) {
			super(Type.PutDirCompleted, true, null, null, null, null, UserAlert.MINOR, true, NodeL10n.getBase().getString("UserAlert.hide"), true, null, completedPutDirs);
			this.identifier = identifier;
			this.uri = uri;
			this.size = size;
			this.files = files;
		}

		@Override
		public void onDismiss() {
			super.onDismiss();
			saveCompletedIdentifiersOffThread();
		}

		@Override
		public void onEventDismiss() {
			synchronized(completedRequestIdentifiers) {
				completedRequestIdentifiers.remove(identifier);
			}
		}

		@Override
		public HTMLNode getEventHTMLText() {
			String name = uri.getPreferredFilename();
			Box text = new Box();
			NodeL10n.getBase().addL10nSubstitution(text, "QueueToadlet.siteUploadSucceeded",
					new String[] { "link", "filename", "size", "files" },
					new HTMLNode[] { new Link("/"+uri.toASCIIString()), new Text(name), new Text(SizeUtil.formatSize(size)), new Text(files) });
			return text;
		}

		@Override
		public String getTitle() {
			String title = null;
			synchronized(events) {
				if(events.size() == 1)
					title = l10n("siteUploadSucceededTitle", "filename", uri.getPreferredFilename());
				else
					title = l10n("sitesUploadSucceededTitle", "nr", Integer.toString(events.size()));
			}
			return title;
		}

		@Override
		public String getShortText() {
			return getTitle();
		}

		@Override
		public String getEventText() {
			return l10n("siteUploadSucceededTitle", "filename", uri.getPreferredFilename());
		}

	}

}
