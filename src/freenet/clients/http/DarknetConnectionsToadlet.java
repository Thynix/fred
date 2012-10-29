package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.*;
import freenet.node.DarknetPeerNode.FRIEND_TRUST;
import freenet.node.DarknetPeerNode.FRIEND_VISIBILITY;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.SimpleFieldSet;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;

import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.HashMap;

public class DarknetConnectionsToadlet extends ConnectionsToadlet {
	
	DarknetConnectionsToadlet(Node n, NodeClientCore core, HighLevelSimpleClient client) {
		super(n, core, client);
	}

	private static String l10n(String string) {
		return NodeL10n.getBase().getString("DarknetConnectionsToadlet."+string);
	}
	
	protected class DarknetComparator extends ComparatorByStatus {

		DarknetComparator(String sortBy, boolean reversed) {
			super(sortBy, reversed);
		}
	
		@Override
		protected int customCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode, String sortBy) {
			if(sortBy.equals("name")) {
				return ((DarknetPeerNodeStatus)firstNode).getName().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getName());
			}else if(sortBy.equals("privnote")){
				return ((DarknetPeerNodeStatus)firstNode).getPrivateDarknetCommentNote().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getPrivateDarknetCommentNote());
			} else if(sortBy.equals("trust")){
				return ((DarknetPeerNodeStatus)firstNode).getTrustLevel().compareTo(((DarknetPeerNodeStatus)secondNode).getTrustLevel());
			} else if(sortBy.equals("visibility")){
				int ret = ((DarknetPeerNodeStatus)firstNode).getOurVisibility().compareTo(((DarknetPeerNodeStatus)secondNode).getOurVisibility());
				if(ret != 0) return ret;
				return ((DarknetPeerNodeStatus)firstNode).getTheirVisibility().compareTo(((DarknetPeerNodeStatus)secondNode).getTheirVisibility());
			} else
				return super.customCompare(firstNode, secondNode, sortBy);
		}
		
		/** Default comparison, after taking into account status */
		@Override
		protected int lastResortCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
			return ((DarknetPeerNodeStatus)firstNode).getName().compareToIgnoreCase(((DarknetPeerNodeStatus)secondNode).getName());
		}

	}
	
	@Override
	protected Comparator<PeerNodeStatus> comparator(String sortBy, boolean reversed) {
		return new DarknetComparator(sortBy, reversed);
	}
		
	@Override
	protected boolean hasNameColumn() {
		return true;
	}
	
	@Override
	protected void drawNameColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean advanced) {
		// name column
		Cell cell = peerRow.addCell(Category.PEERNAME);
		cell.addLink("/send_n2ntm/?peernode_hashcode=" + peerNodeStatus.hashCode(), ((DarknetPeerNodeStatus) peerNodeStatus).getName());
		if (advanced && peerNodeStatus.hasFullNoderef) {
			cell.addText(" (");
			cell.addLink( path()+"friend-"+peerNodeStatus.hashCode()+".fref", l10n("noderefLink"));
			cell.addText(")");
		}
	}
	
	@Override
	protected boolean hasTrustColumn() {
		return true;
	}

	@Override
	protected void drawTrustColumn(Row peerRow, PeerNodeStatus peerNodeStatus) {
		peerRow.addCell(Category.PEERTRUST).addText(((DarknetPeerNodeStatus) peerNodeStatus).getTrustLevel().name());
	}

	@Override
	protected boolean hasVisibilityColumn() {
		return true;
	}

	@Override
	protected void drawVisibilityColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean advancedModeEnabled) {
		String content = ((DarknetPeerNodeStatus)peerNodeStatus).getOurVisibility().name();
		if(advancedModeEnabled)
			content += " ("+((DarknetPeerNodeStatus)peerNodeStatus).getTheirVisibility().name()+")";
		peerRow.addCell(Category.PEERTRUST).addText(content);
	}

	@Override
	protected boolean hasPrivateNoteColumn() {
		return true;
	}

	@Override
	protected void drawPrivateNoteColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean fProxyJavascriptEnabled) {
		// private darknet node comment note column
		DarknetPeerNodeStatus status = (DarknetPeerNodeStatus) peerNodeStatus;
		Input commentNote = new Input(InputType.TEXT, "peerPrivateNote_", status.getPrivateDarknetCommentNote(), 16, (short) 250);
		if(fProxyJavascriptEnabled) {
			commentNote.onBlur("peerNoteBlur();");
			commentNote.onChange("peerNoteChange();");
		}
		peerRow.addCell(Category.PEERPRIVATEDARKNETCOMMENTNOTE).addInput(commentNote);
	}

	@Override
	protected SimpleFieldSet getNoderef() {
		return node.exportDarknetPublicFieldSet();
	}

	@Override
	protected PeerNodeStatus[] getPeerNodeStatuses(boolean noHeavy) {
		return node.peers.getDarknetPeerNodeStatuses(noHeavy);
	}

	@Override
	protected String getPageTitle(String titleCountString) {
		return NodeL10n.getBase().getString("DarknetConnectionsToadlet.fullTitle",
				new String[] {"counts"}, new String[] {titleCountString} );
	}
	
	@Override
	protected boolean shouldDrawNoderefBox(boolean advancedModeEnabled) {
		return advancedModeEnabled; // Convenient for advanced users, but normally we will use the "Add a friend" box.
	}

	@Override
	protected boolean showPeerActionsBox() {
		return true;
	}

	@Override
	protected void drawPeerActionSelectBox(HTMLNode peerForm, boolean advancedModeEnabled) {
		HTMLNode actionSelect = peerForm.addChild("select", new String[] { "id", "name" }, new String[] { "action", "action" });
		actionSelect.addChild("option", "value", "", l10n("selectAction"));
		actionSelect.addChild("option", "value", "send_n2ntm", l10n("sendMessageToPeers"));
		actionSelect.addChild("option", "value", "update_notes", l10n("updateChangedPrivnotes"));
		if(advancedModeEnabled) {
			actionSelect.addChild("option", "value", "enable", l10n("peersEnable"));
			actionSelect.addChild("option", "value", "disable", l10n("peersDisable"));
			actionSelect.addChild("option", "value", "set_burst_only", l10n("peersSetBurstOnly"));
			actionSelect.addChild("option", "value", "clear_burst_only", l10n("peersClearBurstOnly"));
			actionSelect.addChild("option", "value", "set_listen_only", l10n("peersSetListenOnly"));
			actionSelect.addChild("option", "value", "clear_listen_only", l10n("peersClearListenOnly"));
			actionSelect.addChild("option", "value", "set_allow_local", l10n("peersSetAllowLocal"));
			actionSelect.addChild("option", "value", "clear_allow_local", l10n("peersClearAllowLocal"));
			actionSelect.addChild("option", "value", "set_ignore_source_port", l10n("peersSetIgnoreSourcePort"));
			actionSelect.addChild("option", "value", "clear_ignore_source_port", l10n("peersClearIgnoreSourcePort"));
			actionSelect.addChild("option", "value", "set_dont_route", l10n("peersSetDontRoute"));
			actionSelect.addChild("option", "value", "clear_dont_route", l10n("peersClearDontRoute"));
		}
		actionSelect.addChild("option", "value", "", l10n("separator"));
		actionSelect.addChild("option", "value", "remove", l10n("removePeers"));
		peerForm.addInput(InputType.SUBMIT, "doAction", l10n("go"));
		peerForm.addLineBreak();
		peerForm.addInput(InputType.SUBMIT, "doChangeTrust", l10n("changeTrustButton"));
		HTMLNode changeTrustLevelSelect = peerForm.addChild("select", new String[] { "id", "name" }, new String[] { "changeTrust", "changeTrust" });
		for(FRIEND_TRUST trust : FRIEND_TRUST.valuesBackwards()) {
			changeTrustLevelSelect.addChild("option", "value", trust.name(), l10n("peerTrust."+trust.name()));
		}
		peerForm.addLineBreak();
		peerForm.addInput(InputType.SUBMIT, "doChangeVisibility", l10n("changeVisibilityButton"));
		HTMLNode changeVisibilitySelect = peerForm.addChild("select", new String[] { "id", "name" }, new String[] { "changeVisibility", "changeVisibility" });
		for(FRIEND_VISIBILITY trust : FRIEND_VISIBILITY.values()) {
			changeVisibilitySelect.addChild("option", "value", trust.name(), l10n("peerVisibility."+trust.name()));
		}
	}

	@Override
	protected String getPeerListTitle() {
		return l10n("myFriends");
	}

	@Override
	protected boolean acceptRefPosts() {
		return true;
	}

	@Override
	protected String defaultRedirectLocation() {
		return Path.FRIENDS.url; // FIXME
	}

	/**
	 * Implement other post actions than adding nodes.
	 * @throws java.io.IOException
	 * @throws ToadletContextClosedException
	 * @throws RedirectException
	 */
	@Override
	protected void handleAltPost(URI uri, HTTPRequest request, ToadletContext ctx, boolean logMINOR)
		throws ToadletContextClosedException, IOException, RedirectException {
		if (request.isPartSet("doAction") &&
			request.getPartAsStringFailsafe("action", 25).equals("send_n2ntm")) {
			Page altPostPage = ctx.getPageMaker().getPage(l10n("sendMessageTitle"), ctx);
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			HashMap<String, String> peers = new HashMap<String, String>();
			for (DarknetPeerNode pn : peerNodes) {
				if (request.isPartSet("node_" + pn.hashCode())) {
					String peer_name = pn.getName();
					String peer_hash = "" + pn.hashCode();
					if (! peers.containsKey(peer_hash)) {
						peers.put(peer_hash, peer_name);
					}
				}
			}
			N2NTMToadlet.createN2NTMSendForm(altPostPage, ctx.getContainer().isAdvancedModeEnabled(), ctx, peers);
			writeHTMLReply(ctx, 200, "OK", altPostPage.generate());
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("update_notes")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("peerPrivateNote_"+peerNodes[i].hashCode())) {
					if(!request.getPartAsStringFailsafe("peerPrivateNote_"+peerNodes[i].hashCode(),250).equals(peerNodes[i].getPrivateDarknetCommentNote())) {
						peerNodes[i].setPrivateDarknetCommentNote(request.getPartAsStringFailsafe("peerPrivateNote_"+peerNodes[i].hashCode(),250));
					}
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("enable")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].enablePeer();
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("disable")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].disablePeer();
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("set_burst_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setBurstOnly(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("clear_burst_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setBurstOnly(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("set_ignore_source_port")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setIgnoreSourcePort(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("clear_ignore_source_port")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setIgnoreSourcePort(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("clear_dont_route")) {
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setRoutingStatus(true, true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("set_dont_route")) {
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if(request.isPartSet("node_" + peerNodes[i].hashCode())) {
					peerNodes[i].setRoutingStatus(false, true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("set_listen_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setListenOnly(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("clear_listen_only")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setListenOnly(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("set_allow_local")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setAllowLocalAddresses(true);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("doAction") && request.getPartAsStringFailsafe("action",25).equals("clear_allow_local")) {
			//int hashcode = Integer.decode(request.getParam("node")).intValue();
			
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					peerNodes[i].setAllowLocalAddresses(false);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("changeTrust") && request.isPartSet("doChangeTrust")) {
			FRIEND_TRUST trust = FRIEND_TRUST.valueOf(request.getPartAsStringFailsafe("changeTrust", 10));
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {	
					peerNodes[i].setTrustLevel(trust);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("changeVisibility") && request.isPartSet("doChangeVisibility")) {
			FRIEND_VISIBILITY trust = FRIEND_VISIBILITY.valueOf(request.getPartAsStringFailsafe("changeVisibility", 10));
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {	
					peerNodes[i].setVisibility(trust);
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("remove") || (request.isPartSet("doAction") &&
			request.getPartAsStringFailsafe("action", 25).equals("remove"))) {
			if (logMINOR) {
				Logger.minor(this, "Remove node");
			}
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for (int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_" + peerNodes[i].hashCode())) {
					if ((peerNodes[i].timeLastConnectionCompleted() < (System.currentTimeMillis
						() -
						1000 * 60 * 60 * 24 * 7) /* one week */) ||
						(peerNodes[i].peerNodeStatus ==
							PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED) ||
						request.isPartSet("forceit")) {
						this.node.removePeerConnection(peerNodes[i]);
						if (logMINOR) {
							Logger.minor(this,
								"Removed node: node_" + peerNodes[i].hashCode());
						}
					} else {
						if (logMINOR) {
							Logger.minor(this,
								"Refusing to remove : node_" + peerNodes[i]
									.hashCode() +
									" (trying to prevent network churn) : let's" +
									" display the warning message.");
						}
						Page confirmPage =
							ctx.getPageMaker().getPage(l10n("confirmRemoveNodeTitle"),
								ctx);
						Infobox removeDarknetNode = confirmPage.content.addInfobox(
							InfoboxType.WARNING, Identifier.DARKNETREMOVENODE,
							l10n("confirmRemoveNodeWarningTitle"));
						removeDarknetNode.body.addBlockText((NodeL10n.getBase()
							.getString("DarknetConnectionsToadlet.confirmRemoveNode",
								new String[]{"name"},
								new String[]{peerNodes[i].getName()})));
						HTMLNode removeForm =
							ctx.addFormChild(removeDarknetNode.body, Path.FRIENDS.url,
								"removeConfirmForm");
						removeForm.addInput(InputType.HIDDEN, "node_" + peerNodes[i].hashCode(),
								"remove");
						removeForm.addInput(InputType.SUBMIT, "cancel",
								NodeL10n.getBase().getString("Toadlet.cancel"));
						removeForm.addInput(InputType.SUBMIT, "remove", l10n("remove"));
						removeForm.addInput(InputType.HIDDEN, "forceit", l10n("forceRemove"));
						writeHTMLReply(ctx, 200, "OK", confirmPage.generate());
						return; // FIXME: maybe it breaks multi-node removing
					}
				} else {
					if (logMINOR) {
						Logger.minor(this, "Part not set: node_" + peerNodes[i].hashCode());
					}
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("acceptTransfer")) {
			// FIXME this is ugly, should probably move both this code and the PeerNode code somewhere.
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					long id = Long.parseLong(request.getPartAsStringFailsafe("id", 32)); // FIXME handle NumberFormatException
					peerNodes[i].acceptTransfer(id);
					break;
				}
			}
			redirectHere(ctx);
			return;
		} else if (request.isPartSet("rejectTransfer")) {
			// FIXME this is ugly, should probably move both this code and the PeerNode code somewhere.
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			for(int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_"+peerNodes[i].hashCode())) {
					long id = Long.parseLong(request.getPartAsStringFailsafe("id", 32)); // FIXME handle NumberFormatException
					peerNodes[i].rejectTransfer(id);
					break;
				}
			}
			redirectHere(ctx);
			return;
		} else {
			this.handleMethodGET(uri, new HTTPRequestImpl(uri, "GET"), ctx);
		}
	}

	private void redirectHere(ToadletContext ctx) throws ToadletContextClosedException, IOException {
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", Path.FRIENDS.url);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	@Override
	protected boolean isOpennet() {
		return false;
	}

	@Override
	SimpleColumn[] endColumnHeaders(boolean advancedMode) {
		return null;
	}

	@Override
	public String path() {
		return Path.FRIENDS.url;
	}

	@Override
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if(tryHandlePeerNoderef(uri, request, ctx)) return;
		super.handleMethodGET(uri, request, ctx);
	}

	private boolean tryHandlePeerNoderef(URI uri, HTTPRequest request,
			ToadletContext ctx) throws ToadletContextClosedException, IOException {
		String path = uri.getPath();
		if(path.endsWith(".fref") && path.startsWith(path()+"friend-")) {
			// Get noderef for a peer
			String input_hashcode_string = path.substring((path()+"friend-").length());
			input_hashcode_string = input_hashcode_string.substring(0, input_hashcode_string.length() - ".fref".length());
			int input_hashcode;
			try {
				input_hashcode = (Integer.valueOf(input_hashcode_string)).intValue();
			} catch (NumberFormatException e) {
				// ignore here, handle below
				return false;
			}
			String peernode_name = null;
			SimpleFieldSet fs = null;
			if (input_hashcode != -1) {
				DarknetPeerNode[] peerNodes = node.getDarknetConnections();
				for (int i = 0; i < peerNodes.length; i++) {
					int peer_hashcode = peerNodes[i].hashCode();
					if (peer_hashcode == input_hashcode) {
						peernode_name = peerNodes[i].getName();
						fs = peerNodes[i].getFullNoderef();
						break;
					}
				}
			}
			
			if(fs == null) return false;
			String filename = FileUtil.sanitizeFileNameWithExtras(peernode_name+".fref", "\" ");
			String content = fs.toString();
			MultiValueTable<String, String> extraHeaders = new MultiValueTable<String, String>();
			// Force download to disk
			extraHeaders.put("Content-Disposition", "attachment; filename="+filename);
			this.writeReply(ctx, 200, "application/x-freenet-reference", "OK", extraHeaders, content);
			return true;
		} else return false;
	}

	@Override
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		super.handleMethodPOST(uri, request, ctx);
	}

}
