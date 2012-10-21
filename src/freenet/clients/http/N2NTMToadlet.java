/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import freenet.client.HighLevelSimpleClient;
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
import freenet.support.htmlprimitives.Div;
import freenet.support.htmlprimitives.HTMLClass;
import freenet.support.htmlprimitives.HTMLID;
import freenet.support.uielements.InfoboxWidget;

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

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
					.getString("Toadlet.unauthorized"));
			return;
		}

		if (request.isParameterSet("peernode_hashcode")) {
			PageNode page = ctx.getPageMaker().getPageNode(l10n("sendMessage"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;

			String peernode_name = null;
			String input_hashcode_string = request.getParam("peernode_hashcode");
			int input_hashcode = -1;
			try {
				input_hashcode = (Integer.valueOf(input_hashcode_string)).intValue();
			} catch (NumberFormatException e) {
				// ignore here, handle below
			}
			if (input_hashcode != -1) {
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
				contentNode.addChild(createPeerInfobox(HTMLClass.INFOBOXERROR,
					l10n("peerNotFoundTitle"), l10n("peerNotFoundWithHash",
					"hash", input_hashcode_string)));
				this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
				return;
			}
			HashMap<String, String> peers = new HashMap<String, String>();
			peers.put(input_hashcode_string, peernode_name);
			createN2NTMSendForm(pageNode, ctx.getContainer().isAdvancedModeEnabled(), contentNode, ctx, peers);
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/friends/");
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

	private static HTMLNode createPeerInfobox(HTMLClass infoboxType, String header, String message) {
		HTMLNode infobox = new Div(infoboxType);
		infobox.addChild(new Div(HTMLClass.INFOBOXHEADER, header));
		HTMLNode infoboxContent = infobox.addChild(new Div(HTMLClass.INFOBOXCONTENT));
		infoboxContent.addChild("#", message);
		HTMLNode list = infoboxContent.addChild("ul");
		Toadlet.addHomepageLink(list);
		list.addChild("li").addChild("a", new String[] { "href", "title" },
		        new String[] { "/friends/", l10n("returnToFriends") },
		        l10n("friends"));
		return infobox;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx)
			throws ToadletContextClosedException, IOException,
			RedirectException {
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if ((pass == null) || !pass.equals(core.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", "/send_n2ntm/");
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}

		//Browse button clicked. Redirect.
		if(request.isPartSet("n2nm-browse"))
		{
			try {
				throw new RedirectException(LocalFileN2NMToadlet.PATH);
			} catch (URISyntaxException e) {
				//Should be impossible because the browser is registered with .PATH.
			}
			return;
		}

		if (request.isPartSet("n2nm-upload") || request.isPartSet(LocalFileBrowserToadlet.selectFile) || request.isPartSet("send")) {
			File filename = null;
			String message = request.getPartAsStringFailsafe("message", 5 * 1024);
			message = message.trim();
			if (message.length() > 1024) {
				this.writeTextReply(ctx, 400, "Bad request", l10n("tooLong"));
				return;
			}
			PageNode page =  ctx.getPageMaker().getPageNode(l10n("processingSend"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			InfoboxWidget peerTableInfobox = new InfoboxWidget(InfoboxWidget.Type.NORMAL, null);
			contentNode.addChild(peerTableInfobox);
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			if(request.isPartSet(LocalFileBrowserToadlet.selectFile)) {
				String fnam = request.getPartAsStringFailsafe("filename", 1024);
				if(fnam != null && fnam.length() > 0) {
					filename = new File(fnam);
					if(!(filename.exists() && filename.canRead())) {
						peerTableInfobox.body.addChild("#", l10n("noSuchFileOrCannotRead"));
						Toadlet.addHomepageLink(peerTableInfobox.body);
						this.writeHTMLReply(ctx, 400, "OK", pageNode.generate());
						return;
					}
				}
			}

			HTMLNode peerTable = peerTableInfobox.body.addChild("table", "class", "n2ntm-send-statuses");
			HTMLNode peerTableHeaderRow = peerTable.addChild("tr");
			peerTableHeaderRow.addChild("th", l10n("peerName"));
			peerTableHeaderRow.addChild("th", l10n("sendStatus"));
			for (int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_" + peerNodes[i].hashCode())) {
					DarknetPeerNode pn = peerNodes[i];
					
					int status;
					
					if(filename != null) {
						try {
							status = pn.sendFileOffer(filename, message);
						} catch (IOException e) {
							peerTableInfobox.body.addChild("#", l10n("noSuchFileOrCannotRead"));
							Toadlet.addHomepageLink(peerTableInfobox.body);
							this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
							return;
						}
					} else if(request.isPartSet("n2nm-upload")) {
						try{
							HTTPUploadedFile file = request.getUploadedFile("n2nm-upload");
							if(!file.getFilename().equals("")) {
								long size = request.getUploadedFile("n2nm-upload").getData().size();
								if(size > 0) {
									long limit = maxSize();
									if(size > limit){
										peerTableInfobox.body.addChild("#", l10n("tooLarge", new String[]{"attempt", "limit"},
											new String[]{SizeUtil.formatSize(size, true), SizeUtil.formatSize(limit, true)}));
										HTMLNode list = peerTableInfobox.body.addChild("ul");
										Toadlet.addHomepageLink(list);
										list.addChild("li").addChild("a", new String[] { "href", "title" },
												new String[] { "/friends/", l10n("returnToFriends") },
												l10n("friends"));
										this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
										return;
									}
									status = pn.sendFileOffer(request.getUploadedFile("n2nm-upload"), message);
								}
							}
						} catch (IOException e) {
							peerTableInfobox.body.addChild("#", l10n("uploadFailed"));
							Toadlet.addHomepageLink(peerTableInfobox.body);
							this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
							return;
						}
					}
					status = pn.sendTextFeed(message);
					
					String sendStatusShort;
					String sendStatusLong;
					String sendStatusClass;
					if(status == PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF) {
						sendStatusShort = l10n("delayedTitle");
						sendStatusLong = l10n("delayed");
						sendStatusClass = "n2ntm-send-delayed";
						Logger.normal(this, "Sent N2NTM to '"
								+ pn.getName() + "': " + message);
					} else if(status == PeerManager.PEER_NODE_STATUS_CONNECTED) {
						sendStatusShort = l10n("sentTitle");
						sendStatusLong = l10n("sent");
						sendStatusClass = "n2ntm-send-sent";
						Logger.normal(this, "Sent N2NTM to '"
								+ pn.getName() + "': " + message);
					} else {
						sendStatusShort = l10n("queuedTitle");
						sendStatusLong = l10n("queued");
						sendStatusClass = "n2ntm-send-queued";
						Logger.normal(this, "Queued N2NTM to '"
								+ pn.getName() + "': " + message);
					}
					HTMLNode peerRow = peerTable.addChild("tr");
					peerRow.addChild("td", "class", "peer-name").addChild("#", pn.getName());
					peerRow.addChild("td", "class", sendStatusClass)
									.addChild("span",
									new String[] { "title", "style" },
									new String[] { sendStatusLong,
											"border-bottom: 1px dotted; cursor: help;" },
									sendStatusShort);
				}
			}
			HTMLNode infoboxContent = peerTableInfobox.body.addChild(new Div(HTMLClass.N2NTMMESSAGETEXT));
			infoboxContent.addChild("#", message);
			HTMLNode list = peerTableInfobox.body.addChild("ul");
			Toadlet.addHomepageLink(list);
			list.addChild("li").addChild("a", new String[] { "href", "title" },
					new String[] { "/friends/", l10n("returnToFriends") },
					l10n("friends"));
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/friends/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	public static void createN2NTMSendForm(HTMLNode pageNode, boolean advancedMode,
			HTMLNode contentNode, ToadletContext ctx, HashMap<String, String> peers)
			throws ToadletContextClosedException, IOException {
		InfoboxWidget messageeditor = new InfoboxWidget(InfoboxWidget.Type.NORMAL, l10n("sendMessage"));
		contentNode.addChild(messageeditor);
		messageeditor.setID(HTMLID.N2NBOX);
		messageeditor.body.addChild("p", l10n("composingMessageLabel"));
		HTMLNode messageTargetList = messageeditor.body.addChild("ul");
		// Iterate peers
		for (String peer_name: peers.values()) {
			messageTargetList.addChild("li", peer_name);
		}
		HTMLNode infoboxContent = messageeditor.addContentNode();
		HTMLNode messageForm = ctx.addFormChild(infoboxContent, "/send_n2ntm/", "sendN2NTMForm");
		// Iterate peers
		for (String peerNodeHash : peers.keySet()) {
			messageForm.addChild("input", new String[] { "type", "name",
					"value" }, new String[] { "hidden", "node_" + peerNodeHash,
					"1" });
		}
		messageForm.addChild("textarea", new String[] { "id", "name", "rows",
				"cols" }, new String[] { "n2ntmtext", "message", "8", "74" });
		messageForm.addChild("br");
		messageForm.addChild("#", NodeL10n.getBase().getString("N2NTMToadlet.mayAttachFile"));
		if(ctx.isAllowedFullAccess()) {
			messageForm.addChild("br");
			messageForm.addChild("#", NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseLabel")+": ");
			messageForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "n2nm-browse", NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseButton") + "..." });
			messageForm.addChild("br");
		}
		if(advancedMode){
			messageForm.addChild("#", NodeL10n.getBase().getString("N2NTMToadlet.sizeWarning", "limit", SizeUtil.formatSize(maxSize(), true)));
			messageForm.addChild("br");
			messageForm.addChild("#", NodeL10n.getBase().getString("QueueToadlet.insertFileLabel") + ": ");
			messageForm.addChild("input", new String[] {"type", "name", "value" }, new String[] { "file", "n2nm-upload", "" });
			messageForm.addChild("br");
		}
		messageForm.addChild("input", new String[] { "type", "name", "value" },
				new String[] { "submit", "send", l10n("sendMessageShort") });
	}

	@Override
	public String path() {
		return "/send_n2ntm/";
	}
}
