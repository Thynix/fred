/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.PeerManager;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.SizeUtil;
import freenet.support.api.HTTPRequest;
import freenet.support.api.HTTPUploadedFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

public class N2NTMToadlet extends Toadlet {
	private Node node;
	private NodeClientCore core;
	private LocalFileN2NMToadlet browser;
	protected N2NTMToadlet(Node n, NodeClientCore core,
			HighLevelSimpleClient client) {
		super(client);
		browser = new LocalFileN2NMToadlet(core, client);
		this.node = n;
		this.core = core;
	}

	public Toadlet getBrowser() {
		return browser;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException,
		RedirectException {
		if (! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
				.getString("Toadlet.unauthorized"));
			return;
		}
		if (request.isParameterSet("peernode_hashcode")) {
			Page sendMessagePage = ctx.getPageMaker().getPage(l10n("sendMessage"), ctx);
			String peernode_name = null;
			String input_hashcode_string = request.getParam("peernode_hashcode");
			int input_hashcode = - 1;
			try {
				input_hashcode = (Integer.valueOf(input_hashcode_string)).intValue();
			} catch (NumberFormatException e) {
				// ignore here, handle below
			}
			if (input_hashcode != - 1) {
				DarknetPeerNode[] peerNodes = node.getDarknetConnections();
				for (int i = 0; i < peerNodes.length; i++) {
					int peer_hashcode = peerNodes[i].hashCode();
					if (peer_hashcode == input_hashcode) {
						peernode_name = peerNodes[i].getName();
						break;
					}
				}
			}
			if (peernode_name == null) {
				sendMessagePage.content.addInfobox(createPeerInfobox(InfoboxType.ERROR,
					l10n("peerNotFoundTitle"), l10n("peerNotFoundWithHash",
					"hash", input_hashcode_string)));
				this.writeHTMLReply(ctx, 200, "OK", sendMessagePage.generate());
				return;
			}
			HashMap<String, String> peers = new HashMap<String, String>();
			peers.put(input_hashcode_string, peernode_name);
			createN2NTMSendForm(sendMessagePage, ctx.getContainer().isAdvancedModeEnabled(), ctx, peers);
			this.writeHTMLReply(ctx, 200, "OK", sendMessagePage.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", Path.FRIENDS.url);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	private String l10n(String key, String pattern[], String value[]) {
		return NodeL10n.getBase().getString("N2NTMToadlet." + key, pattern, value);
	}

	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("N2NTMToadlet." + key, new String[] { pattern }, new String[] { value });
	}

	private static String l10n(String key) {
		return NodeL10n.getBase().getString("N2NTMToadlet." + key);
	}
	
	/*
	 * File size limit is 1 MiB (1024*1024 bytes) or 5% of maximum Java memory, whichever is greater.
	 */
	private static long maxSize(){
		long maxMem = Math.round(0.05*Runtime.getRuntime().maxMemory());
		long limit = Math.max(maxMem, 1024*1024);
		if(maxMem == Long.MAX_VALUE) limit = 1024*1024;
		return limit;
	}

	private static Infobox createPeerInfobox(InfoboxType type, String header, String message) {
		Infobox peerInfobox = new Infobox(type, header);
		peerInfobox.body.addText(message);
		OutputList peerList = peerInfobox.body.addList();
		Toadlet.addHomepageLink(peerList);
		peerList.addItem().addLink(Path.FRIENDS.url, l10n("returnToFriends"), l10n("friends"));
		return peerInfobox;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException,
		RedirectException {
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ((pass == null) || ! pass.equals(core.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", "/send_n2ntm/");
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}
		if (! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized",
				NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		//Browse button clicked. Redirect.
		if (request.isPartSet("n2nm-browse")) {
			try {
				throw new RedirectException(LocalFileN2NMToadlet.PATH);
			} catch (URISyntaxException e) {
				//Should be impossible because the browser is registered with .PATH.
			}
			return;
		}
		if (request.isPartSet("n2nm-upload") || request.isPartSet(LocalFileBrowserToadlet.selectFile) ||
			request.isPartSet("send")) {
			File filename = null;
			String message = request.getPartAsStringFailsafe("message", 5 * 1024);
			message = message.trim();
			if (message.length() > 1024) {
				this.writeTextReply(ctx, 400, "Bad request", l10n("tooLong"));
				return;
			}
			Page sendPage = ctx.getPageMaker().getPage(l10n("processingSend"), ctx);
			Infobox peerTableInfobox = sendPage.content.addInfobox(InfoboxType.NORMAL, null);
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			if (request.isPartSet(LocalFileBrowserToadlet.selectFile)) {
				String fnam = request.getPartAsStringFailsafe("filename", 1024);
				if (fnam != null && fnam.length() > 0) {
					filename = new File(fnam);
					if (! (filename.exists() && filename.canRead())) {
						peerTableInfobox.body.addText(l10n("noSuchFileOrCannotRead"));
						Toadlet.addHomepageLink(peerTableInfobox.body);
						this.writeHTMLReply(ctx, 400, "OK", sendPage.generate());
						return;
					}
				}
			}
			Table peerTable = peerTableInfobox.body.addTable(Category.N2NTMSENDSTATUS);
			Row peerTableHeaderRow = peerTable.addRow();
			peerTableHeaderRow.addHeader(l10n("peerName"));
			peerTableHeaderRow.addHeader(l10n("sendStatus"));
			for (int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_" + peerNodes[i].hashCode())) {
					DarknetPeerNode pn = peerNodes[i];
					int status;
					if (filename != null) {
						try {
							status = pn.sendFileOffer(filename, message);
						} catch (IOException e) {
							peerTableInfobox.body.addText(l10n("noSuchFileOrCannotRead"));
							Toadlet.addHomepageLink(peerTableInfobox.body);
							this.writeHTMLReply(ctx, 200, "OK", sendPage.generate());
							return;
						}
					} else if (request.isPartSet("n2nm-upload")) {
						try {
							HTTPUploadedFile file = request.getUploadedFile
								("n2nm-upload");
							if (! file.getFilename().equals("")) {
								long size =
									request.getUploadedFile("n2nm-upload")
										.getData()
										.size();
								if (size > 0) {
									long limit = maxSize();
									if (size > limit) {
										peerTableInfobox.body.addText(
											l10n("tooLarge",
												new
													String[]{"attempt",
													"limit"},
												new String[]{
													SizeUtil.formatSize(
														size,
														true),
													SizeUtil.formatSize(
														limit,
														true)}));
										OutputList friendList =
											peerTableInfobox.body.addList();
										Toadlet.addHomepageLink(friendList);
										friendList.addItem().addLink(
											Path.FRIENDS.url,
											l10n("returnToFriends"),
											l10n("friends"));
										this.writeHTMLReply(ctx, 200, "OK",
											sendPage.generate());
										return;
									}
									status = pn.sendFileOffer(
										request.getUploadedFile("n2nm-upload"),
										message);
								}
							}
						} catch (IOException e) {
							peerTableInfobox.body.addText(l10n("uploadFailed"));
							Toadlet.addHomepageLink(peerTableInfobox.body);
							this.writeHTMLReply(ctx, 200, "OK", sendPage.generate());
							return;
						}
					}
					status = pn.sendTextFeed(message);
					String sendStatusShort;
					String sendStatusLong;
					Category sendStatusClass;
					if (status == PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF) {
						sendStatusShort = l10n("delayedTitle");
						sendStatusLong = l10n("delayed");
						sendStatusClass = Category.N2NTMSENDDELAYED;
						Logger.normal(this, "Sent N2NTM to '"
							+ pn.getName() + "': " + message);
					} else if (status == PeerManager.PEER_NODE_STATUS_CONNECTED) {
						sendStatusShort = l10n("sentTitle");
						sendStatusLong = l10n("sent");
						sendStatusClass = Category.N2NTMSENDSENT;
						Logger.normal(this, "Sent N2NTM to '"
							+ pn.getName() + "': " + message);
					} else {
						sendStatusShort = l10n("queuedTitle");
						sendStatusLong = l10n("queued");
						sendStatusClass = Category.N2NTMSENDQUEUED;
						Logger.normal(this, "Queued N2NTM to '"
							+ pn.getName() + "': " + message);
					}
					Row peerRow = peerTable.addRow();
					peerRow.addCell("peer-name").addText(pn.getName());
					peerRow.addCell(sendStatusClass).addInlineBox(Category.N2NTMSENDSTATUS,
						sendStatusLong, sendStatusShort);
				}
			}
			HTMLNode infoboxContent = peerTableInfobox.body.addBox(Category.N2NTMMESSAGETEXT);
			infoboxContent.addText(message);
			OutputList list = peerTableInfobox.body.addList();
			Toadlet.addHomepageLink(list);
			list.addItem().addLink(Path.FRIENDS.url, l10n("returnToFriends"), l10n("friends"));
			this.writeHTMLReply(ctx, 200, "OK", sendPage.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", Path.FRIENDS.url);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	public static void createN2NTMSendForm(Page pageNode, boolean advancedMode,
	                                       ToadletContext ctx, HashMap<String, String> peers)
		throws ToadletContextClosedException, IOException {
		Infobox messageeditor =
			pageNode.content.addInfobox(InfoboxType.NONE, Identifier.N2NBOX, l10n(
				"sendMessage"));
		messageeditor.body.addBlockText(l10n("composingMessageLabel"));
		OutputList messageTargetList = messageeditor.body.addList();
		// Iterate peers
		for (String peer_name : peers.values()) {
			messageTargetList.addItem(peer_name);
		}
		Box sendForm = messageeditor.addContentNode();
		HTMLNode messageForm = ctx.addFormChild(sendForm, "/send_n2ntm/", "sendN2NTMForm");
		// Iterate peers
		for (String peerNodeHash : peers.keySet()) {
			messageForm.addInput(InputType.HIDDEN, "node_" + peerNodeHash,"1");
		}
		messageForm.addChild("textarea", new String[]{"id", "name", "rows",
			"cols"}, new String[]{"n2ntmtext", "message", "8", "74"});
		messageForm.addLineBreak();
		messageForm.addText(NodeL10n.getBase().getString("N2NTMToadlet.mayAttachFile"));
		if (ctx.isAllowedFullAccess()) {
			messageForm.addLineBreak();
			messageForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseLabel") + ":" +
				" ");
			messageForm.addInput(InputType.SUBMIT, "n2nm-browse",
					NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseButton") + "...");
			messageForm.addLineBreak();
		}
		if (advancedMode) {
			messageForm.addText(NodeL10n.getBase()
				.getString("N2NTMToadlet.sizeWarning", "limit", SizeUtil.formatSize(maxSize(),
					true)));
			messageForm.addLineBreak();
			messageForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileLabel") + ": ");
			messageForm.addInput(InputType.FILE, "n2nm-upload", "");
			messageForm.addLineBreak();
		}
		messageForm.addInput(InputType.SUBMIT, "send", l10n("sendMessageShort"));
	}

	@Override
	public String path() {
		return "/send_n2ntm/";
	}
}
