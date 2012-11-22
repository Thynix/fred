/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http.pages;

import freenet.client.ClientMetadata;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertException;
import freenet.clients.http.ExternalLinkToadlet;
import freenet.clients.http.PageMaker.RenderParameters;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.clients.http.bookmark.BookmarkList;
import freenet.clients.http.bookmark.BookmarkManager;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.node.*;
import freenet.node.useralerts.UserAlert;
import freenet.support.HTMLNode;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.MultiValueTable;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;
import org.tanukisoftware.wrapper.WrapperManager;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;

public class WelcomeToadlet extends Toadlet {

	final NodeClientCore core;
	final Node node;
	final BookmarkManager bookmarkManager;

	private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback() {
			@Override
			public void shouldUpdate() {
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	public static HTMLNode sendRestartingPageInner(ToadletContext ctx) {
		// Tell the user that the node is restarting
		Page page = ctx.getPageMaker().getPage("Node Restart", ctx,
			new RenderParameters().renderNavigationLinks(false));
		page.root.head.addMeta("refresh", "20; url=");
		HTMLNode contentNode = page.content;
		contentNode.addInfobox(InfoboxType.INFORMATION, Identifier.SHUTDOWNPROGRESSING,
			l10n("restartingTitle")).body.addText(l10n("restarting"));
		Logger.normal(WelcomeToadlet.class, "Node is restarting");
		return page;
	}

	private static String l10n(String key) {
		return NodeL10n.getBase().getString("WelcomeToadlet." + key);
	}

	private static String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("WelcomeToadlet." + key, new String[]{pattern}, new String[]{value});
	}

	private void redirectToRoot(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
		return;
	}

	private void putFetchKeyBox(ToadletContext ctx, HTMLNode contentNode) {
		// Fetch-a-key box
		Infobox fetchKey = new Infobox(InfoboxType.NORMAL, Identifier.FETCHKEY, l10n("fetchKeyLabel"));
		Box fetchKeyForm = fetchKey.body.addForm("/", "get").addBox();
		fetchKeyForm.addInlineBox(Category.FETCHKEYLABEL, l10n("keyRequestLabel") + ' ');
		fetchKeyForm.addInput(InputType.TEXT, "key", 80);
		fetchKeyForm.addInput(InputType.SUBMIT, l10n("fetch"));
	}

	private void sendRestartingPage(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		writeHTMLReply(ctx, 200, "OK", sendRestartingPageInner(ctx).generate());
	}

	public WelcomeToadlet(HighLevelSimpleClient client, NodeClientCore core, Node node, BookmarkManager bookmarks) {
		super(client);
		this.node = node;
		this.core = core;
		this.bookmarkManager = bookmarks;
	}

	@Override
	public String path() {
		// So it matches "Browse Freenet" on the menu
		return PATH;
	}

	public static final String PATH = Path.MAIN.url;

	public static void maybeDisplayWrapperLogfile(ToadletContext ctx, HTMLNode contentNode) {
		final File logs = new File("wrapper.log");
		long logSize = logs.length();
		if (logs.exists() && logs.isFile() && logs.canRead() && (logSize > 0)) {
			try {
				boolean isShortFile = logSize < 2000;
				String content = FileUtil.readUTF(logs, (isShortFile ? 0 : logSize - 2000));
				int eol = content.indexOf('\n');
				boolean shallStripFirstLine = (! isShortFile) && (eol > 0);
				OutputNode logContent = new OutputNode("%",
					content.substring((shallStripFirstLine ? eol + 1 : 0))
						.replaceAll("\n", "<br>\n"));
				contentNode.addInfobox(InfoboxType.INFORMATION, Identifier.STARTPROGRESS,
					"Current status", logContent);
			} catch (IOException e) {
			}
		}
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		String passwd = request.getPartAsStringFailsafe("formPassword", 32);
		boolean noPassword = (passwd == null) || !passwd.equals(core.formPassword);
		if (noPassword) {
			if (logMINOR) {
				Logger.minor(this, "No password (" + passwd + " should be " + core.formPassword + ')');
			}
		}
		if (request.getPartAsStringFailsafe("updateconfirm", 32).length() > 0) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			// false for no navigation bars, because that would be very silly
			Page page = ctx.getPageMaker().getPage(l10n("updatingTitle"), ctx);
			Infobox content =
				page.content.addInfobox(InfoboxType.INFORMATION, l10n("updatingTitle"));
			content.body.addBlockText(l10n("updating"));
			content.body.addBlockText(l10n("thanks"));
			writeHTMLReply(ctx, 200, "OK", page.generate());
			Logger.normal(this, "Node is updating/restarting");
			node.getNodeUpdater().arm();
		} else if (request.getPartAsStringFailsafe("update", 32).length() > 0) {
			Page page = ctx.getPageMaker().getPage(l10n("nodeUpdateConfirmTitle"), ctx);
			Infobox content = page.content
				.addInfobox(InfoboxType.QUERY, Identifier.UPDATENODECONFIRM,
					l10n("nodeUpdateConfirmTitle"));
			content.body.addBlockText(l10n("nodeUpdateConfirm"));
			HTMLNode updateForm = ctx.addFormChild(content.body, Path.MAIN.url, "updateConfirmForm");
			updateForm.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
			updateForm.addInput(InputType.SUBMIT, "updateconfirm", l10n("update"));
			writeHTMLReply(ctx, 200, "OK", page.generate());
		} else if (request.isPartSet("getThreadDump")) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			Page page = ctx.getPageMaker().getPage(l10n("threadDumpTitle"), ctx);
			Text Message = new Text();
			if (node.isUsingWrapper()) {
				System.out.println("Thread Dump:");
				WrapperManager.requestThreadDump();
				Message.setContent(l10n("threadDumpWithFilename", "filename",
					WrapperManager.getProperties().getProperty("wrapper.logfile")));
			} else {
				Message.setContent(l10n("threadDumpNotUsingWrapper"));
			}
			page.content.addInfobox(InfoboxType.WTF, Identifier.THREADDUMPGENERATION,
				l10n("threadDumpSubTitle"), Message);
			this.writeHTMLReply(ctx, 200, "OK", page.generate());
		} else if (request.isPartSet("disable")) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			int validAlertsRemaining = 0;
			UserAlert[] alerts = core.alerts.getAlerts();
			for (int i = 0; i < alerts.length; i++) {
				if (request.getIntPart("disable", -1) == alerts[i].hashCode()) {
					UserAlert alert = alerts[i];
					// Won't be dismissed if it's not allowed anyway
					if (alert.userCanDismiss() && alert.shouldUnregisterOnDismiss()) {
						alert.onDismiss();
						Logger.normal(this, "Unregistering the userAlert " + alert.hashCode());
						core.alerts.unregister(alert);
					} else {
						Logger.normal(this, "Disabling the userAlert " + alert.hashCode());
						alert.isValid(false);
					}
				} else if(alerts[i].isValid()) {
					validAlertsRemaining++;
				}
			}
			writePermanentRedirect(ctx, l10n("disabledAlert"), (validAlertsRemaining > 0 ? Path.ALERTS.url : "/"));
			return;
		} else if (request.isPartSet("key") && request.isPartSet("filename")) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			FreenetURI key = new FreenetURI(request.getPartAsStringFailsafe("key", 128));
			String type = request.getPartAsStringFailsafe("content-type", 128);
			if (type == null) {
				type = "text/plain";
			}
			ClientMetadata contentType = new ClientMetadata(type);
			Bucket bucket = request.getPart("filename");
			Page page = ctx.getPageMaker().getPage(l10n("insertedTitle"), ctx);
			Infobox content;
			String filenameHint = null;
			if (key.getKeyType().equals("CHK")) {
				String[] metas = key.getAllMetaStrings();
				if ((metas != null) && (metas.length > 1)) {
					filenameHint = metas[0];
				}
			}
			InsertBlock block = new InsertBlock(bucket, contentType, key);
			try {
				key = this.insert(block, filenameHint, false);
				content = page.content.addInfobox(InfoboxType.SUCCESS, Category.INSERTSUCCESS,
					l10n("insertSucceededTitle"));
				String u = key.toString();
				NodeL10n.getBase().addL10nSubstitution(content.body,
					"WelcomeToadlet.keyInsertedSuccessfullyWithKeyAndName",
					new String[]{"link", "name"}, new HTMLNode[]{new Link("/" + u), new Text(u)});
			} catch (InsertException e) {
				content = page.content.addInfobox(InfoboxType.ERROR, Category.INSERTFAILED,
					l10n("insertFailedTitle"));
				content.body.addText(l10n("insertFailedWithMessage", "message", e.getMessage()));
				content.body.addLineBreak();
				if (e.uri != null) {
					content.body.addText(l10n("uriWouldHaveBeen", "uri", e.uri.toString()));
				}
				int mode = e.getMode();
				if ((mode == InsertException.FATAL_ERRORS_IN_BLOCKS) ||
					(mode == InsertException.TOO_MANY_RETRIES_IN_BLOCKS)) {
					content.body.addLineBreak(); /* TODO */
					content.body.addText(l10n("splitfileErrorLabel"));
					content.body.addChild("pre", e.errorCodes.toVerboseString());
				}
			}
			content.body.addLineBreak();
			addHomepageLink(content.body);
			writeHTMLReply(ctx, 200, "OK", page.generate());
			request.freeParts();
			bucket.free();
		} else if (request.isPartSet("exit")) {
			Page page = ctx.getPageMaker().getPage(l10n("shutdownConfirmTitle"), ctx);
			Infobox content = page.content.addInfobox(InfoboxType.QUERY,
				Identifier.SHUTDOWNCONFIRM, l10n("shutdownConfirmTitle"));
			content.body.addBlockText().addText(l10n("shutdownConfirm"));
			HTMLNode shutdownForm =
				ctx.addFormChild(content.body.addBlockText(), "/", "confirmShutdownForm");
			shutdownForm.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
			shutdownForm.addInput(InputType.SUBMIT, "shutdownconfirm", l10n("shutdown"));
			writeHTMLReply(ctx, 200, "OK", page.generate());
			return;
		} else if (request.isPartSet("shutdownconfirm")) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", "/?terminated&formPassword=" + core.formPassword);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			node.ticker.queueTimedJob(new Runnable() {
				@Override
				public void run() {
					node.exit("Shutdown from fproxy");
				}
			}, 1);
			return;
		} else if (request.isPartSet("restart")) {
			Page page = ctx.getPageMaker().getPage(l10n("restartConfirmTitle"), ctx);
			Infobox content = page.content.addInfobox(InfoboxType.QUERY,
				Identifier.RESTARTCONFIRM, l10n("restartConfirmTitle"));
			content.body.addBlockText().addText(l10n("restartConfirm"));
			HTMLNode restartForm = ctx.addFormChild(content.body.addBlockText(), "/",
				"confirmRestartForm");
			restartForm.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
			restartForm.addInput(InputType.SUBMIT, "restartconfirm", l10n("restart"));
			writeHTMLReply(ctx, 200, "OK", page.generate());
			return;
		} else if (request.isPartSet("restartconfirm")) {
			if (noPassword) {
				redirectToRoot(ctx);
				return;
			}
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", "/?restarted&formPassword=" + core.formPassword);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			node.ticker.queueTimedJob(new Runnable() {
				@Override
				public void run() {
					node.getNodeStarter().restart();
				}
			}, 1);
			return;
		} else if(request.isPartSet("dismiss-events")) {
			if(noPassword) {
				redirectToRoot(ctx);
				return;
			}
			String alertsToDump = request.getPartAsStringFailsafe("events", Integer.MAX_VALUE);
			String[] alertAnchors = alertsToDump.split(",");
			HashSet<String> toDump = new HashSet<String>();
			for (String alertAnchor : alertAnchors) {
				toDump.add(alertAnchor);
			}
			core.alerts.dumpEvents(toDump);
			redirectToRoot(ctx);
		} else {
			redirectToRoot(ctx);
		}
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if (ctx.isAllowedFullAccess()) {
			if (request.isParameterSet("latestlog")) {
				final File logs = new File(node.config.get("logger").getString("dirname") + File.separator + "freenet-latest.log");
				this.writeTextReply(ctx, 200, "OK", FileUtil.readUTF(logs));
				return;
			} else if (request.isParameterSet("terminated")) {
				if ((! request.isParameterSet("formPassword")) ||
					! request.getParam("formPassword").equals(core.formPassword)) {
					redirectToRoot(ctx);
					return;
				}
				// Tell the user that the node is shutting down
				Page page = ctx.getPageMaker().getPage("Node Shutdown", ctx,
					new RenderParameters().renderNavigationLinks(false));
				page.content.addInfobox(InfoboxType.INFORMATION,
					Identifier.SHUTDOWNPROGRESSING,
					l10n("shutdownDone")).addText(
					l10n("thanks"));
				WelcomeToadlet.maybeDisplayWrapperLogfile(ctx, page.content);
				this.writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} else if (request.isParameterSet("restarted")) {
				if ((!request.isParameterSet("formPassword")) || !request.getParam("formPassword").equals(core.formPassword)) {
					redirectToRoot(ctx);
					return;
				}
				sendRestartingPage(ctx);
				return;
			} else if (request.getParam("newbookmark").length() > 0) {
				Page page = ctx.getPageMaker().getPage(l10n("confirmAddBookmarkTitle"), ctx);
				HTMLNode addForm = ctx.addFormChild(page.content
					.addInfobox(InfoboxType.WTF, Identifier.BOOKMARKADDCONFIRM,
						l10n("confirmAddBookmarkSubTitle")).body, "/bookmarkEditor/",
					"editBookmarkForm");
				addForm.addText(
					l10n("confirmAddBookmarkWithKey", "key", request.getParam("newbookmark")));
				addForm.addLineBreak();
				String key = request.getParam("newbookmark");
				if (key.startsWith("freenet:")) {
					key = key.substring(8);
				}
				addForm.addInput(InputType.HIDDEN, "key", key);
				if (request.isParameterSet("hasAnActivelink")) {
					addForm.addInput(InputType.HIDDEN, "hasAnActivelink", request.getParam("hasAnActivelink"));
				}
				addForm.addChild("label", "for", "name",
					NodeL10n.getBase().getString("BookmarkEditorToadlet.nameLabel") + ' ');
				addForm.addInput(InputType.TEXT, "name", request.getParam("desc"));
				addForm.addLineBreak();
				addForm.addInput(InputType.HIDDEN, "bookmark", "/");
				addForm.addInput(InputType.HIDDEN, "action", "addItem");
				addForm.addChild("label", "for", "descB",
					NodeL10n.getBase().getString("BookmarkEditorToadlet.descLabel") + ' ');
				addForm.addLineBreak();
				addForm.addChild("textarea", new String[]{"id", "name", "row", "cols"},
					new String[]{"descB", "descB", "3", "70"});
				if (node.getDarknetConnections().length > 0) {
					addForm.addLineBreak();
					addForm.addLineBreak();
					Table peerTable = new Table(Category.DARKNETCONNECTIONS);
					addForm.addChild(peerTable);
					peerTable.addRow().addCell(2, NodeL10n.getBase()
						.getString("BookmarkEditorToadlet.recommendToFriends"));
					for (DarknetPeerNode peer : node.getDarknetConnections()) {
						Row peerRow = peerTable.addRow(Category.DARKNETCONNECTIONSNORMAL);
						peerRow.addCell(Category.PEERMARKER)
							.addInput("node_" + peer.hashCode(), InputType.CHECKBOX);
						peerRow.addCell(Category.PEERNAME).addText(peer.getName());
					}
					addForm.addChild("label", "for", "descB",
						(NodeL10n.getBase().getString("BookmarkEditorToadlet" +
							".publicDescLabel") +
							' '));
					addForm.addLineBreak();
					addForm.addChild("textarea", new String[]{"id", "name", "row", "cols"},
						new String[]{"descB", "publicDescB", "3", "70"}, "");
				}
				addForm.addLineBreak();
				addForm.addInput(InputType.SUBMIT, "addbookmark",
					NodeL10n.getBase().getString("BookmarkEditorToadlet.addBookmark"));
				this.writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} else if (uri.getQuery() != null && uri.getQuery().startsWith("_CHECKED_HTTP_=")) {
				//Redirect requests for escaped URLs using the old destination to ExternalLinkToadlet.
				super.writeTemporaryRedirect(ctx, "Depreciated", ExternalLinkToadlet.PATH+'?'+uri.getQuery());
				return;
			}
		}
		//Start generating the welcome page
		Page page = ctx.getPageMaker().getPage(l10n("homepageFullTitle"), ctx);
		String useragent = ctx.getHeaders().get("user-agent");
		if (useragent != null) {
			useragent = useragent.toLowerCase();
			if (useragent.contains("msie") && (! useragent.contains("opera"))) {
				page.content
					.addInfobox(InfoboxType.ALERT, Identifier.IEWARNING,
						l10n("ieWarningTitle"))
					.body.addText(l10n("ieWarning"));
			}
		}
		// Alerts
		if (ctx.isAllowedFullAccess()) {
			page.content.addChild(core.alerts.createSummary());
		}
		if (ctx.getPageMaker().getTheme().fetchKeyBoxAboveBookmarks) {
			this.putFetchKeyBox(ctx, page.content);
		}
		// Bookmarks
		Infobox bookmarkBox = page.content.addInfobox(InfoboxType.NORMAL, null);
		bookmarkBox.addClass(Category.BOOKMARKSBOX);
		bookmarkBox.header.addLink(Category.BOOKMARKSHEADERTEXT,
		                           NodeL10n.getBase().getString("BookmarkEditorToadlet.myBookmarksExplanation"),
		                           NodeL10n.getBase().getString("BookmarkEditorToadlet.myBookmarksTitle"));
		OutputNode editLink = new Text();
		editLink.addInlineBox(Category.EDITBRACKET, "[");
		editLink.addInlineBox(Identifier.BOOKMARKEDIT).addLink("/bookmarkEditor/", Category.INTERFACELINK,
		                                                       NodeL10n.getBase().getString(
			                                                       "BookmarkEditorToadlet.edit"));
		editLink.addInlineBox(Category.EDITBRACKET, "]");
		if (ctx.isAllowedFullAccess()) {
			bookmarkBox.header.addChild(editLink);
		}
		bookmarkBox.body.addList(
			new BookmarkList((ctx.getContainer().publicGatewayMode() || ! (ctx.isAllowedFullAccess())),
			                 (ctx.getPageMaker().getTheme().forceActivelinks ||
			                  container.enableActivelinks())));
		// Search Box
		// FIXME search box is BELOW bookmarks for now, until we get search fixed properly.
		Infobox searchBox = page.content.addInfobox(InfoboxType.NORMAL, null);
		searchBox.setID(Identifier.SEARCH);
		searchBox.header.addClass(Category.SEARCHTITLELABEL);
		searchBox.header.setContent(NodeL10n.getBase().getString("WelcomeToadlet.searchBoxLabel"));
		// Search form
		if(core.node.pluginManager != null && core.node.pluginManager.isPluginLoaded("plugins.Library.Main")) {
			// FIXME: Remove this once we have a non-broken index.
			searchBox.body.addInlineBox(Category.SEARCHWARNINGTEXT, l10n("searchBoxWarningSlow"));
			HTMLNode searchForm = container.addFormChild(searchBox.body, "/library/", "searchform");
			searchForm.addInput(InputType.TEXT, "search", 80);
			searchForm.addInput(InputType.SUBMIT, "find", l10n("searchFreenet"));
			// Search must be in a new window so that the user is able to browse the bookmarks.
			searchForm.addAttribute("target", "_blank");
		} else if(core.node.pluginManager == null || core.node.pluginManager.isPluginLoadedOrLoadingOrWantLoad("Library")) {
			// Warn that search plugin is not loaded.
			InlineBox textSpan = searchBox.body.addInlineBox(Category.SEARCHNOTAVAILABLE);
			NodeL10n.getBase().addL10nSubstitution(textSpan, "WelcomeToadlet.searchPluginLoading", new String[] { "link" }, new HTMLNode[] { new Link(Path.PLUGINS.url) });
		} else {
			// Warn that search plugin is not loaded.
			InlineBox textSpan = searchBox.body.addInlineBox(Category.SEARCHNOTAVAILABLE);
			NodeL10n.getBase().addL10nSubstitution(textSpan, "WelcomeToadlet.searchPluginNotLoaded", new String[] { "link" }, new HTMLNode[] { new Link(Path.PLUGINS.url) });
		}
		if (!ctx.getPageMaker().getTheme().fetchKeyBoxAboveBookmarks) {
			this.putFetchKeyBox(ctx, page.content);
		}
		// Version info and Quit Form
		Infobox versionContent =
			page.content.addInfobox(InfoboxType.INFORMATION, Identifier.VERSION, l10n("versionHeader"));
		versionContent.body.addInlineBox(Category.FREENETFULLVERSION, NodeL10n.getBase()
			.getString("WelcomeToadlet.version", new String[]{"fullVersion", "build", "rev"},
				new String[]{Version.publicVersion(), Integer.toString(Version.buildNumber()),
					Version.cvsRevision()}));
		versionContent.body.addLineBreak();
			versionContent.body.addInlineBox(Category.FREENETEXTVERSION, NodeL10n.getBase()
				.getString("WelcomeToadlet.extVersion", new String[]{"build", "rev"},
					new String[]{Integer.toString(NodeStarter.extBuildNumber),
						NodeStarter.extRevisionNumber}));
		versionContent.body.addLineBreak();
		if (ctx.isAllowedFullAccess()) {
			HTMLNode shutdownForm = ctx.addFormChild(versionContent.body, ".", "shutdownForm");
			shutdownForm.addInput("exit", InputType.HIDDEN);
			shutdownForm.addInput(InputType.SUBMIT, l10n("shutdownNode"));
			if (node.isUsingWrapper()) {
				HTMLNode restartForm = ctx.addFormChild(versionContent.body, ".", "restartForm");
				restartForm.addInput("restart", InputType.HIDDEN);
				restartForm.addInput(InputType.SUBMIT, "restart2", l10n("restartNode"));
			}
		}
		this.writeHTMLReply(ctx, 200, "OK", page.generate());
	}
}
