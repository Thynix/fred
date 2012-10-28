package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.geoip.IPConverter;
import freenet.clients.http.geoip.IPConverter.Country;
import freenet.clients.http.uielements.*;
import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.io.xfer.PacketThrottle;
import freenet.l10n.NodeL10n;
import freenet.node.*;
import freenet.node.DarknetPeerNode.FRIEND_TRUST;
import freenet.node.DarknetPeerNode.FRIEND_VISIBILITY;
import freenet.node.PeerNode.IncomingLoadSummaryStats;
import freenet.node.updater.NodeUpdateManager;
import freenet.support.*;
import freenet.support.Logger.LogLevel;
import freenet.support.api.HTTPRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.DecimalFormat;
import java.util.*;

/** Base class for DarknetConnectionsToadlet and OpennetConnectionsToadlet */
public abstract class ConnectionsToadlet extends Toadlet {
	protected class ComparatorByStatus implements Comparator<PeerNodeStatus> {		
		protected final String sortBy;
		protected final boolean reversed;
		
		ComparatorByStatus(String sortBy, boolean reversed) {
			this.sortBy = sortBy;
			this.reversed = reversed;
		}
		
		@Override
		public int compare(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
			int result = 0;
			boolean isSet = true;
			
			if(sortBy != null){
				result = customCompare(firstNode, secondNode, sortBy);
				isSet = (result != 0);
				
			}else
				isSet=false;
			
			if(!isSet){
				int statusDifference = firstNode.getStatusValue() - secondNode.getStatusValue();
				if (statusDifference != 0) 
					result = (statusDifference < 0 ? -1 : 1);
				else
					result = lastResortCompare(firstNode, secondNode);
			}

			if(result == 0){
				return 0;
			}else if(reversed){
				isReversed = true;
				return result > 0 ? -1 : 1;
			}else{
				isReversed = false;
				return result < 0 ? -1 : 1;
			}
		}
		
		// xor: check why we do not just return the result of (long1-long2)
		// j16sdiz: (Long.MAX_VALUE - (-1) ) would overflow and become negative
		private int compareLongs(long long1, long long2) {
			int diff = Long.valueOf(long1).compareTo(long2);
			if(diff == 0)
				return 0;
			else
				return (diff > 0 ? 1 : -1);
		}
		
		private int compareInts(int int1, int int2) {
			int diff = Integer.valueOf(int1).compareTo(int2);
			if(diff == 0)
				return 0;
			else
				return (diff > 0 ? 1 : -1);
		}

		protected int customCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode, String sortBy2) {
			if(sortBy.equals("address")){
				return firstNode.getPeerAddress().compareToIgnoreCase(secondNode.getPeerAddress());
			}else if(sortBy.equals("location")){
				return compareLocations(firstNode, secondNode);
			}else if(sortBy.equals("version")){
				return Version.getArbitraryBuildNumber(firstNode.getVersion(), -1) - Version.getArbitraryBuildNumber(secondNode.getVersion(), -1);
			}else if(sortBy.equals("backoffRT")){
				return Double.compare(firstNode.getBackedOffPercent(true), secondNode.getBackedOffPercent(true));
			}else if(sortBy.equals("backoffBulk")){
				return Double.compare(firstNode.getBackedOffPercent(false), secondNode.getBackedOffPercent(false));
			}else if(sortBy.equals(("overload_p"))){
				return Double.compare(firstNode.getPReject(), secondNode.getPReject());
			}else if(sortBy.equals(("idle"))){
				return compareLongs(firstNode.getTimeLastConnectionCompleted(), secondNode.getTimeLastConnectionCompleted());
			}else if(sortBy.equals("time_routable")){
				return Double.compare(firstNode.getPercentTimeRoutableConnection(), secondNode.getPercentTimeRoutableConnection());
			}else if(sortBy.equals("total_traffic")){
				long total1 = firstNode.getTotalInputBytes()+firstNode.getTotalOutputBytes();
				long total2 = secondNode.getTotalInputBytes()+secondNode.getTotalOutputBytes();
				return compareLongs(total1, total2);
				}else if(sortBy.equals("total_traffic_since_startup")){
					long total1 = firstNode.getTotalInputSinceStartup()+firstNode.getTotalOutputSinceStartup();
					long total2 = secondNode.getTotalInputSinceStartup()+secondNode.getTotalOutputSinceStartup();
					return compareLongs(total1, total2);
			}else if(sortBy.equals("selection_percentage")){
				return Double.compare(firstNode.getSelectionRate(), secondNode.getSelectionRate());
			}else if(sortBy.equals("time_delta")){
				return compareLongs(firstNode.getClockDelta(), secondNode.getClockDelta());
			}else if(sortBy.equals(("uptime"))){
				return compareInts(firstNode.getReportedUptimePercentage(), secondNode.getReportedUptimePercentage());
			}else
				return 0;
		}

		private int compareLocations(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
			double diff = firstNode.getLocation() - secondNode.getLocation(); // Can occasionally be the same, and we must have a consistent sort order
			if(Double.MIN_VALUE*2 > Math.abs(diff)) return 0;
			return diff > 0 ? 1 : -1;
		}

		/** Default comparison, after taking into account status */
		protected int lastResortCompare(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
			return compareLocations(firstNode, secondNode);
		}
	}

	protected final Node node;
	protected final NodeClientCore core;
	protected final NodeStats stats;
	protected final PeerManager peers;
	protected boolean isReversed = false;
	public enum PeerAdditionReturnCodes{ OK, WRONG_ENCODING, CANT_PARSE, INTERNAL_ERROR, INVALID_SIGNATURE, TRY_TO_ADD_SELF, ALREADY_IN_REFERENCE}

	protected ConnectionsToadlet(Node n, NodeClientCore core, HighLevelSimpleClient client) {
		super(client);
		this.node = n;
		this.core = core;
		this.stats = n.nodeStats;
		this.peers = n.peers;
	}

	abstract SimpleColumn[] endColumnHeaders(boolean advancedModeEnabled);
	
	abstract class SimpleColumn {
		abstract protected void drawColumn(Row peerRow, PeerNodeStatus peerNodeStatus);
		abstract public String getSortString();
		abstract public String getTitleKey();
		abstract public String getExplanationKey();
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
                if (!ctx.isAllowedFullAccess()) {
                        super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"), NodeL10n.getBase().getString("Toadlet.unauthorized"));
                        return;
                }

                String path = uri.getPath();
		if(path.endsWith("myref.fref")) {
			SimpleFieldSet fs = getNoderef();
			StringWriter sw = new StringWriter();
			fs.writeTo(sw);
			MultiValueTable<String, String> extraHeaders = new MultiValueTable<String, String>();
			// Force download to disk
			extraHeaders.put("Content-Disposition", "attachment; filename=myref.fref");
			this.writeReply(ctx, 200, "application/x-freenet-reference", "OK", extraHeaders, sw.toString());
			return;
		}

		if(path.endsWith("myref.txt")) {
			SimpleFieldSet fs = getNoderef();
			StringWriter sw = new StringWriter();
			fs.writeTo(sw);
			this.writeTextReply(ctx, 200, "OK", sw.toString());
			return;
		}
		
		final DecimalFormat fix1 = new DecimalFormat("##0.0%");
				
		final boolean fProxyJavascriptEnabled = node.isFProxyJavascriptEnabled();
		boolean drawMessageTypes = path.endsWith("displaymessagetypes.html");
		
		/* gather connection statistics */
		PeerNodeStatus[] peerNodeStatuses = getPeerNodeStatuses(!drawMessageTypes);
		Arrays.sort(peerNodeStatuses, comparator(request.getParam("sortBy", null), request.isParameterSet("reversed")));
		
		int numberOfConnected = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CONNECTED);
		int numberOfRoutingBackedOff = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF);
		int numberOfTooNew = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_TOO_NEW);
		int numberOfTooOld = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_TOO_OLD);
		int numberOfDisconnected = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISCONNECTED);
		int numberOfNeverConnected = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED);
		int numberOfDisabled = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISABLED);
		int numberOfBursting = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_BURSTING);
		int numberOfListening = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_LISTENING);
		int numberOfListenOnly = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_LISTEN_ONLY);
		int numberOfClockProblem = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CLOCK_PROBLEM);
		int numberOfConnError = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CONN_ERROR);
		int numberOfDisconnecting = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISCONNECTING);
		int numberOfRoutingDisabled = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_ROUTING_DISABLED);
		int numberOfNoLoadStats = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_NO_LOAD_STATS);
		
		int numberOfSimpleConnected = numberOfConnected + numberOfRoutingBackedOff;
		int numberOfNotConnected = numberOfTooNew + numberOfTooOld +  numberOfNoLoadStats + numberOfDisconnected + numberOfNeverConnected + numberOfDisabled + numberOfBursting + numberOfListening + numberOfListenOnly + numberOfClockProblem + numberOfConnError;
		String titleCountString = null;
		if(node.isAdvancedModeEnabled()) {
			titleCountString = "(" + numberOfConnected + '/' + numberOfRoutingBackedOff + '/' + numberOfTooNew + '/' + numberOfTooOld + '/' + numberOfNoLoadStats + '/' + numberOfRoutingDisabled + '/' + numberOfNotConnected + ')';
		} else {
			titleCountString = (numberOfNotConnected + numberOfSimpleConnected)>0 ? String.valueOf(numberOfSimpleConnected) : "";
		}
		Page connectionsPage = ctx.getPageMaker().getPage(getPageTitle(titleCountString), ctx);
		final boolean advancedMode = ctx.getContainer().isAdvancedModeEnabled();
		// FIXME! We need some nice images
		long now = System.currentTimeMillis();
		if (ctx.isAllowedFullAccess()) {
			connectionsPage.content.addChild(core.alerts.createSummary());
		}
		if (peerNodeStatuses.length > 0) {
			if (advancedMode) {
				/* node status values */
				long nodeUptimeSeconds = (now - node.startupTime) / 1000;
				int bwlimitDelayTime = (int) stats.getBwlimitDelayTime();
				int nodeAveragePingTime = (int) stats.getNodeAveragePingTime();
				int networkSizeEstimateSession = stats.getDarknetSizeEstimate(- 1);
				int networkSizeEstimateRecent = 0;
				if (nodeUptimeSeconds > (48 * 60 * 60)) {  // 48 hours
					networkSizeEstimateRecent =
						stats.getDarknetSizeEstimate(now - (48 * 60 * 60 * 1000));  // 48
					// hours
				}
				DecimalFormat fix4 = new DecimalFormat("0.0000");
				double routingMissDistanceLocal = stats.routingMissDistanceLocal.currentValue();
				double routingMissDistanceRemote = stats.routingMissDistanceRemote.currentValue();
				double routingMissDistanceOverall = stats.routingMissDistanceOverall.currentValue();
				double routingMissDistanceBulk = stats.routingMissDistanceBulk.currentValue();
				double routingMissDistanceRT = stats.routingMissDistanceRT.currentValue();
				double backedOffPercent = stats.backedOffPercent.currentValue();
				String nodeUptimeString = TimeUtil.formatTime(
					nodeUptimeSeconds * 1000);  // *1000 to convert to milliseconds
				// BEGIN OVERVIEW TABLE
				Table overviewTable = connectionsPage.content.addTable(Category.COLUMN);
				Row overviewTableRow = overviewTable.addRow();
				Cell nextTableCell = overviewTableRow.addCell(Category.FIRST);
				OutputList overviewList = nextTableCell.addInfobox(InfoboxType.NONE,
					"Node status overview").body.addList();
				overviewList.addItem("bwlimitDelayTime:\u00a0" + bwlimitDelayTime + "ms");
				overviewList.addItem("nodeAveragePingTime:\u00a0" + nodeAveragePingTime + "ms");
				overviewList.addItem("darknetSizeEstimateSession:\u00a0" +
					networkSizeEstimateSession +
					"\u00a0nodes");
				if (nodeUptimeSeconds > (48 * 60 * 60)) {  // 48 hours
					overviewList.addItem(
						"darknetSizeEstimateRecent:\u00a0" + networkSizeEstimateRecent +
							"\u00a0nodes");
				}
				overviewList.addItem("nodeUptime:\u00a0" + nodeUptimeString);
				overviewList.addItem(
					"routingMissDistanceLocal:\u00a0" + fix4.format(routingMissDistanceLocal));
				overviewList.addItem(
					"routingMissDistanceRemote:\u00a0" + fix4.format(routingMissDistanceRemote));
				overviewList.addItem(
					"routingMissDistanceOverall:\u00a0" + fix4.format
						(routingMissDistanceOverall));
				overviewList.addItem(
					"routingMissDistanceBulk:\u00a0" + fix4.format(routingMissDistanceBulk));
				overviewList
					.addItem("routingMissDistanceRT:\u00a0" + fix4.format(routingMissDistanceRT));
				overviewList.addItem("backedOffPercent:\u00a0" + fix1.format(backedOffPercent));
				overviewList.addItem(
					"pInstantReject:\u00a0" + fix1.format(stats.pRejectIncomingInstantly()));
				// Activity box
				int numARKFetchers = node.getNumARKFetchers();
				nextTableCell = overviewTableRow.addCell();
				Infobox activityInfobox =
					nextTableCell.addInfobox(InfoboxType.NONE, l10n("activityTitle"));
				OutputList activityList = StatisticsToadlet.drawActivity(activityInfobox.body, node);
				if (advancedMode && (activityList != null)) {
					if (numARKFetchers > 0) {
						activityList.addItem(
							"ARK\u00a0Fetch\u00a0Requests:\u00a0" + numARKFetchers);
					}
					StatisticsToadlet
						.drawBandwidth(activityList, node, nodeUptimeSeconds, advancedMode);
				}
				// Peer statistics box
				nextTableCell = overviewTableRow.addCell(Category.LAST);
				Infobox peerStatsInfobox =
					nextTableCell.addInfobox(InfoboxType.NONE, null);
				StatisticsToadlet.drawPeerStatsBox(peerStatsInfobox, advancedMode, numberOfConnected,
					numberOfRoutingBackedOff, numberOfTooNew, numberOfTooOld,
					numberOfDisconnected,
					numberOfNeverConnected, numberOfDisabled, numberOfBursting, numberOfListening,
					numberOfListenOnly, 0, 0, numberOfRoutingDisabled, numberOfClockProblem,
					numberOfConnError, numberOfDisconnecting, numberOfNoLoadStats, node);
				// Peer routing backoff reason box
				if (advancedMode) {
					Infobox backoffReasonInfobox =
						new Infobox(InfoboxType.NONE, null);
					nextTableCell.addChild(backoffReasonInfobox);
					String[] routingBackoffReasons = peers.getPeerNodeRoutingBackoffReasons(true);
					int total = 0;
					if (routingBackoffReasons.length == 0) {
						backoffReasonInfobox.body.addText(
							NodeL10n.getBase().getString("StatisticsToadlet" +
								".notBackedOff"));
					} else {
						OutputList reasonList = backoffReasonInfobox.body.addList();
						for (int i = 0; i < routingBackoffReasons.length; i++) {
							int reasonCount = peers.getPeerNodeRoutingBackoffReasonSize(
								routingBackoffReasons[i], true);
							if (reasonCount > 0) {
								total += reasonCount;
								reasonList.addItem(routingBackoffReasons[i] +
									'\u00a0' +
									reasonCount);
							}
						}
					}
					if (total > 0) {
						backoffReasonInfobox.header
							.setContent("Peer backoff reasons (realtime): " + total);
					} else {
						backoffReasonInfobox.header
							.setContent("Peer backoff reasons (realtime)");
					}
					backoffReasonInfobox = new Infobox(InfoboxType.NONE, null);
					nextTableCell.addChild(backoffReasonInfobox);
					routingBackoffReasons = peers.getPeerNodeRoutingBackoffReasons(false);
					total = 0;
					if (routingBackoffReasons.length == 0) {
						backoffReasonInfobox.body.addText(
							NodeL10n.getBase().getString("StatisticsToadlet" +
								".notBackedOff"));
					} else {
						OutputList reasonList = backoffReasonInfobox.body.addList();
						for (int i = 0; i < routingBackoffReasons.length; i++) {
							int reasonCount = peers.getPeerNodeRoutingBackoffReasonSize(
								routingBackoffReasons[i], false);
							if (reasonCount > 0) {
								total += reasonCount;
								reasonList.addItem(routingBackoffReasons[i] +
									'\u00a0' +
									reasonCount);
							}
						}
					}
					if (total > 0) {
						backoffReasonInfobox.header
							.setContent("Peer backoff reasons (bulk): " + total);
					} else {
						backoffReasonInfobox.header.setContent("Peer backoff reasons (bulk)");
					}
				}
				// END OVERVIEW TABLE
			}
			boolean enablePeerActions = showPeerActionsBox();
			// BEGIN PEER TABLE
			if (fProxyJavascriptEnabled) {
				StringBuilder jsBuf = new StringBuilder();
				// FIXME: There's probably some icky Javascript in here (this is the first thing
				// that worked for me); feel free to fix up to Javascript guru standards
				jsBuf.append("  function peerNoteChange() {\n");
				jsBuf.append("    var theobj = document.getElementById( \"action\" );\n");
				jsBuf.append("    var length = theobj.options.length;\n");
				jsBuf.append("    for (var i = 0; i < length; i++) {\n");
				jsBuf.append("      if(theobj.options[i] == \"update_notes\") {\n");
				jsBuf.append("        theobj.options[i].select = true;\n");
				jsBuf.append("      } else {\n");
				jsBuf.append("        theobj.options[i].select = false;\n");
				jsBuf.append("      }\n");
				jsBuf.append("    }\n");
				jsBuf.append("    theobj.value=\"update_notes\";\n");
				//jsBuf.append( "    document.getElementById( \"peersForm\" ).submit();\n" );
				jsBuf.append("    document.getElementById( \"peersForm\" ).doAction.click();\n");
				jsBuf.append("  }\n");
				jsBuf.append("  function peerNoteBlur() {\n");
				jsBuf.append("    var theobj = document.getElementById( \"action\" );\n");
				jsBuf.append("    var length = theobj.options.length;\n");
				jsBuf.append("    for (var i = 0; i < length; i++) {\n");
				jsBuf.append("      if(theobj.options[i] == \"update_notes\") {\n");
				jsBuf.append("        theobj.options[i].select = true;\n");
				jsBuf.append("      } else {\n");
				jsBuf.append("        theobj.options[i].select = false;\n");
				jsBuf.append("      }\n");
				jsBuf.append("    }\n");
				jsBuf.append("    theobj.value=\"update_notes\";\n");
				jsBuf.append("  }\n");
				connectionsPage.content.addChild("script", "type", "text/javascript")
					.addChild("%", jsBuf.toString());
			}
			Infobox peerTableInfobox = connectionsPage.content.addInfobox(InfoboxType.NORMAL,
				null);
			peerTableInfobox.header.addText(getPeerListTitle());
			if (advancedMode) {
				if (! path.endsWith("displaymessagetypes.html")) {
					peerTableInfobox.header.addText(" ");
					peerTableInfobox.header
						.addLink("displaymessagetypes.html", l10n("bracketedMoreDetailed"));
				}
			}
			if (! isOpennet()) {
				BlockText myName = peerTableInfobox.body.addBlockText();
				myName.addInlineBox(NodeL10n.getBase()
					.getString("DarknetConnectionsToadlet.myName", "name", node.getMyName()));
				myName.addInlineBox(" [");
				myName.addInlineBox().addLink("/config/node#name",
					NodeL10n.getBase().getString("DarknetConnectionsToadlet.changeMyName"));
				myName.addInlineBox("]");
			}
			if (peerNodeStatuses.length == 0) {
				NodeL10n.getBase().addL10nSubstitution(peerTableInfobox.body,
					"DarknetConnectionsToadlet.noPeersWithHomepageLink",
					new String[]{"link"}, new HTMLNode[]{new Link("/")});
			} else {
				HTMLNode peerForm = null;
				Table peerTable;
				if (enablePeerActions) {
					peerForm = ctx.addFormChild(peerTableInfobox.body, ".", "peersForm");
					peerTable = new Table(Category.DARKNETCONNECTIONS);
					peerForm.addChild(peerTable);
				} else {
					peerTable = new Table(Category.DARKNETCONNECTIONS);
					peerTableInfobox.body.addChild(peerTable);
				}
				Row peerTableHeaderRow = peerTable.addRow();
				if (enablePeerActions) {
					peerTableHeaderRow.addHeader();
				}
				peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "status"))
					.addText(l10n("statusTitle"));
				if (hasNameColumn()) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "name"))
						.addInlineBox(
							Category.CONNECTIONHELP, l10n("nameClickToMessage"),
							l10n("nameTitle"));
				}
				if (hasTrustColumn()) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "trust"))
						.addInlineBox(
							Category.CONNECTIONHELP, l10n("trustMessage"),
							l10n("trustTitle"));
				}
				if (hasVisibilityColumn()) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "trust"))
						.addInlineBox(
							Category.CONNECTIONHELP, l10n("visibilityMessage" +
							(advancedMode ? "Advanced" : "Simple")),
							l10n("visibilityTitle"));
				}
				if (advancedMode) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "address"))
						.addInlineBox(
							Category.CONNECTIONHELP, l10n("ipAddress"),
							l10n("ipAddressTitle"));
				}
				peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "version"))
					.addText(l10n("versionTitle"));
				if (advancedMode) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "location"))
						.addText("Location");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "backoffRT"))
						.addInlineBox(
							Category.CONNECTIONHELP,
							"Other node busy (realtime)? Display: Percentage of time " +
								"the node is overloaded, Current wait time " +
								"remaining (0=not overloaded)/total/last overload " +
								"reason",
							"Backoff (realtime)");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "backoffBulk"))
						.addInlineBox(
							Category.CONNECTIONHELP,
							"Other node busy (bulk)? Display: Percentage of time the " +
								"node is overloaded, Current wait time remaining " +
								"(0=not overloaded)/total/last overload reason",
							"Backoff (bulk)");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "overload_p"))
						.addInlineBox(
							Category.CONNECTIONHELP,
							"Probability of the node rejecting a request due to " +
								"overload or causing a timeout.",
							"Overload Probability");
				}
				peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "idle")).addInlineBox(
					Category.CONNECTIONHELP, l10n("idleTime"), l10n("idleTimeTitle"));
				if (hasPrivateNoteColumn()) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "privnote"))
						.addInlineBox(
							Category.CONNECTIONHELP, l10n("privateNote"),
							l10n("privateNoteTitle"));
				}
				if (advancedMode) {
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed,
						"time_routable"))
						.addText("%\u00a0Time Routable");
					peerTableHeaderRow.addHeader()
						.addLink(sortString(isReversed, "selection_percentage"))
						.addText("%\u00a0Selection");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed,
						"total_traffic"))
						.addText("Total\u00a0Traffic\u00a0(in/out/resent)");
					peerTableHeaderRow.addHeader()
						.addLink(sortString(isReversed, "total_traffic_since_startup"))
						.addText("Total\u00a0Traffic\u00a0(in/out) since startup");
					peerTableHeaderRow.addHeader("Congestion\u00a0Control");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "time_delta"))
						.addText("Time\u00a0Delta");
					peerTableHeaderRow.addHeader().addLink(sortString(isReversed, "uptime"))
						.addText("Reported\u00a0Uptime");
					peerTableHeaderRow.addHeader("Transmit\u00a0Queue");
					peerTableHeaderRow.addHeader("Peer\u00a0Capacity\u00a0Bulk");
					peerTableHeaderRow.addHeader("Peer\u00a0Capacity\u00a0Realtime");
				}
				SimpleColumn[] endCols = endColumnHeaders(advancedMode);
				if (endCols != null) {
					for (int i = 0; i < endCols.length; i++) {
						SimpleColumn col = endCols[i];
						OutputNode header = peerTableHeaderRow.addHeader();
						String sortString = col.getSortString();
						InlineBox span = new InlineBox(Category.CONNECTIONHELP,
							NodeL10n.getBase().getString(col.getExplanationKey()),
							NodeL10n.getBase().getString(col.getTitleKey()));
						if (sortString != null) {
							header.addLink(sortString(isReversed, sortString))
								.addChild(span);
						} else {
							header.addChild(span);
						}
					}
				}
				double totalSelectionRate = 0.0;
				for (PeerNodeStatus status : peerNodeStatuses) {
					totalSelectionRate += status.getSelectionRate();
				}
				for (int peerIndex = 0, peerCount = peerNodeStatuses.length; peerIndex < peerCount;
				     peerIndex++) {
					PeerNodeStatus peerNodeStatus = peerNodeStatuses[peerIndex];
					drawRow(peerTable, peerNodeStatus, advancedMode, fProxyJavascriptEnabled, now,
						path, enablePeerActions, endCols, drawMessageTypes,
						totalSelectionRate,
						fix1);
				}
				if (peerForm != null) {
					drawPeerActionSelectBox(peerForm, advancedMode);
				}
			}
			// END PEER TABLE
		} else {
			if (! isOpennet()) {
				try {
					throw new RedirectException("/addfriend/");
				} catch (URISyntaxException e) {
					Logger.error(this, "Impossible: " + e + " for /addfriend/", e);
				}
			}
		}
		// our reference
		if (shouldDrawNoderefBox(advancedMode)) {
			drawAddPeerBox(connectionsPage.content, ctx);
			drawNoderefBox(connectionsPage.content, getNoderef(), true);
		}
		this.writeHTMLReply(ctx, 200, "OK", connectionsPage.generate());
	}

	protected abstract boolean acceptRefPosts();
	
	/** Where to redirect to if there is an error */
	protected abstract String defaultRedirectLocation();

	public void handleMethodPOST(URI uri, final HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		boolean logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
		
		if(!acceptRefPosts()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if((pass == null) || !pass.equals(core.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", defaultRedirectLocation());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			if(logMINOR) Logger.minor(this, "No password ("+pass+" should be "+core.formPassword+ ')');
			return;
		}
		if (request.isPartSet("add")) {
			// add a new node
			String urltext = request.getPartAsStringFailsafe("url", 200);
			urltext = urltext.trim();
			String reftext = request.getPartAsStringFailsafe("ref", Integer.MAX_VALUE);
			reftext = reftext.trim();
			if (reftext.length() < 200) {
				reftext = request.getPartAsStringFailsafe("reffile", Integer.MAX_VALUE);
				reftext = reftext.trim();
			}
			String privateComment = null;
			if (! isOpennet())
				privateComment = request.getPartAsStringFailsafe("peerPrivateNote", 250).trim();
			String trustS = request.getPartAsStringFailsafe("trust", 10);
			FRIEND_TRUST trust = null;
			if (trustS != null && ! trustS.equals(""))
				trust = FRIEND_TRUST.valueOf(trustS);
			String visibilityS = request.getPartAsStringFailsafe("visibility", 10);
			FRIEND_VISIBILITY visibility = null;
			if (visibilityS != null && ! visibilityS.equals(""))
				visibility = FRIEND_VISIBILITY.valueOf(visibilityS);
			if (trust == null && ! isOpennet()) {
				// FIXME: Layering violation. Ideally DarknetPeerNode would do this check.
				this.sendErrorPage(ctx, 200, l10n("noTrustLevelAddingFriendTitle"),
					l10n("noTrustLevelAddingFriend"), ! isOpennet());
				return;
			}
			if (visibility == null && ! isOpennet()) {
				// FIXME: Layering violation. Ideally DarknetPeerNode would do this check.
				this.sendErrorPage(ctx, 200, l10n("noVisibilityLevelAddingFriendTitle"),
					l10n("noVisibilityLevelAddingFriend"), ! isOpennet());
				return;
			}
			StringBuilder ref = new StringBuilder(1024);
			if (urltext.length() > 0) {
				// fetch reference from a URL
				BufferedReader in = null;
				try {
					URL url = new URL(urltext);
					URLConnection uc = url.openConnection();
					// FIXME get charset encoding from uc.getContentType()
					in = new BufferedReader(new InputStreamReader(uc.getInputStream()));
					String line;
					while ((line = in.readLine()) != null) {
						ref.append(line).append('\n');
					}
				} catch (IOException e) {
					this.sendErrorPage(ctx, 200, l10n("failedToAddNodeTitle"), NodeL10n.getBase()
						.getString("DarknetConnectionsToadlet.cantFetchNoderefURL",
							new String[]{"url"}, new String[]{urltext}), ! isOpennet());
					return;
				} finally {
					if (in != null) {
						in.close();
					}
				}
			} else if (reftext.length() > 0) {
				// read from post data or file upload
				// this slightly scary looking regexp chops any extra characters off the beginning or ends of lines and removes extra line breaks
				ref = new StringBuilder(
					reftext.replaceAll(".*?((?:[\\w,\\.]+\\=[^\r\n]+?)|(?:End))[ \\t]*(?:\\r?\\n)+",

						"$1\n"));
			} else {
				this.sendErrorPage(ctx, 200, l10n("failedToAddNodeTitle"), l10n("noRefOrURL"),
					! isOpennet());
				request.freeParts();
				return;
			}
			ref = new StringBuilder(ref.toString().trim());
			request.freeParts();
			//Split the references string, because the peers are added individually
			// FIXME split by lines at this point rather than in addNewNode would be more efficient
			int idx;
			while ((idx = ref.indexOf("\r\n")) > - 1) {
				ref.deleteCharAt(idx);
			}
			while ((idx = ref.indexOf("\r")) > - 1) {
				// Mac's just use \r
				ref.setCharAt(idx, '\n');
			}
			String[] nodesToAdd = ref.toString().split("\nEnd\n");
			for (int i = 0; i < nodesToAdd.length; i++) {
				String[] split = nodesToAdd[i].split("\n");
				StringBuffer sb = new StringBuffer(nodesToAdd[i].length());
				boolean first = true;
				for (String s : split) {
					if (s.equals("End"))
						break;
					if (s.indexOf('=') > - 1) {
						if (! first)
							sb.append('\n');
					} else {
						// Try appending it - don't add a newline.
						// This will make broken refs work sometimes.
					}
					sb.append(s);
					first = false;
				}
				nodesToAdd[i] = sb.toString();
				// Don't need to add a newline at the end, we will do that later.
			}
			//The peer's additions results
			Map<PeerAdditionReturnCodes, Integer> results = new HashMap<PeerAdditionReturnCodes, Integer>();

			for (int i = 0; i < nodesToAdd.length; i++) {
				//We need to trim then concat 'End' to the node's reference, this way we have a normal reference(the split() removes the 'End'-s!)
				PeerAdditionReturnCodes result =
					addNewNode(nodesToAdd[i].trim().concat("\nEnd"), privateComment, trust,
						visibility);
				//Store the result
				if (results.containsKey(result) == false) {
					results.put(result, Integer.valueOf(0));
				}
				results.put(result, results.get(result) + 1);
			}
			Page addedNodesPage = ctx.getPageMaker().getPage(l10n("reportOfNodeAddition"), ctx);
			//We create a table to show the results
			Table detailedStatusBox = new Table();
			//Header of the table
			Row headerRow = detailedStatusBox.addRow();
			headerRow.addHeader(l10n("resultName"));
			headerRow.addHeader(l10n(l10n("numOfResults")));
			detailedStatusBox.addBody();
			//Iterate through the return codes
			for (PeerAdditionReturnCodes returnCode : PeerAdditionReturnCodes.values()) {
				if (results.containsKey(returnCode)) {
					//Add a <tr> and 2 <td> with the name of the code and the number of
					// occasions it happened. If the code is OK, we use green, red elsewhere.
					Row StatusRow = detailedStatusBox.body.addRow();
					StatusRow.addAttribute("style", "color:" +
						(returnCode == PeerAdditionReturnCodes.OK ? "green" : "red"));
					StatusRow.addCell(l10n("peerAdditionCode." + returnCode.toString()));
					StatusRow.addCell(results.get(returnCode).toString());
				}
			}
			Infobox nodeAdded =
				addedNodesPage.content.addInfobox(Identifier.NODEADDED, l10n("reportOfNodeAddition"));
			nodeAdded.body.addChild(detailedStatusBox);
			if (! isOpennet()) {
				nodeAdded.body.addBlockText().addLink("/addfriend/", l10n("addAnotherFriend"));
			}
			nodeAdded.body.addBlockText().addLink(path(), l10n("goFriendConnectionStatus"));
			addHomepageLink(nodeAdded.body.addBlockText());
			writeHTMLReply(ctx, 500, l10n("reportOfNodeAddition"), nodeAdded.generate());
		} else {
			handleAltPost(uri, request, ctx, logMINOR);
		}
	}
	
	/** Adds a new node. If any error arises, it returns the appropriate return code.
	 * @param nodeReference - The reference to the new node
	 * @param privateComment - The private comment when adding a Darknet node
	 * @param trust 
	 * @return The result of the addition*/
	private PeerAdditionReturnCodes addNewNode(String nodeReference,String privateComment, FRIEND_TRUST trust, FRIEND_VISIBILITY visibility){
		SimpleFieldSet fs;
		
		try {
			nodeReference = Fields.trimLines(nodeReference);
			fs = new SimpleFieldSet(nodeReference, false, true);
			if(!fs.getEndMarker().endsWith("End")) {
				Logger.error(this, "Trying to add noderef with end marker \""+fs.getEndMarker()+"\"");
				return PeerAdditionReturnCodes.WRONG_ENCODING;
			}
			fs.setEndMarker("End"); // It's always End ; the regex above doesn't always grok this
		} catch (IOException e) {
            Logger.error(this, "IOException adding reference :" + e.getMessage(), e);
			return PeerAdditionReturnCodes.CANT_PARSE;
		} catch (Throwable t) {
		    Logger.error(this, "Internal error adding reference :" + t.getMessage(), t);
			return PeerAdditionReturnCodes.INTERNAL_ERROR;
		}
		PeerNode pn;
		try {
			if(isOpennet()) {
				pn = node.createNewOpennetNode(fs);
			} else {
				pn = node.createNewDarknetNode(fs, trust, visibility);
				((DarknetPeerNode)pn).setPrivateDarknetCommentNote(privateComment);
			}
		} catch (FSParseException e1) {
			return PeerAdditionReturnCodes.CANT_PARSE;
		} catch (PeerParseException e1) {
			return PeerAdditionReturnCodes.CANT_PARSE;
		} catch (ReferenceSignatureVerificationException e1){
			return PeerAdditionReturnCodes.INVALID_SIGNATURE;
		} catch (Throwable t) {
            Logger.error(this, "Internal error adding reference :" + t.getMessage(), t);
			return PeerAdditionReturnCodes.INTERNAL_ERROR;
		}
		if(Arrays.equals(pn.getIdentity(), node.getDarknetIdentity())) {
			return PeerAdditionReturnCodes.TRY_TO_ADD_SELF;
		}
		if(!this.node.addPeerConnection(pn)) {
			return PeerAdditionReturnCodes.ALREADY_IN_REFERENCE;
		}
		return PeerAdditionReturnCodes.OK;
	}

	/** Adding a darknet node or an opennet node? */
	protected abstract boolean isOpennet();

	/**
	 * Rest of handlePost() method - supplied by subclass.
	 * @throws IOException 
	 * @throws ToadletContextClosedException 
	 * @throws RedirectException 
	 */
	protected void handleAltPost(URI uri, HTTPRequest request, ToadletContext ctx, boolean logMINOR) throws ToadletContextClosedException, IOException, RedirectException {
		// Do nothing - we only support adding nodes
		handleMethodGET(uri, new HTTPRequestImpl(uri, "GET"), ctx);
	}

	/**
	 * What should the heading (before "(more detailed)") be on the peers table?
	 */
	protected abstract String getPeerListTitle();

	/** Should there be a checkbox for each peer, and drawPeerActionSelectBox() be called directly
	 * after drawing the peers list? */
	protected abstract boolean showPeerActionsBox();

	/** If showPeerActionsBox() is true, this will be called directly after drawing the peers table.
	 * A form has been added, and checkboxes added for each peer. This function should draw the rest
	 * of the form - any additional controls and one or more submit buttons.
	 */
	protected abstract void drawPeerActionSelectBox(HTMLNode peerForm, boolean advancedModeEnabled);
	
	protected abstract boolean shouldDrawNoderefBox(boolean advancedModeEnabled);

	static final HTMLNode REF_LINK = new Link("myref.fref").setReadOnly();
	static final HTMLNode REFTEXT_LINK = new Link("myref.txt").setReadOnly();

	/**
	 *
	 * @param contentNode Node to add noderef box to.
	 * @param fs Noderef to render as text if requested.
	 * @param showNoderef If true, render the text of the noderef so that it may be copy-pasted. If false, only
	 *                    show a link to download it.
	 */
	static void drawNoderefBox(HTMLNode contentNode, SimpleFieldSet fs, boolean showNoderef) {
		Infobox referenceInfobox = new Infobox(InfoboxType.NORMAL, null);
		contentNode.addChild(referenceInfobox);
		// FIXME better way to deal with this sort of thing???
		NodeL10n.getBase().addL10nSubstitution(referenceInfobox.header, "DarknetConnectionsToadlet.myReferenceHeader",
				new String[] { "linkref", "linktext" },
				new HTMLNode[] { REF_LINK, REFTEXT_LINK });

		BlockText myName = referenceInfobox.body.addBlockText();
		myName.addInlineBox(NodeL10n.getBase().getString("DarknetConnectionsToadlet.myName", "name", fs.get("myName")));
		myName.addInlineBox(" [");
		myName.addInlineBox().addLink("/config/node#name", NodeL10n.getBase().getString("DarknetConnectionsToadlet.changeMyName"));
		myName.addInlineBox("]");

		if (showNoderef) {
			HTMLNode warningSentence = referenceInfobox.body.addBlockText();
			NodeL10n.getBase().addL10nSubstitution(warningSentence, "DarknetConnectionsToadlet.referenceCopyWarning",
					new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
			referenceInfobox.body.addChild("pre", "id", "reference", fs.toString() + '\n');
		}
	}

	protected abstract String getPageTitle(String titleCountString);

	/** Draw the add a peer box. This comes immediately after the main peers table and before the noderef box.
	 * Implementors may skip it by not doing anything in this method. */
	protected void drawAddPeerBox(HTMLNode contentNode, ToadletContext ctx) {
		drawAddPeerBox(contentNode, ctx, isOpennet(), path());
	}
	
	protected static void drawAddPeerBox(HTMLNode contentNode, ToadletContext ctx, boolean isOpennet, String formTarget) {
		// BEGIN PEER ADDITION BOX
		Infobox peerAdditionInfobox = new Infobox(InfoboxType.NORMAL, l10n(isOpennet ? "addOpennetPeerTitle" : "addPeerTitle"));
		contentNode.addChild(peerAdditionInfobox);
		HTMLNode peerAdditionForm = ctx.addFormChild(peerAdditionInfobox.body, formTarget, "addPeerForm");
		peerAdditionForm.addText(l10n("pasteReference"));
		peerAdditionForm.addLineBreak();
		peerAdditionForm.addChild("textarea", new String[] { "id", "name", "rows", "cols" }, new String[] { "reftext", "ref", "8", "74" });
		peerAdditionForm.addLineBreak();
		peerAdditionForm.addText((l10n("urlReference") + ' '));
		peerAdditionForm.addInput(InputType.TEXT, "url",  Identifier.REFURL);
		peerAdditionForm.addLineBreak();
		peerAdditionForm.addText((l10n("fileReference") + ' '));
		peerAdditionForm.addInput(InputType.TEXT, "reffile",  Identifier.REFURL);
		peerAdditionForm.addLineBreak();
		if(!isOpennet) {
			peerAdditionForm.addInlineBox(Category.BOLD, l10n("peerTrustTitle"));
			peerAdditionForm.addText(" ");
			peerAdditionForm.addText(l10n("peerTrustIntroduction"));
			for(FRIEND_TRUST trust : FRIEND_TRUST.valuesBackwards()) { // FIXME reverse order
				HTMLNode input = peerAdditionForm.addLineBreak().addInput(InputType.RADIO, "trust", trust.name());
				input.addInlineBox(Category.BOLD, l10n("peerTrust." + trust.name())); // FIXME l10n
				input.addText(": ");
				input.addText(l10n("peerTrustExplain." + trust.name()));
			}
			peerAdditionForm.addLineBreak();
			
			peerAdditionForm.addInlineBox(Category.BOLD, l10n("peerVisibilityTitle"));
			peerAdditionForm.addText(" ");
			peerAdditionForm.addText(l10n("peerVisibilityIntroduction"));
			for(FRIEND_VISIBILITY trust : FRIEND_VISIBILITY.values()) { // FIXME reverse order
				HTMLNode input = peerAdditionForm.addLineBreak().addInput(InputType.RADIO, "visibility", trust.name());
				input.addInlineBox(Category.BOLD, l10n("peerVisibility." + trust.name())); // FIXME l10n
				input.addText(": ");
				input.addText(l10n("peerVisibilityExplain." + trust.name()));
			}
			peerAdditionForm.addLineBreak();
			
		}
		
		if(!isOpennet) {
			peerAdditionForm.addText((l10n("enterDescription") + ' '));
			peerAdditionForm.addInput(InputType.TEXT, "peerPrivateNote", "",  16, (short) 250, Identifier.PEERPRIVATENOTE);
			peerAdditionForm.addLineBreak();
		}
		peerAdditionForm.addInput(InputType.SUBMIT, "add", l10n("add"));
	}

	protected Comparator<PeerNodeStatus> comparator(String sortBy, boolean reversed) {
		return new ComparatorByStatus(sortBy, reversed);
	}

	abstract protected PeerNodeStatus[] getPeerNodeStatuses(boolean noHeavy);

	abstract protected SimpleFieldSet getNoderef();

	private void drawRow(Table peerTable, PeerNodeStatus peerNodeStatus, boolean advancedModeEnabled, boolean fProxyJavascriptEnabled, long now, String path, boolean enablePeerActions, SimpleColumn[] endCols, boolean drawMessageTypes, double totalSelectionRate, DecimalFormat fix1) {
		double selectionRate = peerNodeStatus.getSelectionRate();
		int peerSelectionPercentage = 0;
		if(totalSelectionRate > 0) {
			peerSelectionPercentage = (int) (selectionRate * 100 / totalSelectionRate);
		}
		Row peerRow = peerTable.addRow((peerSelectionPercentage > PeerNode.SELECTION_PERCENTAGE_WARNING ? Category.DARKNETCONNECTIONSWARNING: Category.DARKNETCONNECTIONSNORMAL));
		
		if(enablePeerActions) {
			// check box column
			peerRow.addCell(Category.PEERMARKER).addInput("node_" + peerNodeStatus.hashCode(), InputType.CHECKBOX);
		}

		// status column
		String statusString = peerNodeStatus.getStatusName();
		if (!advancedModeEnabled && (peerNodeStatus.getStatusValue() == PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF)) {
			statusString = "BUSY";
		}
		InlineBox statusBox = peerRow.addCell(Category.PEERSTATUS).addInlineBox(NodeL10n.getBase().getString("ConnectionsToadlet.nodeStatus." + statusString) + (peerNodeStatus.isFetchingARK() ? "*" : ""));
		statusBox.addAttribute("class", peerNodeStatus.getStatusCSSName());

		drawNameColumn(peerRow, peerNodeStatus, advancedModeEnabled);
		
		drawTrustColumn(peerRow, peerNodeStatus);
		
		drawVisibilityColumn(peerRow, peerNodeStatus, advancedModeEnabled);
		
		// address column
		if (advancedModeEnabled) {
			String pingTime = "";
			if (peerNodeStatus.isConnected()) {
				pingTime = " (" + (int) peerNodeStatus.getAveragePingTime() + "ms / " +
				(int) peerNodeStatus.getAveragePingTimeCorrected()+"ms)";
			}
			Cell addressRow = peerRow.addCell(Category.PEERADDRESS);
			// Ip to country + Flags
			IPConverter ipc = IPConverter.getInstance(node.runDir().file(NodeUpdateManager.IPV4_TO_COUNTRY_FILENAME));
			// Only IPv4 at the time
			String addr = peerNodeStatus.getPeerAddressNumerical();
			if(addr != null && !addr.contains(":")) {
				Country country = ipc.locateIP(addr);
				if(country != null) {
					country.renderFlagIcon(addressRow);
				}
			}
			addressRow.addText(((peerNodeStatus.getPeerAddress() != null) ? (peerNodeStatus.getPeerAddress() + ':' + peerNodeStatus.getPeerPort()) : (l10n("unknownAddress"))) + pingTime);
		}

		// version column
		if (peerNodeStatus.getStatusValue() != PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED && (peerNodeStatus.isPublicInvalidVersion() || peerNodeStatus.isPublicReverseInvalidVersion())) {  // Don't draw attention to a version problem if NEVER CONNECTED
			peerRow.addCell(Category.PEERVERSION).addInlineBox(Category.PEERVERSIONPROBLEM, Integer.toString(peerNodeStatus.getSimpleVersion()));
		} else {
			peerRow.addCell(Category.PEERVERSION).addText(Integer.toString(peerNodeStatus.getSimpleVersion()));
		}

		// location column
		if (advancedModeEnabled) {
			Cell locationNode = peerRow.addCell(Category.PEERLOCATION);
			locationNode.addInlineBox(Category.BOLD, String.valueOf(peerNodeStatus.getLocation()));
			locationNode.addLineBreak();
			double[] peersLoc = peerNodeStatus.getPeersLocation();
			if(peersLoc != null) {
				for(double loc : peersLoc)
					locationNode.addInlineBox(Category.ITALIC, String.valueOf(loc)).addLineBreak();
			}
		}

		if (advancedModeEnabled) {
			// backoff column
			Cell backoffCell = peerRow.addCell(Category.PEERBACKOFF);
			backoffCell.addText(fix1.format(peerNodeStatus.getBackedOffPercent(true)));
			int backoff = (int) (Math.max(peerNodeStatus.getRoutingBackedOffUntil(true) - now, 0));
			// Don't list the backoff as zero before it's actually zero
			if ((backoff > 0) && (backoff < 1000)) {
				backoff = 1000;
			}
			backoffCell.addText(' ' + String.valueOf(backoff / 1000) + '/' + String.valueOf(peerNodeStatus.getRoutingBackoffLength(true) / 1000));
			backoffCell.addText((peerNodeStatus.getLastBackoffReason(true) == null) ? "" : ('/' + (peerNodeStatus.getLastBackoffReason(true))));

			// backoff column
			backoffCell = peerRow.addCell(Category.PEERBACKOFF);
			backoffCell.addText(fix1.format(peerNodeStatus.getBackedOffPercent(false)));
			backoff = (int) (Math.max(peerNodeStatus.getRoutingBackedOffUntil(false) - now, 0));
			// Don't list the backoff as zero before it's actually zero
			if ((backoff > 0) && (backoff < 1000)) {
				backoff = 1000;
			}
			backoffCell.addText(' ' + String.valueOf(backoff / 1000) + '/' + String.valueOf(peerNodeStatus.getRoutingBackoffLength(false) / 1000));
			backoffCell.addText((peerNodeStatus.getLastBackoffReason(false) == null) ? "" : ('/' + (peerNodeStatus.getLastBackoffReason(false))));

			// overload probability column
			Cell pRejectCell = peerRow.addCell(Category.PEERBACKOFF); // FIXME
			pRejectCell.addText(fix1.format(peerNodeStatus.getPReject()));
		}

		// idle column
		long idle = peerNodeStatus.getTimeLastRoutable();
		if (peerNodeStatus.isRoutable()) {
			idle = peerNodeStatus.getTimeLastConnectionCompleted();
		} else if (peerNodeStatus.getStatusValue() == PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED) {
			idle = peerNodeStatus.getPeerAddedTime();
		}
		if(!peerNodeStatus.isConnected() && (now - idle) > (2 * 7 * 24 * 60 * 60 * (long) 1000)) { // 2 weeks
			peerRow.addCell(Category.PEERIDLE).addInlineBox(Category.PEERIDLEOLD, idleToString(now, idle));
		} else {
			peerRow.addCell(Category.PEERIDLE, idleToString(now, idle));
		}

		if(hasPrivateNoteColumn())
			drawPrivateNoteColumn(peerRow, peerNodeStatus, fProxyJavascriptEnabled);

		if(advancedModeEnabled) {
			// percent of time connected column
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(fix1.format(peerNodeStatus.getPercentTimeRoutableConnection()));
			// selection stats
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText((totalSelectionRate > 0 ? (peerSelectionPercentage + "%") : "N/A"));
			// total traffic column
			long sent = peerNodeStatus.getTotalOutputBytes();
			long resent = peerNodeStatus.getResendBytesSent();
			long received = peerNodeStatus.getTotalInputBytes();
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(SizeUtil.formatSize(received) + " / " + SizeUtil.formatSize(sent) + "/" + SizeUtil.formatSize(resent) + " (" + fix1.format(((double) resent) / ((double) sent)) + ")");
			// total traffic column startup
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(SizeUtil.formatSize(peerNodeStatus.getTotalInputSinceStartup()) + " / " + SizeUtil.formatSize(peerNodeStatus.getTotalOutputSinceStartup()));
			// congestion control
			PacketThrottle t = peerNodeStatus.getThrottle();
			String val;
			if(t == null)
				val = "none";
			else
				val = (int)t.getBandwidth()+"B/sec delay "+
					t.getDelay()+"ms (RTT "+t.getRoundTripTime()+"ms window "+t.getWindowSize()+')';
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(val);
			// time delta
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(TimeUtil.formatTime(peerNodeStatus.getClockDelta()));
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(peerNodeStatus.getReportedUptimePercentage() + "%");
			peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(SizeUtil.formatSize(peerNodeStatus.getMessageQueueLengthBytes()) + ":" + TimeUtil.formatTime(peerNodeStatus.getMessageQueueLengthTime()));
			IncomingLoadSummaryStats loadStatsBulk = peerNodeStatus.incomingLoadStatsBulk;
			if(loadStatsBulk == null)
				peerRow.addCell(Category.PEERIDLE /* FIXME */);
			else
				peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(loadStatsBulk.runningRequestsTotal + "reqs:out:" + SizeUtil.formatSize(loadStatsBulk.usedCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.othersUsedCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.peerCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.totalCapacityOutputBytes) + ":in:" + SizeUtil.formatSize(loadStatsBulk.usedCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.othersUsedCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.peerCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsBulk.totalCapacityInputBytes));
			IncomingLoadSummaryStats loadStatsRT = peerNodeStatus.incomingLoadStatsRealTime;
			if(loadStatsRT == null)
				peerRow.addCell(Category.PEERIDLE /* FIXME */);
			else
				peerRow.addCell(Category.PEERIDLE /* FIXME */).addText(loadStatsRT.runningRequestsTotal + "reqs:out:" + SizeUtil.formatSize(loadStatsRT.usedCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.othersUsedCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.peerCapacityOutputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.totalCapacityOutputBytes) + ":in:" + SizeUtil.formatSize(loadStatsRT.usedCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.othersUsedCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.peerCapacityInputBytes) + "/" + SizeUtil.formatSize(loadStatsRT.totalCapacityInputBytes));
		}
		
		if(endCols != null) {
			for(int i=0;i<endCols.length;i++) {
				endCols[i].drawColumn(peerRow, peerNodeStatus);
			}
		}
		
		if (drawMessageTypes) {
			drawMessageTypes(peerTable, peerNodeStatus);
		}
	}

	protected boolean hasTrustColumn() {
		return false;
	}

	protected void drawTrustColumn(Row peerRow, PeerNodeStatus peerNodeStatus) {
		// Do nothing
	}

	protected boolean hasVisibilityColumn() {
		return false;
	}

	protected void drawVisibilityColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean advancedModeEnabled) {
		// Do nothing
	}

	/** Is there a name column? */
	abstract protected boolean hasNameColumn();
	
	/**
	 * Draw the name column, if there is one. This will be directly after the status column.
	 */
	abstract protected void drawNameColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean advanced);

	/**
	 * Is there a private note column?
	 */
	abstract protected boolean hasPrivateNoteColumn();

	/**
	 * Draw the private note column.
	 */
	abstract protected void drawPrivateNoteColumn(Row peerRow, PeerNodeStatus peerNodeStatus, boolean fProxyJavascriptEnabled);
	
	private void drawMessageTypes(Table peerTable, PeerNodeStatus peerNodeStatus) {
		Row messageCountRow = peerTable.addRow(Category.MESSAGESTATUS);
		messageCountRow.addCell(2);
		Cell messageCountCell = messageCountRow.addCell(9);  // = total table row width - 2 from above colspan
		Table messageCountTable = messageCountCell.addTable(Category.MESSAGECOUNT);
		Row countHeaderRow = messageCountTable.addRow();
		countHeaderRow.addHeader("Message");
		countHeaderRow.addHeader("Incoming");
		countHeaderRow.addHeader("Outgoing");
		List<String> messageNames = new ArrayList<String>();
		Map<String, Long[]> messageCounts = new HashMap<String, Long[]>();
		for (Map.Entry<String,Long> entry : peerNodeStatus.getLocalMessagesReceived().entrySet() ) {
			String messageName = entry.getKey();
			Long messageCount = entry.getValue();
			messageNames.add(messageName);
			messageCounts.put(messageName, new Long[] { messageCount, Long.valueOf(0) });
		}
		for (Map.Entry<String,Long> entry : peerNodeStatus.getLocalMessagesSent().entrySet() ) {
			String messageName =  entry.getKey();
			Long messageCount = entry.getValue();
			if (!messageNames.contains(messageName)) {
				messageNames.add(messageName);
			}
			Long[] existingCounts = messageCounts.get(messageName);
			if (existingCounts == null) {
				messageCounts.put(messageName, new Long[] { Long.valueOf(0), messageCount });
			} else {
				existingCounts[1] = messageCount;
			}
		}
		Collections.sort(messageNames, new Comparator<String>() {
			@Override
			public int compare(String first, String second) {
				return first.compareToIgnoreCase(second);
			}
		});
		for (Iterator<String> messageNamesIterator = messageNames.iterator(); messageNamesIterator.hasNext(); ) {
			String messageName = messageNamesIterator.next();
			Long[] messageCount = messageCounts.get(messageName);
			Row messageRow = messageCountTable.addRow();
			messageRow.addCell(messageName);
			messageRow.addCell(Category.ALIGNRIGHT, String.valueOf(messageCount[0]));
			messageRow.addCell(Category.ALIGNRIGHT, String.valueOf(messageCount[1]));
		}
	}

	private String idleToString(long now, long idle) {
		if (idle <= 0) {
			return " ";
		}
		long idleMilliseconds = now - idle;
		return TimeUtil.formatTime(idleMilliseconds);
	}
	
	private static String l10n(String string) {
		return NodeL10n.getBase().getString("DarknetConnectionsToadlet."+string);
	}
	
	private String sortString(boolean isReversed, String type) {
		return (isReversed ? ("?sortBy="+type) : ("?sortBy="+type+"&reversed"));
	}

	/**
	 * Send a simple error page.
	 */
	protected void sendErrorPage(ToadletContext ctx, int code, String desc, String message,
	                             boolean returnToAddFriends) throws ToadletContextClosedException, IOException {
		Page errorPage = ctx.getPageMaker().getPage(desc, ctx);
		Infobox errorMessage = errorPage.content.addInfobox(InfoboxType.ERROR, desc);
		errorMessage.body.addText(message);
		if (returnToAddFriends) {
			errorMessage.body.addLineBreak();
			errorMessage.body.addLink(DarknetAddRefToadlet.PATH,
				l10n("returnToAddAFriendPage"));
			errorMessage.body.addLineBreak();
		} else {
			errorMessage.body.addLineBreak();
			errorMessage.body.addLink(".", l10n("returnToPrevPage"));
			errorMessage.body.addLineBreak();
		}
		addHomepageLink(errorMessage.body);
		writeHTMLReply(ctx, code, desc, errorPage.generate());
	}
}
