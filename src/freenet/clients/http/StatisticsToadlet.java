package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientRequester;
import freenet.clients.http.uielements.*;
import freenet.config.SubConfig;
import freenet.crypt.ciphers.Rijndael;
import freenet.io.comm.IncomingPacketFilterImpl;
import freenet.io.xfer.BlockReceiver;
import freenet.io.xfer.BlockTransmitter;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.node.*;
import freenet.node.stats.DataStoreInstanceType;
import freenet.node.stats.DataStoreStats;
import freenet.node.stats.StatsNotAvailableException;
import freenet.node.stats.StoreAccessStats;
import freenet.support.BandwidthStatsContainer;
import freenet.support.HTMLNode;
import freenet.support.SizeUtil;
import freenet.support.TimeUtil;
import freenet.support.api.HTTPRequest;
import freenet.support.io.NativeThread;

import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;

public class StatisticsToadlet extends Toadlet {

	static final NumberFormat thousandPoint = NumberFormat.getInstance();

	private static class STMessageCount {
		public String messageName;
		public int messageCount;

		STMessageCount( String messageName, int messageCount ) {
			this.messageName = messageName;
			this.messageCount = messageCount;
		}
	}

	private final Node node;
	private final NodeClientCore core;
	private final NodeStats stats;
	private final PeerManager peers;
	private final DecimalFormat fix1p1 = new DecimalFormat("0.0");
	private final DecimalFormat fix1p2 = new DecimalFormat("0.00");
	private final DecimalFormat fix1p4 = new DecimalFormat("0.0000");
	private final DecimalFormat fix1p6sci = new DecimalFormat("0.######E0");
	private final DecimalFormat fix3p1pct = new DecimalFormat("##0.0%");
	private final DecimalFormat fix3p1US = new DecimalFormat("##0.0", new DecimalFormatSymbols(Locale.US));
	private final DecimalFormat fix3pctUS = new DecimalFormat("##0%", new DecimalFormatSymbols(Locale.US));
	private final DecimalFormat fix6p6 = new DecimalFormat("#####0.0#####");

	protected StatisticsToadlet(Node n, NodeClientCore core, HighLevelSimpleClient client) {
		super(client);
		this.node = n;
		this.core = core;
		stats = node.nodeStats;
		peers = node.peers;
	}

	/**
	 * Counts the peers in <code>peerNodes</code> that have the specified
	 * status.
	 * @param peerNodeStatuses The peer nodes' statuses
	 * @param status The status to count
	 * @return The number of peers that have the specified status.
	 */
	private int getPeerStatusCount(PeerNodeStatus[] peerNodeStatuses, int status) {
		int count = 0;
		for (int peerIndex = 0, peerCount = peerNodeStatuses.length; peerIndex < peerCount; peerIndex++) {
			if(!peerNodeStatuses[peerIndex].recordStatus())
				continue;
			if (peerNodeStatuses[peerIndex].getStatusValue() == status) {
				count++;
			}
		}
		return count;
	}
	
	private int getCountSeedServers(PeerNodeStatus[] peerNodeStatuses) {
		int count = 0;
		for(int peerIndex = 0; peerIndex < peerNodeStatuses.length; peerIndex++) {
			if(peerNodeStatuses[peerIndex].isSeedServer()) count++;
		}
		return count;
	}

	private int getCountSeedClients(PeerNodeStatus[] peerNodeStatuses) {
		int count = 0;
		for(int peerIndex = 0; peerIndex < peerNodeStatuses.length; peerIndex++) {
			if(peerNodeStatuses[peerIndex].isSeedClient()) count++;
		}
		return count;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {

		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"), NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		final SubConfig nodeConfig = node.config.get("node");
		
		final String requestPath = request.getPath().substring(path().length());

		if (requestPath.length() > 0) {
			if(requestPath.equals("requesters.html") || requestPath.equals("/requesters.html")) {
				showRequesters(request, ctx);
				return;
			}
		}

		node.clientCore.bandwidthStatsPutter.updateData();

		HTMLNode pageNode;
		
		// Synchronize to avoid problems with DecimalFormat.
		synchronized(this) {
		
		/* gather connection statistics */
		PeerNodeStatus[] peerNodeStatuses = peers.getPeerNodeStatuses(true);
		Arrays.sort(peerNodeStatuses, new Comparator<PeerNodeStatus>() {
			@Override
			public int compare(PeerNodeStatus firstNode, PeerNodeStatus secondNode) {
				int statusDifference = firstNode.getStatusValue() - secondNode.getStatusValue();
				if (statusDifference != 0) {
					return statusDifference;
				}
				return 0;
			}
		});

		int numberOfConnected = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CONNECTED);
		int numberOfRoutingBackedOff = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF);
		int numberOfTooNew = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_TOO_NEW);
		int numberOfTooOld = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_TOO_OLD);
		int numberOfDisconnected = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISCONNECTED);
		int numberOfNeverConnected = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_NEVER_CONNECTED);
		int numberOfDisabled = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISABLED);
		int numberOfBursting = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_BURSTING);
		int numberOfListening = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_LISTENING);
		int numberOfListenOnly = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_LISTEN_ONLY);
		int numberOfSeedServers = getCountSeedServers(peerNodeStatuses);
		int numberOfSeedClients = getCountSeedClients(peerNodeStatuses);
		int numberOfRoutingDisabled = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_ROUTING_DISABLED);
		int numberOfClockProblem = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CLOCK_PROBLEM);
		int numberOfConnError = getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_CONN_ERROR);
		int numberOfDisconnecting = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_DISCONNECTING);
		int numberOfNoLoadStats = PeerNodeStatus.getPeerStatusCount(peerNodeStatuses, PeerManager.PEER_NODE_STATUS_NO_LOAD_STATS);

		PageNode page = ctx.getPageMaker().getPageNode(l10n("fullTitle"), ctx);
		boolean advancedMode = ctx.getContainer().isAdvancedModeEnabled();
		pageNode = page.outer;
		HTMLNode contentNode = page.content;

		// FIXME! We need some nice images
		final long now = System.currentTimeMillis();
		double myLocation = node.getLocation();
		final long nodeUptimeSeconds = (now - node.startupTime) / 1000;

		if(ctx.isAllowedFullAccess())
			contentNode.addChild(core.alerts.createSummary());

		double swaps = node.getSwaps();
		double noSwaps = node.getNoSwaps();

		Table overviewTable = new Table(Category.COLUMN);
		contentNode.addChild(overviewTable);

		Row overviewTableRow = overviewTable.addRow();
		Cell nextTableCell = overviewTableRow.addCell(Category.FIRST);

		// node version information box
		InfoboxWidget versionInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
		drawNodeVersionBox(versionInfobox);
		
		// jvm stats box
		InfoboxWidget jvmStatsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
		drawJVMStatsBox(jvmStatsInfobox, advancedMode);
		
		// Statistic gathering box
		InfoboxWidget StatisticsGenerating = new InfoboxWidget(InfoboxWidget.Type.WTF, Identifier.STATISTICSGENERATING, l10n("statisticGatheringTitle"));
		nextTableCell.addInfobox(StatisticsGenerating);
		// Generate a Thread-Dump
		if(node.isUsingWrapper()){
			HTMLNode threadDumpForm = ctx.addFormChild(StatisticsGenerating.body, "/", "threadDumpForm");
			threadDumpForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "getThreadDump", l10n("threadDumpButton")});
		}
		// Get logs
		OutputList logsList = new OutputList();
		StatisticsGenerating.body.addList(logsList);
		if(nodeConfig.config.get("logger").getBoolean("enabled")) {
			logsList.addItem().addLink("/?latestlog", "_blank", l10n("getLogs"));
		}
		logsList.addItem().addLink(TranslationToadlet.TOADLET_URL+"?getOverrideTranlationFile").addText(NodeL10n.getBase().getString("TranslationToadlet.downloadTranslationsFile"));
		logsList.addItem().addLink(DiagnosticToadlet.TOADLET_URL).addText(NodeL10n.getBase().getString("FProxyToadlet.diagnostic"));
		
		if(advancedMode) {
			// store size box
			//HTMLNode storeSizeInfobox = nextTableCell.addChild(new div(HTMLClass.INFOBOX));
			InfoboxWidget storeSizeInfobox = new InfoboxWidget(InfoboxWidget.Type.NONE, null);
			contentNode.addChild(storeSizeInfobox);
			drawStoreSizeBox(storeSizeInfobox, myLocation, nodeUptimeSeconds);
           
			if(numberOfConnected + numberOfRoutingBackedOff > 0) {
				
				InfoboxWidget loadStatsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
				drawLoadBalancingBox(loadStatsInfobox, false);
				
				loadStatsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
				drawLoadBalancingBox(loadStatsInfobox, true);
				
				InfoboxWidget newLoadManagementBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
				drawNewLoadManagementBox(newLoadManagementBox);

				// Psuccess box
				InfoboxWidget successRateBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, l10n("successRate"));
				stats.fillSuccessRateBox(successRateBox.body);
				
				InfoboxWidget timeDetailBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, l10n("chkDetailTiming"));
				stats.fillDetailedTimingsBox(timeDetailBox.body);
				
				InfoboxWidget byHTLBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, l10n("successByHTLBulk"));
				stats.fillRemoteRequestHTLsBox(byHTLBox.body, false);
				
				byHTLBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, l10n("successByHTLRT"));
				stats.fillRemoteRequestHTLsBox(byHTLBox.body, true);
			}
		}

		if(advancedMode || numberOfConnected + numberOfRoutingBackedOff > 0) {

			// Activity box
			nextTableCell = overviewTableRow.addCell(Category.LAST);
			InfoboxWidget activityInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawActivityBox(activityInfobox, advancedMode);

			/* node status overview box */
			if(advancedMode) {
				InfoboxWidget overviewInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
				drawOverviewBox(overviewInfobox, nodeUptimeSeconds, node.clientCore.bandwidthStatsPutter.getLatestUptimeData().totalUptime, now, swaps, noSwaps);
			}

			// Peer statistics box
			InfoboxWidget peerStatsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawPeerStatsBox(peerStatsInfobox, advancedMode, numberOfConnected, numberOfRoutingBackedOff,
					numberOfTooNew, numberOfTooOld, numberOfDisconnected, numberOfNeverConnected, numberOfDisabled, 
					numberOfBursting, numberOfListening, numberOfListenOnly, numberOfSeedServers, numberOfSeedClients,
					numberOfRoutingDisabled, numberOfClockProblem, numberOfConnError, numberOfDisconnecting, numberOfNoLoadStats, node);

			// Bandwidth box
			InfoboxWidget bandwidthInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawBandwidthBox(bandwidthInfobox, nodeUptimeSeconds, advancedMode);
		}

		if (advancedMode) {
			// Peer routing backoff reason box
			InfoboxWidget backoffReasonInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Peer Backoff");
			InfoboxWidget curBackoffReasonInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Current backoff reasons (bulk)");
			backoffReasonInfobox.body.addChild(curBackoffReasonInfobox);

			String [] routingBackoffReasons = peers.getPeerNodeRoutingBackoffReasons(false);
			if(routingBackoffReasons.length == 0) {
				curBackoffReasonInfobox.body.addText(l10n("notBackedOff"));
			} else {
				OutputList reasonList = new OutputList();
				curBackoffReasonInfobox.body.addChild(reasonList);
				for(int i=0;i<routingBackoffReasons.length;i++) {
					int reasonCount = peers.getPeerNodeRoutingBackoffReasonSize(routingBackoffReasons[i], false);
					if(reasonCount > 0) {
						reasonList.addItem(routingBackoffReasons[i] + '\u00a0' + reasonCount);
					}
				}
			}
			curBackoffReasonInfobox = new InfoboxWidget(InfoboxWidget.Type.NONE, "Current backoff reasons (realtime)");
			backoffReasonInfobox.body.addChild(curBackoffReasonInfobox);
			routingBackoffReasons = peers.getPeerNodeRoutingBackoffReasons(true);
			if(routingBackoffReasons.length == 0) {
				curBackoffReasonInfobox.body.addText(l10n("notBackedOff"));
			} else {
				OutputList reasonList = new OutputList();
				curBackoffReasonInfobox.body.addChild(reasonList);
				for(int i=0;i<routingBackoffReasons.length;i++) {
					int reasonCount = peers.getPeerNodeRoutingBackoffReasonSize(routingBackoffReasons[i], true);
					if(reasonCount > 0) {
						reasonList.addItem(routingBackoffReasons[i] + '\u00a0' + reasonCount);
					}
				}
			}

			// Per backoff-type count and avg backoff lengths

			// Mandatory backoff - bulk
			Table mandatoryBackoffStatisticsTableBulk = backoffReasonInfobox.addTable(Category.NOBORDER);
			Row row = mandatoryBackoffStatisticsTableBulk.addRow();
			row.addHeader(l10n("mandatoryBackoffReason") + " (bulk)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for (NodeStats.TimedStats entry : stats.getMandatoryBackoffStatistics(false)) {
				row = mandatoryBackoffStatisticsTableBulk.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}

			// Mandatory backoff - realtime
			Table mandatoryBackoffStatisticsTableRT = backoffReasonInfobox.addTable(Category.NOBORDER);
			row = mandatoryBackoffStatisticsTableRT.addRow();
			row.addHeader(l10n("mandatoryBackoffReason") + " (realtime)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for(NodeStats.TimedStats entry : stats.getMandatoryBackoffStatistics(true)) {
				row = mandatoryBackoffStatisticsTableRT.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}

			// Routing Backoff bulk
			Table routingBackoffStatisticsTableBulk = backoffReasonInfobox.addTable(Category.NOBORDER);
			row = routingBackoffStatisticsTableBulk.addRow();
			row.addHeader(l10n("routingBackoffReason") + " (bulk)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for(NodeStats.TimedStats entry : stats.getRoutingBackoffStatistics(false)) {
				row = routingBackoffStatisticsTableBulk.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}

			// Routing Backoff realtime
			Table routingBackoffStatisticsTableRT = backoffReasonInfobox.addTable(Category.NOBORDER);
			row = routingBackoffStatisticsTableRT.addRow();
			row.addHeader(l10n("routingBackoffReason") + " (realtime)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for(NodeStats.TimedStats entry : stats.getRoutingBackoffStatistics(true)) {
				row = routingBackoffStatisticsTableRT.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}

			// Transfer Backoff bulk
			Table transferBackoffStatisticsTableBulk = backoffReasonInfobox.addTable(Category.NOBORDER);
			row = transferBackoffStatisticsTableBulk.addRow();
			row.addHeader(l10n("transferBackoffReason") + " (bulk)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for(NodeStats.TimedStats entry : stats.getTransferBackoffStatistics(false)) {
				row = transferBackoffStatisticsTableBulk.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}
			// Transfer Backoff realtime
			Table transferBackoffStatisticsTableRT = backoffReasonInfobox.addTable(Category.NOBORDER);
			row = transferBackoffStatisticsTableRT.addRow();
			row.addHeader(l10n("transferBackoffReason") + " (realtime)");
			row.addHeader(l10n("count"));
			row.addHeader(l10n("avgTime"));
			row.addHeader(l10n("totalTime"));

			for(NodeStats.TimedStats entry : stats.getTransferBackoffStatistics(true)) {
				row = transferBackoffStatisticsTableRT.addRow();
				row.addCell(entry.keyStr);
				row.addCell(Long.toString(entry.count));
				row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
				row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
			}

			//Swap statistics box
			InfoboxWidget locationSwapInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawSwapStatsBox(locationSwapInfobox, myLocation, nodeUptimeSeconds, swaps, noSwaps);

			// unclaimedFIFOMessageCounts box
			InfoboxWidget unclaimedFIFOMessageCountsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawUnclaimedFIFOMessageCountsBox(unclaimedFIFOMessageCountsInfobox);

			InfoboxWidget threadsPriorityInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawThreadPriorityStatsBox(threadsPriorityInfobox);
			
			nextTableCell = overviewTableRow.addCell();

			// thread usage box
			InfoboxWidget threadUsageInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Thread usage");
			OutputList threadUsageList = new OutputList();
			threadUsageInfobox.body.addChild(threadUsageList);
			getThreadNames(threadUsageList);
			
			// rejection reasons box
			drawRejectReasonsBox(nextTableCell, false);
			drawRejectReasonsBox(nextTableCell, true);
			
			// database thread jobs box
			
			InfoboxWidget databaseJobsInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
			drawDatabaseJobsBox(databaseJobsInfobox);

			OpennetManager om = node.getOpennet();
			if(om != null) {
				// opennet stats box
				InfoboxWidget OpennetStatsBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
				drawOpennetStatsBox(OpennetStatsBox, om);
				
				if(node.isSeednode()) {
					InfoboxWidget SeedStatsBox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, null);
					drawSeedStatsBox(SeedStatsBox, om);
				}
			}

			// peer distribution box
			overviewTableRow = overviewTable.addRow();
			nextTableCell = overviewTableRow.addCell(Category.FIRST);
			InfoboxWidget peerCircleInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Peer\u00a0Location\u00a0Distribution (w/pReject)");
			Table peerCircleTable = peerCircleInfobox.body.addTable();
			addPeerCircle(peerCircleTable, peerNodeStatuses, myLocation);
			nextTableCell = overviewTableRow.addCell();

			// node distribution box
			InfoboxWidget nodeCircleInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Node\u00a0Location\u00a0Distribution (w/Swap\u00a0Age)");
			Table nodeCircleTable = nodeCircleInfobox.body.addTable();
			addNodeCircle(nodeCircleTable, myLocation);
			overviewTableRow = overviewTable.addRow();
			nextTableCell = overviewTableRow.addCell(Category.FIRST);
			// specialisation box
			int[] incomingRequestCountArray = new int[1];
			int[] incomingRequestLocation = stats.getIncomingRequestLocation(incomingRequestCountArray);
			int incomingRequestsCount = incomingRequestCountArray[0];
			
			if(incomingRequestsCount > 0) {
				InfoboxWidget nodeSpecialisationInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Incoming\u00a0Request\u00a0Distribution");
				Table nodeSpecialisationTable = nodeSpecialisationInfobox.body.addTable();
				addSpecialisation(nodeSpecialisationTable, myLocation, incomingRequestsCount, incomingRequestLocation);
			}
			
			nextTableCell = overviewTableRow.addCell();
			int[] outgoingLocalRequestCountArray = new int[1];
			int[] outgoingLocalRequestLocation = stats.getOutgoingLocalRequestLocation(outgoingLocalRequestCountArray);
			int outgoingLocalRequestsCount = outgoingLocalRequestCountArray[0];
			int[] outgoingRequestCountArray = new int[1];
			int[] outgoingRequestLocation = stats.getOutgoingRequestLocation(outgoingRequestCountArray);
			int outgoingRequestsCount = outgoingRequestCountArray[0];
			
			if(outgoingLocalRequestsCount > 0 && outgoingRequestsCount > 0) {
				InfoboxWidget nodeSpecialisationInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Outgoing\u00a0Request\u00a0Distribution");
				Table nodeSpecialisationTable = nodeSpecialisationInfobox.body.addTable();
				addCombinedSpecialisation(nodeSpecialisationTable, myLocation, outgoingLocalRequestsCount, outgoingLocalRequestLocation, outgoingRequestsCount, outgoingRequestLocation);
			}

			overviewTableRow = overviewTable.addRow();
			nextTableCell = overviewTableRow.addCell(Category.FIRST);

			// success rate per location
			int[] locationSuccessRatesArray = stats.chkSuccessRatesByLocation.getPercentageArray(1000);

			{
				InfoboxWidget nodeSpecialisationInfobox = nextTableCell.addInfobox(InfoboxWidget.Type.NONE, "Local\u00a0CHK\u00a0Success\u00a0Rates\u00a0By\u00a0Location");
				Table nodeSpecialisationTable = nodeSpecialisationInfobox.body.addTable();
				addSpecialisation(nodeSpecialisationTable, myLocation, 1000, locationSuccessRatesArray);
			}
		}
		}

		this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	private void showRequesters(HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		PageNode page = ctx.getPageMaker().getPageNode(l10n("fullTitle"), ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;

		drawClientRequestersBox(contentNode);
		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	private void drawLoadBalancingBox(InfoboxWidget loadStatsInfobox, boolean realTime) {
		// Load balancing box
		// Include overall window, and RTTs for each
		
		loadStatsInfobox.setTitle("Load limiting "+(realTime ? "RealTime" : "Bulk"));
		RequestStarterGroup starters = core.requestStarters;
		double window = starters.getWindow(realTime);
		double realWindow = starters.getRealWindow(realTime);
		OutputList loadStatsList = new OutputList();
		loadStatsInfobox.body.addChild(loadStatsList);
		loadStatsList.addItem(l10n("globalWindow") + ": " + window);
		loadStatsList.addItem(l10n("realGlobalWindow") + ": " + realWindow);
		loadStatsList.addItem(starters.statsPageLine(false, false, realTime));
		loadStatsList.addItem(starters.statsPageLine(true, false, realTime));
		loadStatsList.addItem(starters.statsPageLine(false, true, realTime));
		loadStatsList.addItem(starters.statsPageLine(true, true, realTime));
		loadStatsList.addItem(starters.diagnosticThrottlesLine(false));
		loadStatsList.addItem(starters.diagnosticThrottlesLine(true));
	}
	
	private void drawNewLoadManagementBox(InfoboxWidget infobox) {
		infobox.setTitle(l10n("newLoadManagementTitle"));
		node.nodeStats.drawNewLoadManagementDelayTimes(infobox.body);
	}

	private void drawRejectReasonsBox(Cell nextTableCell, boolean local) {
		Table rejectReasonsTable = new Table();
		NodeStats stats = node.nodeStats;
		boolean success = local ? stats.getLocalRejectReasonsTable(rejectReasonsTable) :
			stats.getRejectReasonsTable(rejectReasonsTable);
		if (!success) {
			return;
		} else {
			nextTableCell.addInfobox(InfoboxWidget.Type.NONE, (local ? "Local " : "") +
				"Preemptive Rejection Reasons").body.addChild(rejectReasonsTable);
		}
	}

	private void drawNodeVersionBox(InfoboxWidget versionInfobox) {
		
		versionInfobox.setTitle(l10n("versionTitle"));
		OutputList versionInfoboxList = new OutputList();
		versionInfobox.body.addChild(versionInfoboxList);
		versionInfoboxList.addItem(NodeL10n.getBase().getString("WelcomeToadlet.version", new String[]{"fullVersion", "build", "rev"},
			new String[]{Version.publicVersion(), Integer.toString(Version.buildNumber()), Version.cvsRevision()}));
		if(NodeStarter.extBuildNumber < NodeStarter.RECOMMENDED_EXT_BUILD_NUMBER)
			versionInfoboxList.addItem(NodeL10n.getBase().getString("WelcomeToadlet.extVersionWithRecommended",
				new String[]{"build", "recbuild", "rev"},
				new String[]{Integer.toString(NodeStarter.extBuildNumber), Integer.toString(NodeStarter.RECOMMENDED_EXT_BUILD_NUMBER), NodeStarter.extRevisionNumber}));
		else
			versionInfoboxList.addItem(NodeL10n.getBase().getString("WelcomeToadlet.extVersion", new String[]{"build", "rev"},
				new String[]{Integer.toString(NodeStarter.extBuildNumber), NodeStarter.extRevisionNumber}));
		
	}

	private void drawJVMStatsBox(InfoboxWidget jvmStatsInfobox, boolean advancedModeEnabled) {
		
		jvmStatsInfobox.setTitle(l10n("jvmInfoTitle"));
		OutputList jvmStatsList = new OutputList();
		jvmStatsInfobox.body.addChild(jvmStatsList);

		Runtime rt = Runtime.getRuntime();
		long freeMemory = rt.freeMemory();
		long totalMemory = rt.totalMemory();
		long maxMemory = rt.maxMemory();

		long usedJavaMem = totalMemory - freeMemory;
		long allocatedJavaMem = totalMemory;
		long maxJavaMem = maxMemory;
		int availableCpus = rt.availableProcessors();

		int threadCount = stats.getActiveThreadCount();

		jvmStatsList.addItem(l10n("usedMemory", "memory", SizeUtil.formatSize(usedJavaMem, true)));
		jvmStatsList.addItem(l10n("allocMemory", "memory", SizeUtil.formatSize(allocatedJavaMem, true)));
		jvmStatsList.addItem(l10n("maxMemory", "memory", SizeUtil.formatSize(maxJavaMem, true)));
		jvmStatsList.addItem(l10n("threads", new String[]{"running", "max"},
			new String[]{thousandPoint.format(threadCount), Integer.toString(stats.getThreadLimit())}));
		jvmStatsList.addItem(l10n("cpus", "count", Integer.toString(availableCpus)));
		jvmStatsList.addItem(l10n("javaVersion", "version", System.getProperty("java.version")));
		jvmStatsList.addItem(l10n("jvmVendor", "vendor", System.getProperty("java.vendor")));
		jvmStatsList.addItem(l10n("jvmName", "name", System.getProperty("java.vm.name")));
		jvmStatsList.addItem(l10n("jvmVersion", "version", System.getProperty("java.vm.version")));
		jvmStatsList.addItem(l10n("osName", "name", System.getProperty("os.name")));
		jvmStatsList.addItem(l10n("osVersion", "version", System.getProperty("os.version")));
		jvmStatsList.addItem(l10n("osArch", "arch", System.getProperty("os.arch")));
		if(advancedModeEnabled) {
			if(Rijndael.isJCACrippled)
				jvmStatsList.addItem(l10n("cryptoUsingBuiltin"));
			else
				jvmStatsList.addItem(l10n("cryptoUsingJCA", "provider", Rijndael.getProviderName()));
		}
	}
	
	private void drawThreadPriorityStatsBox(InfoboxWidget node) {
		
		node.setTitle(l10n("threadsByPriority"));
		int[] activeThreadsByPriority = stats.getActiveThreadsByPriority();
		int[] waitingThreadsByPriority = stats.getWaitingThreadsByPriority();
		
		Table threadsByPriorityTable = node.body.addTable(Category.NOBORDER);
		Row row = threadsByPriorityTable.addRow();

		row.addHeader(l10n("priority"));
		row.addHeader(l10n("running"));
		row.addHeader(l10n("waiting"));
		
		for(int i=0; i<activeThreadsByPriority.length; i++) {
			row = threadsByPriorityTable.addRow();
			row.addCell(String.valueOf(i + 1));
			row.addCell(String.valueOf(activeThreadsByPriority[i]));
			row.addCell(String.valueOf(waitingThreadsByPriority[i]));
		}
	}

	private void drawDatabaseJobsBox(InfoboxWidget node) {
		// Job count by priority
		node.setTitle(l10n("databaseJobsByPriority"));
		int[] jobsByPriority = core.clientDatabaseExecutor.getQueuedJobsCountByPriority();

		Table threadsByPriorityTable = node.body.addTable(Category.NOBORDER);
		Row row = threadsByPriorityTable.addRow();

		row.addHeader(l10n("priority"));
		row.addHeader(l10n("waiting"));
		
		for(int i=0; i<jobsByPriority.length; i++) {
			row = threadsByPriorityTable.addRow();
			row.addCell(String.valueOf(i));
			row.addCell(String.valueOf(jobsByPriority[i]));
		}

		// Per job-type execution count and avg execution time

		Table executionTimeStatisticsTable = node.body.addTable(Category.NOBORDER);
		row = executionTimeStatisticsTable.addRow();
		row.addHeader(l10n("jobType"));
		row.addHeader(l10n("count"));
		row.addHeader(l10n("avgTime"));
		row.addHeader(l10n("totalTime"));
		
		
		for(NodeStats.TimedStats entry : stats.getDatabaseJobExecutionStatistics()) {
			row = executionTimeStatisticsTable.addRow();
			row.addCell(entry.keyStr);
			row.addCell(Long.toString(entry.count));
			row.addCell(TimeUtil.formatTime(entry.avgTime, 2, true));
			row.addCell(TimeUtil.formatTime(entry.totalTime, 2, true));
		}

		Table jobQueueStatistics = node.body.addTable(Category.NOBORDER);
		row = jobQueueStatistics.addRow();
		row.addHeader(l10n("queuedCount"));
		row.addHeader(l10n("jobType"));
		stats.getDatabaseJobQueueStatistics().toTableRows(jobQueueStatistics);
	}

	private void drawOpennetStatsBox(InfoboxWidget box, OpennetManager om) {
		box.setTitle(l10n("opennetStats"));
		om.drawOpennetStatsBox(box.body);
	}
	
	private void drawSeedStatsBox(InfoboxWidget box, OpennetManager om) {
		box.setTitle(l10n("seedStats"));
		om.drawSeedStatsBox(box.body);
	}

	private void drawClientRequestersBox(HTMLNode box) {
		box.addChild(new Box(Category.INFOBOXHEADER, l10n("clientRequesterObjects")));
		Box masterContent = new Box(Category.INFOBOXCONTENT);
		box.addChild(masterContent);
		Table table = masterContent.addTable();
		Row row = table.addRow();
		row.addHeader("RequestClient");
		row.addHeader(l10n("clientRequesters.class"));
		row.addHeader(l10n("clientRequesters.age"));
		row.addHeader(l10n("clientRequesters.priorityClass"));
		row.addHeader(l10n("clientRequesters.realtimeFlag"));
		row.addHeader(l10n("clientRequesters.uri"));
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(0);
		nf.setMinimumIntegerDigits(2);
		ClientRequester[] requests = ClientRequester.getAll();
		Arrays.sort(requests, new Comparator<ClientRequester>() {
				@Override
				public int compare(ClientRequester a, ClientRequester b) {
					return -Long.signum(a.creationTime - b.creationTime);
				}
			});
		long now = System.currentTimeMillis();
		for(ClientRequester request : requests) {
			if(request.isFinished() || request.isCancelled())
				continue;
			row = table.addRow();
			RequestClient client = request.getClient();
			// client can be null if the request is stored in DB4O and then deactivated
			row.addCell(client == null ? "null" : client.toString());
			try {
				String s = request.toString();
				if(s.indexOf(':') > s.indexOf('@')) {
					s = s.substring(0, s.indexOf(':'));
				}
				row.addCell(s);
			} catch (Throwable t) {
				// FIXME shouldn't happen...
				row.addCell("ERROR: " + request.getClass().toString());
			}
			long diff = now - request.creationTime;
			StringBuilder sb = new StringBuilder();
			sb.append(TimeUtil.formatTime(diff, 2));
			row.addCell(sb.toString());
			row.addCell(Short.toString(request.getPriorityClass()));
			row.addCell(client == null ? "?" : Boolean.toString(client.realTimeFlag()));
			FreenetURI uri = request.getURI(); // getURI() sometimes returns null, eg for ClientPutters
			row.addCell(uri == null ? "null" : uri.toString());
		}
	}

	private void drawStoreSizeBox(InfoboxWidget storeSizeInfobox, double loc, long nodeUptimeSeconds) {
		storeSizeInfobox.setTitle(l10n("datastore"));
		Box scrollBox = new Box();
		scrollBox.addAttribute("style", "overflow:scr");
		storeSizeInfobox.body.addChild(scrollBox);

		Table storeSizeTable = scrollBox.addTable(Category.NOBORDER);
		Row row = storeSizeTable.addRow();

		//FIXME - Non-breaking space? "Stat-name"?
		row.addHeader("");
		row.addHeader(l10n("keys"));
		row.addHeader(l10n("capacity"));
		row.addHeader(l10n("datasize"));
		row.addHeader(l10n("utilization"));
		row.addHeader(l10n("readRequests"));
		row.addHeader(l10n("successfulReads"));
		row.addHeader(l10n("successRate"));
		row.addHeader(l10n("writes"));
		row.addHeader(l10n("accessRate"));
		row.addHeader(l10n("writeRate"));
		row.addHeader(l10n("falsePos"));
		row.addHeader(l10n("avgLocation"));
		row.addHeader(l10n("avgSuccessLoc"));
		row.addHeader(l10n("furthestSuccess"));
		row.addHeader(l10n("avgDist"));
		row.addHeader(l10n("distanceStats"));


		Map<DataStoreInstanceType, DataStoreStats> storeStats = node.getDataStoreStats();
		for (Map.Entry<DataStoreInstanceType, DataStoreStats> entry : storeStats.entrySet()) {
			DataStoreInstanceType instance = entry.getKey();
			DataStoreStats stats = entry.getValue();
			
			StoreAccessStats sessionAccess = stats.getSessionAccessStats();
			StoreAccessStats totalAccess;
			long totalUptimeSeconds = 0;
			try {
				totalAccess = stats.getTotalAccessStats();
				// FIXME this is not necessarily the same as the datastore's uptime if we've switched.
				// Ideally we'd track uptime there too.
				totalUptimeSeconds = 
					node.clientCore.bandwidthStatsPutter.getLatestUptimeData().totalUptime;
			} catch (StatsNotAvailableException e) {
				totalAccess = null;
			}
			
			row = storeSizeTable.addRow();
			row.addHeader(l10n(instance.store.name()) + "\n" + " (" + l10n(instance.key.name()) + ")");

			row.addCell(thousandPoint.format(stats.keys()));
			row.addCell(thousandPoint.format(stats.capacity()));
			row.addCell(SizeUtil.formatSize(stats.dataSize()));
			row.addCell(fix3p1pct.format(stats.utilization()));
			row.addCell(thousandPoint.format(sessionAccess.readRequests()) +
					(totalAccess == null ? "" : (" ("+thousandPoint.format(totalAccess.readRequests())+")")));
			row.addCell(thousandPoint.format(sessionAccess.successfulReads()) +
					(totalAccess == null ? "" : (" ("+thousandPoint.format(totalAccess.successfulReads())+")")));
			try {
				String rate = fix1p4.format(sessionAccess.successRate()) + "%";
				if(totalAccess != null) {
					try {
						rate += " (" + fix1p4.format(totalAccess.successRate()) + "%)";
					} catch (StatsNotAvailableException e) {
						// Ignore
					}
				}
				row.addCell(rate);
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}
			row.addCell(thousandPoint.format(sessionAccess.writes()) +
					(totalAccess == null ? "" : (" ("+thousandPoint.format(totalAccess.writes())+")")));
			String access = fix1p2.format(sessionAccess.accessRate(nodeUptimeSeconds)) + " /s";
			if(totalAccess != null)
				access += " (" + fix1p2.format(totalAccess.accessRate(totalUptimeSeconds)) + " /s)";
			row.addCell(access);
			access = fix1p2.format(sessionAccess.writeRate(nodeUptimeSeconds)) + " /s";
			if(totalAccess != null)
				access += " (" + fix1p2.format(totalAccess.writeRate(totalUptimeSeconds)) + " /s)";
			row.addCell(access);
			row.addCell(thousandPoint.format(sessionAccess.falsePos()) +
					(totalAccess == null ? "" : (" ("+thousandPoint.format(totalAccess.falsePos())+")")));
			try {
				row.addCell(fix1p4.format(stats.avgLocation()));
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}

			try {
				row.addCell(fix1p4.format(stats.avgSuccess()));
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}

			try {
				row.addCell(fix1p4.format(stats.furthestSuccess()));
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}

			try {
				row.addCell(fix1p4.format(stats.avgDist()));
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}

			try {
				row.addCell(fix3p1pct.format(stats.distanceStats()));
			} catch (StatsNotAvailableException e) {
				row.addCell("N/A");
			}
		}

	}

	private void drawUnclaimedFIFOMessageCountsBox(InfoboxWidget unclaimedFIFOMessageCountsInfobox) {
		
		unclaimedFIFOMessageCountsInfobox.setTitle("unclaimedFIFO Message Counts");
		OutputList unclaimedFIFOMessageCountsList = new OutputList();
		unclaimedFIFOMessageCountsInfobox.body.addChild(unclaimedFIFOMessageCountsList);
		Map<String, Integer> unclaimedFIFOMessageCountsMap = node.getUSM().getUnclaimedFIFOMessageCounts();
		STMessageCount[] unclaimedFIFOMessageCountsArray = new STMessageCount[unclaimedFIFOMessageCountsMap.size()];
		int i = 0;
		int totalCount = 0;
		for (Map.Entry<String, Integer> e : unclaimedFIFOMessageCountsMap.entrySet()) {
			String messageName = e.getKey();
			int messageCount = e.getValue();
			totalCount = totalCount + messageCount;
			unclaimedFIFOMessageCountsArray[i++] = new STMessageCount( messageName, messageCount );
		}
		Arrays.sort(unclaimedFIFOMessageCountsArray, new Comparator<STMessageCount>() {
			@Override
			public int compare(STMessageCount firstCount, STMessageCount secondCount) {
				return secondCount.messageCount - firstCount.messageCount;  // sort in descending order
			}
		});
		for (int countsArrayIndex = 0, countsArrayCount = unclaimedFIFOMessageCountsArray.length; countsArrayIndex < countsArrayCount; countsArrayIndex++) {
			STMessageCount messageCountItem = unclaimedFIFOMessageCountsArray[countsArrayIndex];
			int thisMessageCount = messageCountItem.messageCount;
			double thisMessagePercentOfTotal = ((double) thisMessageCount) / ((double) totalCount);
			unclaimedFIFOMessageCountsList.addItem("" + messageCountItem.messageName + ":\u00a0" + thisMessageCount + "\u00a0(" + fix3p1pct.format(thisMessagePercentOfTotal) + ')');
		}
		unclaimedFIFOMessageCountsList.addItem("Unclaimed Messages Considered:\u00a0" + totalCount);
		
	}

	private void drawSwapStatsBox(InfoboxWidget locationSwapInfobox, double location, long nodeUptimeSeconds, double swaps, double noSwaps) {
		
		locationSwapInfobox.setTitle("Location swaps");
		int startedSwaps = node.getStartedSwaps();
		int swapsRejectedAlreadyLocked = node.getSwapsRejectedAlreadyLocked();
		int swapsRejectedNowhereToGo = node.getSwapsRejectedNowhereToGo();
		int swapsRejectedRateLimit = node.getSwapsRejectedRateLimit();
		int swapsRejectedRecognizedID = node.getSwapsRejectedRecognizedID();
		double locChangeSession = node.getLocationChangeSession();
		int averageSwapTime = node.getAverageOutgoingSwapTime();
		int sendSwapInterval = node.getSendSwapInterval();

		OutputList locationSwapList = new OutputList();
		locationSwapInfobox.body.addChild(locationSwapList);
		locationSwapList.addItem("location:\u00a0" + location);
		if (swaps > 0.0) {
			locationSwapList.addItem("locChangeSession:\u00a0" + fix1p6sci.format(locChangeSession));
			locationSwapList.addItem("locChangePerSwap:\u00a0" + fix1p6sci.format(locChangeSession / swaps));
		}
		if ((swaps > 0.0) && (nodeUptimeSeconds >= 60)) {
			locationSwapList.addItem("locChangePerMinute:\u00a0" + fix1p6sci.format(locChangeSession / (nodeUptimeSeconds / 60.0)));
		}
		if ((swaps > 0.0) && (nodeUptimeSeconds >= 60)) {
			locationSwapList.addItem("swapsPerMinute:\u00a0" + fix1p6sci.format(swaps / (nodeUptimeSeconds / 60.0)));
		}
		if ((noSwaps > 0.0) && (nodeUptimeSeconds >= 60)) {
			locationSwapList.addItem("noSwapsPerMinute:\u00a0" + fix1p6sci.format(noSwaps / (nodeUptimeSeconds / 60.0)));
		}
		if ((swaps > 0.0) && (noSwaps > 0.0)) {
			locationSwapList.addItem("swapsPerNoSwaps:\u00a0" + fix1p6sci.format(swaps / noSwaps));
		}
		if (swaps > 0.0) {
			locationSwapList.addItem("swaps:\u00a0" + (int) swaps);
		}
		if (noSwaps > 0.0) {
			locationSwapList.addItem("noSwaps:\u00a0" + (int) noSwaps);
		}
		if (startedSwaps > 0) {
			locationSwapList.addItem("startedSwaps:\u00a0" + startedSwaps);
		}
		if (swapsRejectedAlreadyLocked > 0) {
			locationSwapList.addItem("swapsRejectedAlreadyLocked:\u00a0" + swapsRejectedAlreadyLocked);
		}
		if (swapsRejectedNowhereToGo > 0) {
			locationSwapList.addItem("swapsRejectedNowhereToGo:\u00a0" + swapsRejectedNowhereToGo);
		}
		if (swapsRejectedRateLimit > 0) {
			locationSwapList.addItem("swapsRejectedRateLimit:\u00a0" + swapsRejectedRateLimit);
		}
		if (swapsRejectedRecognizedID > 0) {
			locationSwapList.addItem("swapsRejectedRecognizedID:\u00a0" + swapsRejectedRecognizedID);
		}
		locationSwapList.addItem("averageSwapTime:\u00a0" + TimeUtil.formatTime(averageSwapTime, 2, true));
		locationSwapList.addItem("sendSwapInterval:\u00a0" + TimeUtil.formatTime(sendSwapInterval, 2, true));
	}

	protected static void drawPeerStatsBox(InfoboxWidget peerStatsInfobox, boolean advancedModeEnabled, int numberOfConnected,
			int numberOfRoutingBackedOff, int numberOfTooNew, int numberOfTooOld, int numberOfDisconnected, 
			int numberOfNeverConnected, int numberOfDisabled, int numberOfBursting, int numberOfListening, 
			int numberOfListenOnly, int numberOfSeedServers, int numberOfSeedClients, int numberOfRoutingDisabled, 
			int numberOfClockProblem, int numberOfConnError, int numberOfDisconnecting, int numberOfNoLoadStats, Node node) {
		
		peerStatsInfobox.setTitle(l10n("peerStatsTitle"));
		OutputList peerStatsList = new OutputList();
		peerStatsInfobox.body.addChild(peerStatsList);
		if (numberOfConnected > 0) {
			InlineBox peerStatsConnectedListItem = peerStatsList.addItem().addInlineBox();
			peerStatsConnectedListItem.addInlineBox(Category.PEERCONNECTED, l10nDark("connected"), l10nDark("connectedShort"));
			peerStatsConnectedListItem.addInlineBox(":\u00a0" + numberOfConnected);
		}
		if (numberOfRoutingBackedOff > 0) {
			InlineBox peerStatsRoutingBackedOffListItem = peerStatsList.addItem().addInlineBox();
			peerStatsRoutingBackedOffListItem.addInlineBox(Category.PEERBACKEDOFF, l10nDark(advancedModeEnabled ? "backedOff" : "busy"), l10nDark((advancedModeEnabled ? "backedOff" : "busy")+"Short"));
			peerStatsRoutingBackedOffListItem.addInlineBox(":\u00a0" + numberOfRoutingBackedOff);
		}
		if (numberOfTooNew > 0) {
			InlineBox peerStatsTooNewListItem = peerStatsList.addItem().addInlineBox();
			peerStatsTooNewListItem.addInlineBox(Category.PEERTOONEW, l10nDark("tooNew"), l10nDark("tooNewShort"));
			peerStatsTooNewListItem.addInlineBox(":\u00a0" + numberOfTooNew);
		}
		if (numberOfTooOld > 0) {
			InlineBox peerStatsTooOldListItem = peerStatsList.addItem().addInlineBox();
			peerStatsTooOldListItem.addInlineBox(Category.PEERTOOOLD, l10nDark("tooOld"), l10nDark("tooOldShort"));
			peerStatsTooOldListItem.addInlineBox(":\u00a0" + numberOfTooOld);
		}
		if (numberOfDisconnected > 0) {
			InlineBox peerStatsDisconnectedListItem = peerStatsList.addItem().addInlineBox();
			peerStatsDisconnectedListItem.addInlineBox(Category.PEERDISCONNECTED, l10nDark("notConnected"), l10nDark("notConnectedShort"));
			peerStatsDisconnectedListItem.addInlineBox(":\u00a0" + numberOfDisconnected);
		}
		if (numberOfNeverConnected > 0) {
			InlineBox peerStatsNeverConnectedListItem = peerStatsList.addItem().addInlineBox();
			peerStatsNeverConnectedListItem.addInlineBox(Category.PEERNEVERCONNECTED, l10nDark("neverConnected"), l10nDark("neverConnectedShort"));
			peerStatsNeverConnectedListItem.addInlineBox(":\u00a0" + numberOfNeverConnected);
		}
		if (numberOfDisabled > 0) {
			InlineBox peerStatsDisabledListItem = peerStatsList.addItem().addInlineBox();
			peerStatsDisabledListItem.addInlineBox(Category.PEERDISABLED, l10nDark("disabled"), l10nDark("disabledShort"));
			peerStatsDisabledListItem.addInlineBox(":\u00a0" + numberOfDisabled);
		}
		if (numberOfBursting > 0) {
			InlineBox peerStatsBurstingListItem = peerStatsList.addItem().addInlineBox();
			peerStatsBurstingListItem.addInlineBox(Category.PEERBURSTING, l10nDark("bursting"), l10nDark("burstingShort"));
			peerStatsBurstingListItem.addInlineBox(":\u00a0" + numberOfBursting);
		}
		if (numberOfListening > 0) {
			InlineBox peerStatsListeningListItem = peerStatsList.addItem().addInlineBox();
			peerStatsListeningListItem.addInlineBox(Category.PEERLISTENING, l10nDark("listening"), l10nDark("listeningShort"));
			peerStatsListeningListItem.addInlineBox(":\u00a0" + numberOfListening);
		}
		if (numberOfListenOnly > 0) {
			InlineBox peerStatsListenOnlyListItem = peerStatsList.addItem().addInlineBox();
			peerStatsListenOnlyListItem.addInlineBox(Category.PEERLISTENONLY, l10nDark("listenOnly"), l10nDark("listenOnlyShort"));
			peerStatsListenOnlyListItem.addInlineBox(":\u00a0" + numberOfListenOnly);
		}
		if (numberOfClockProblem > 0) {
			InlineBox peerStatsRoutingDisabledListItem = peerStatsList.addItem().addInlineBox();
			peerStatsRoutingDisabledListItem.addInlineBox(Category.PEERCLOCKPROBLEM, l10nDark("clockProblem"), l10nDark("clockProblemShort"));
			peerStatsRoutingDisabledListItem.addInlineBox(":\u00a0" + numberOfClockProblem);
		}
		if (numberOfConnError > 0) {
			InlineBox peerStatsRoutingDisabledListItem = peerStatsList.addItem().addInlineBox();
			peerStatsRoutingDisabledListItem.addInlineBox(Category.PEERROUTINGDISABLED, l10nDark("connError"), l10nDark("connErrorShort"));
			peerStatsRoutingDisabledListItem.addInlineBox(":\u00a0" + numberOfClockProblem);
		}
		if (numberOfDisconnecting > 0) {
			InlineBox peerStatsListenOnlyListItem = peerStatsList.addItem().addInlineBox();
			peerStatsListenOnlyListItem.addInlineBox(Category.PEERDISCONNECTING, l10nDark("disconnecting"), l10nDark("disconnectingShort"));
			peerStatsListenOnlyListItem.addInlineBox(":\u00a0" + numberOfDisconnecting);
		}
		if (numberOfSeedServers > 0) {
			InlineBox peerStatsSeedServersListItem = peerStatsList.addItem().addInlineBox();
			peerStatsSeedServersListItem.addInlineBox(Category.SEEDSERVER, l10nDark("seedServers"), l10nDark("seedServersShort"));
			peerStatsSeedServersListItem.addInlineBox(":\u00a0" + numberOfSeedServers);
		}
		if (numberOfSeedClients > 0) {
			InlineBox peerStatsSeedClientsListItem = peerStatsList.addItem().addInlineBox();
			peerStatsSeedClientsListItem.addInlineBox(Category.SEEDCLIENT, l10nDark("seedClients"), l10nDark("seedClientsShort"));
			peerStatsSeedClientsListItem.addInlineBox(":\u00a0" + numberOfSeedClients);
		}
		if (numberOfRoutingDisabled > 0) {
			InlineBox peerStatsRoutingDisabledListItem = peerStatsList.addItem().addInlineBox();
			peerStatsRoutingDisabledListItem.addInlineBox(Category.PEERROUTINGDISABLED, l10nDark("routingDisabled"), l10nDark("routingDisabledShort"));
			peerStatsRoutingDisabledListItem.addInlineBox(":\u00a0" + numberOfRoutingDisabled);
		}
		if (numberOfNoLoadStats > 0) {
			InlineBox peerStatsNoLoadStatsListItem = peerStatsList.addItem().addInlineBox();
			peerStatsNoLoadStatsListItem.addInlineBox(Category.PEERNOLOADSTATS, l10nDark("noLoadStats"), l10nDark("noLoadStatsShort"));
			peerStatsNoLoadStatsListItem.addInlineBox(":\u00a0" + numberOfNoLoadStats);
		}
		OpennetManager om = node.getOpennet();
		if(om != null) {
			peerStatsList.addItem(l10n("maxTotalPeers") + ": " + om.getNumberOfConnectedPeersToAimIncludingDarknet());
			peerStatsList.addItem(l10n("maxOpennetPeers") + ": " + om.getNumberOfConnectedPeersToAim());
		}
	}

	private static String l10n(String key) {
		return NodeL10n.getBase().getString("StatisticsToadlet."+key);
	}
	
	private static String l10nDark(String key) {
		return NodeL10n.getBase().getString("DarknetConnectionsToadlet."+key);
	}

	private static String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("StatisticsToadlet."+key, new String[] { pattern }, new String[] { value });
	}
	
	private static String l10n(String key, String[] patterns, String[] values) {
		return NodeL10n.getBase().getString("StatisticsToadlet."+key, patterns, values);
	}
	
	private void drawActivityBox(InfoboxWidget activityInfobox, boolean advancedModeEnabled) {
		
		activityInfobox.setTitle(l10nDark("activityTitle"));
		OutputList activityList = drawActivity(activityInfobox.body, node);
		
		int numARKFetchers = node.getNumARKFetchers();

		if (advancedModeEnabled && activityList != null) {
			if (numARKFetchers > 0)
				activityList.addItem("ARK\u00a0Fetch\u00a0Requests:\u00a0" + numARKFetchers);
			activityList.addItem("BackgroundFetcherByUSKSize:\u00a0" + node.clientCore.uskManager.getBackgroundFetcherByUSKSize());
			activityList.addItem("temporaryBackgroundFetchersLRUSize:\u00a0" + node.clientCore.uskManager.getTemporaryBackgroundFetchersLRU());
		}
		
	}
	
	static void drawBandwidth(OutputList activityList, Node node, long nodeUptimeSeconds, boolean isAdvancedModeEnabled) {
		long[] total = node.collector.getTotalIO();
		if(total[0] == 0 || total[1] == 0)
			return;
		long total_output_rate = (total[0]) / nodeUptimeSeconds;
		long total_input_rate = (total[1]) / nodeUptimeSeconds;
		long totalPayload = node.getTotalPayloadSent();
		long total_payload_rate = totalPayload / nodeUptimeSeconds;
		if(node.clientCore == null) throw new NullPointerException();
		BandwidthStatsContainer stats = node.clientCore.bandwidthStatsPutter.getLatestBWData();
		if(stats == null) throw new NullPointerException();
		long overall_total_out = stats.totalBytesOut;
		long overall_total_in = stats.totalBytesIn;
		int percent = (int) (100 * totalPayload / total[0]);
		long[] rate = node.nodeStats.getNodeIOStats();
		long delta = (rate[5] - rate[2]) / 1000;
		if(delta > 0) {
			long output_rate = (rate[3] - rate[0]) / delta;
			long input_rate = (rate[4] - rate[1]) / delta;
			SubConfig nodeConfig = node.config.get("node");
			int outputBandwidthLimit = nodeConfig.getInt("outputBandwidthLimit");
			int inputBandwidthLimit = nodeConfig.getInt("inputBandwidthLimit");
			if(inputBandwidthLimit == -1) {
				inputBandwidthLimit = outputBandwidthLimit * 4;
			}
			activityList.addItem(l10n("inputRate", new String[]{"rate", "max"}, new String[]{SizeUtil.formatSize(input_rate, true), SizeUtil.formatSize(inputBandwidthLimit, true)}));
			activityList.addItem(l10n("outputRate", new String[]{"rate", "max"}, new String[]{SizeUtil.formatSize(output_rate, true), SizeUtil.formatSize(outputBandwidthLimit, true)}));
		}
		activityList.addItem(l10n("totalInputSession", new String[]{"total", "rate"}, new String[]{SizeUtil.formatSize(total[1], true), SizeUtil.formatSize(total_input_rate, true)}));
		activityList.addItem(l10n("totalOutputSession", new String[]{"total", "rate"}, new String[]{SizeUtil.formatSize(total[0], true), SizeUtil.formatSize(total_output_rate, true)}));
		activityList.addItem(l10n("payloadOutput", new String[]{"total", "rate", "percent"}, new String[]{SizeUtil.formatSize(totalPayload, true), SizeUtil.formatSize(total_payload_rate, true), Integer.toString(percent)}));
		activityList.addItem(l10n("totalInput", new String[]{"total"}, new String[]{SizeUtil.formatSize(overall_total_in, true)}));
		activityList.addItem(l10n("totalOutput", new String[]{"total"}, new String[]{SizeUtil.formatSize(overall_total_out, true)}));
		if(isAdvancedModeEnabled) {
			long totalBytesSentCHKRequests = node.nodeStats.getCHKRequestTotalBytesSent();
			long totalBytesSentSSKRequests = node.nodeStats.getSSKRequestTotalBytesSent();
			long totalBytesSentCHKInserts = node.nodeStats.getCHKInsertTotalBytesSent();
			long totalBytesSentSSKInserts = node.nodeStats.getSSKInsertTotalBytesSent();
			long totalBytesSentOfferedKeys = node.nodeStats.getOfferedKeysTotalBytesSent();
			long totalBytesSendOffers = node.nodeStats.getOffersSentBytesSent();
			long totalBytesSentSwapOutput = node.nodeStats.getSwappingTotalBytesSent();
			long totalBytesSentAuth = node.nodeStats.getTotalAuthBytesSent();
			long totalBytesSentAckOnly = node.nodeStats.getNotificationOnlyPacketsSentBytes();
			long totalBytesSentResends = node.nodeStats.getResendBytesSent();
			long totalBytesSentUOM = node.nodeStats.getUOMBytesSent();
			long totalBytesSentAnnounce = node.nodeStats.getAnnounceBytesSent();
			long totalBytesSentAnnouncePayload = node.nodeStats.getAnnounceBytesPayloadSent();
			long totalBytesSentRoutingStatus = node.nodeStats.getRoutingStatusBytes();
			long totalBytesSentNetworkColoring = node.nodeStats.getNetworkColoringSentBytes();
			long totalBytesSentPing = node.nodeStats.getPingSentBytes();
			long totalBytesSentProbeRequest = node.nodeStats.getProbeRequestSentBytes();
			long totalBytesSentRouted = node.nodeStats.getRoutedMessageSentBytes();
			long totalBytesSentDisconn = node.nodeStats.getDisconnBytesSent();
			long totalBytesSentInitial = node.nodeStats.getInitialMessagesBytesSent();
			long totalBytesSentChangedIP = node.nodeStats.getChangedIPBytesSent();
			long totalBytesSentNodeToNode = node.nodeStats.getNodeToNodeBytesSent();
			long totalBytesSentAllocationNotices = node.nodeStats.getAllocationNoticesBytesSent();
			long totalBytesSentFOAF = node.nodeStats.getFOAFBytesSent();
			long totalBytesSentRemaining = total[0] - 
				(totalPayload + totalBytesSentCHKRequests + totalBytesSentSSKRequests +
				totalBytesSentCHKInserts + totalBytesSentSSKInserts +
				totalBytesSentOfferedKeys + totalBytesSendOffers + totalBytesSentSwapOutput + 
				totalBytesSentAuth + totalBytesSentAckOnly + totalBytesSentResends +
				totalBytesSentUOM + totalBytesSentAnnounce + 
				totalBytesSentRoutingStatus + totalBytesSentNetworkColoring + totalBytesSentPing +
				totalBytesSentProbeRequest + totalBytesSentRouted + totalBytesSentDisconn + 
				totalBytesSentInitial + totalBytesSentChangedIP + totalBytesSentNodeToNode + totalBytesSentAllocationNotices + totalBytesSentFOAF);
			activityList.addItem(l10n("requestOutput", new String[]{"chk", "ssk"}, new String[]{SizeUtil.formatSize(totalBytesSentCHKRequests, true), SizeUtil.formatSize(totalBytesSentSSKRequests, true)}));
			activityList.addItem(l10n("insertOutput", new String[]{"chk", "ssk"}, new String[]{SizeUtil.formatSize(totalBytesSentCHKInserts, true), SizeUtil.formatSize(totalBytesSentSSKInserts, true)}));
			activityList.addItem(l10n("offeredKeyOutput", new String[]{"total", "offered"}, new String[]{SizeUtil.formatSize(totalBytesSentOfferedKeys, true), SizeUtil.formatSize(totalBytesSendOffers, true)}));
			activityList.addItem(l10n("swapOutput", "total", SizeUtil.formatSize(totalBytesSentSwapOutput, true)));
			activityList.addItem(l10n("authBytes", "total", SizeUtil.formatSize(totalBytesSentAuth, true)));
			activityList.addItem(l10n("ackOnlyBytes", "total", SizeUtil.formatSize(totalBytesSentAckOnly, true)));
			activityList.addItem(l10n("resendBytes", new String[]{"total", "percent"}, new String[]{SizeUtil.formatSize(totalBytesSentResends, true), Long.toString((100 * totalBytesSentResends) / Math.max(1, total[0]))}));
			activityList.addItem(l10n("uomBytes", "total", SizeUtil.formatSize(totalBytesSentUOM, true)));
			activityList.addItem(l10n("announceBytes", new String[]{"total", "payload"}, new String[]{SizeUtil.formatSize(totalBytesSentAnnounce, true), SizeUtil.formatSize(totalBytesSentAnnouncePayload, true)}));
			activityList.addItem(l10n("adminBytes", new String[]{"routingStatus", "disconn", "initial", "changedIP"}, new String[]{SizeUtil.formatSize(totalBytesSentRoutingStatus, true), SizeUtil.formatSize(totalBytesSentDisconn, true), SizeUtil.formatSize(totalBytesSentInitial, true), SizeUtil.formatSize(totalBytesSentChangedIP, true)}));
			activityList.addItem(l10n("debuggingBytes", new String[]{"netColoring", "ping", "probe", "routed"}, new String[]{SizeUtil.formatSize(totalBytesSentNetworkColoring, true), SizeUtil.formatSize(totalBytesSentPing, true), SizeUtil.formatSize(totalBytesSentProbeRequest, true), SizeUtil.formatSize(totalBytesSentRouted, true)}));
			activityList.addItem(l10n("nodeToNodeBytes", "total", SizeUtil.formatSize(totalBytesSentNodeToNode, true)));
			activityList.addItem(l10n("loadAllocationNoticesBytes", "total", SizeUtil.formatSize(totalBytesSentAllocationNotices, true)));
			activityList.addItem(l10n("foafBytes", "total", SizeUtil.formatSize(totalBytesSentFOAF, true)));
			activityList.addItem(l10n("unaccountedBytes", new String[]{"total", "percent"},
				new String[]{SizeUtil.formatSize(totalBytesSentRemaining, true), Integer.toString((int) (totalBytesSentRemaining * 100 / total[0]))}));
			double sentOverheadPerSecond = node.nodeStats.getSentOverheadPerSecond();
			activityList.addItem(l10n("totalOverhead", new String[]{"rate", "percent"},
				new String[]{SizeUtil.formatSize((long) sentOverheadPerSecond), Integer.toString((int) ((100 * sentOverheadPerSecond) / total_output_rate))}));
		}
	}

	static OutputList drawActivity(HTMLNode activityInfoboxContent, Node node) {
		RequestTracker tracker = node.tracker;
		int numLocalCHKInserts = tracker.getNumLocalCHKInserts();
		int numRemoteCHKInserts = tracker.getNumRemoteCHKInserts();
		int numLocalSSKInserts = tracker.getNumLocalSSKInserts();
		int numRemoteSSKInserts = tracker.getNumRemoteSSKInserts();
		int numLocalCHKRequests = tracker.getNumLocalCHKRequests();
		int numRemoteCHKRequests = tracker.getNumRemoteCHKRequests();
		int numLocalSSKRequests = tracker.getNumLocalSSKRequests();
		int numRemoteSSKRequests = tracker.getNumRemoteSSKRequests();
		int numTransferringRequests = node.getNumTransferringRequestSenders();
		int numTransferringRequestHandlers = node.getNumTransferringRequestHandlers();
		int numCHKOfferReplys = tracker.getNumCHKOfferReplies();
		int numSSKOfferReplys = tracker.getNumSSKOfferReplies();
		int numCHKRequests = numLocalCHKRequests + numRemoteCHKRequests;
		int numSSKRequests = numLocalSSKRequests + numRemoteSSKRequests;
		int numCHKInserts = numLocalCHKInserts + numRemoteCHKInserts;
		int numSSKInserts = numLocalSSKInserts + numRemoteSSKInserts;
		if ((numTransferringRequests == 0) &&
				(numCHKRequests == 0) && (numSSKRequests == 0) &&
				(numCHKInserts == 0) && (numSSKInserts == 0) &&
				(numTransferringRequestHandlers == 0) && 
				(numCHKOfferReplys == 0) && (numSSKOfferReplys == 0)) {
			activityInfoboxContent.addText(l10n("noRequests"));
			
			return null;
		} else {
			OutputList activityList = new OutputList();
			activityInfoboxContent.addChild(activityList);
			if (numCHKInserts > 0 || numSSKInserts > 0) {
				activityList.addItem(NodeL10n.getBase().getString("StatisticsToadlet.activityInserts",
					new String[]{"CHKhandlers", "SSKhandlers", "local"},
					new String[]{Integer.toString(numCHKInserts), Integer.toString(numSSKInserts), Integer.toString(numLocalCHKInserts) + "/" + Integer.toString(numLocalSSKInserts)}));
			}
			if (numCHKRequests > 0 || numSSKRequests > 0) {
				activityList.addItem(NodeL10n.getBase().getString("StatisticsToadlet.activityRequests",
					new String[]{"CHKhandlers", "SSKhandlers", "local"},
					new String[]{Integer.toString(numCHKRequests), Integer.toString(numSSKRequests), Integer.toString(numLocalCHKRequests) + "/" + Integer.toString(numLocalSSKRequests)}));
			}
			if (numTransferringRequests > 0 || numTransferringRequestHandlers > 0) {
				activityList.addItem(NodeL10n.getBase().getString("StatisticsToadlet.transferringRequests",
					new String[]{"senders", "receivers", "turtles"}, new String[]{Integer.toString(numTransferringRequests), Integer.toString(numTransferringRequestHandlers), "0"}));
			}
			if (numCHKOfferReplys > 0 || numSSKOfferReplys > 0) {
				activityList.addItem(NodeL10n.getBase().getString("StatisticsToadlet.offerReplys",
					new String[]{"chk", "ssk"}, new String[]{Integer.toString(numCHKOfferReplys), Integer.toString(numSSKOfferReplys)}));
			}
			activityList.addItem(NodeL10n.getBase().getString("StatisticsToadlet.runningBlockTransfers",
				new String[]{"sends", "receives"}, new String[]{Integer.toString(BlockTransmitter.getRunningSends()), Integer.toString(BlockReceiver.getRunningReceives())}));
			return activityList;
		}
	}

	private void drawOverviewBox(InfoboxWidget overviewInfobox, long nodeUptimeSeconds, long nodeUptimeTotal, long now, double swaps, double noSwaps) {
		
		overviewInfobox.setTitle("Node status overview");
		OutputList overviewList = new OutputList();
		overviewInfobox.body.addChild(overviewList);
		/* node status values */
		int bwlimitDelayTime = (int) stats.getBwlimitDelayTime();
		int bwlimitDelayTimeBulk = (int) stats.getBwlimitDelayTimeBulk();
		int bwlimitDelayTimeRT = (int) stats.getBwlimitDelayTimeRT();
		int nodeAveragePingTime = (int) stats.getNodeAveragePingTime();
		double numberOfRemotePeerLocationsSeenInSwaps = node.getNumberOfRemotePeerLocationsSeenInSwaps();

		// Darknet
		int darknetSizeEstimateSession = stats.getDarknetSizeEstimate(-1);
		int darknetSizeEstimate24h = 0;
		int darknetSizeEstimate48h = 0;
		if(nodeUptimeSeconds > (24*60*60)) {  // 24 hours
			darknetSizeEstimate24h = stats.getDarknetSizeEstimate(now - (24*60*60*1000));  // 48 hours
		}
		if(nodeUptimeSeconds > (48*60*60)) {  // 48 hours
			darknetSizeEstimate48h = stats.getDarknetSizeEstimate(now - (48*60*60*1000));  // 48 hours
		}
		// Opennet
		int opennetSizeEstimateSession = stats.getOpennetSizeEstimate(-1);
		int opennetSizeEstimate24h = 0;
		int opennetSizeEstimate48h = 0;
		if (nodeUptimeSeconds > (24 * 60 * 60)) { // 24 hours
			opennetSizeEstimate24h = stats.getOpennetSizeEstimate(now - (24 * 60 * 60 * 1000)); // 48 hours
		}
		if (nodeUptimeSeconds > (48 * 60 * 60)) { // 48 hours
			opennetSizeEstimate48h = stats.getOpennetSizeEstimate(now - (48 * 60 * 60 * 1000)); // 48 hours
		}
		
		double routingMissDistanceLocal =  stats.routingMissDistanceLocal.currentValue();
		double routingMissDistanceRemote =  stats.routingMissDistanceRemote.currentValue();
		double routingMissDistanceOverall =  stats.routingMissDistanceOverall.currentValue();
		double routingMissDistanceBulk =  stats.routingMissDistanceBulk.currentValue();
		double routingMissDistanceRT =  stats.routingMissDistanceRT.currentValue();
		double backedOffPercent =  stats.backedOffPercent.currentValue();
		overviewList.addItem("bwlimitDelayTime:\u00a0" + bwlimitDelayTime + "ms");
		overviewList.addItem("bwlimitDelayTimeBulk:\u00a0" + bwlimitDelayTimeBulk + "ms");
		overviewList.addItem("bwlimitDelayTimeRT:\u00a0" + bwlimitDelayTimeRT + "ms");
		overviewList.addItem("nodeAveragePingTime:\u00a0" + nodeAveragePingTime + "ms");
		overviewList.addItem("darknetSizeEstimateSession:\u00a0" + darknetSizeEstimateSession + "\u00a0nodes");
		if(nodeUptimeSeconds > (24*60*60)) {  // 24 hours
			overviewList.addItem("darknetSizeEstimate24h:\u00a0" + darknetSizeEstimate24h + "\u00a0nodes");
		}
		if(nodeUptimeSeconds > (48*60*60)) {  // 48 hours
			overviewList.addItem("darknetSizeEstimate48h:\u00a0" + darknetSizeEstimate48h + "\u00a0nodes");
		}
		overviewList.addItem("opennetSizeEstimateSession:\u00a0" + opennetSizeEstimateSession + "\u00a0nodes");
		if (nodeUptimeSeconds > (24 * 60 * 60)) { // 24 hours
			overviewList.addItem("opennetSizeEstimate24h:\u00a0" + opennetSizeEstimate24h + "\u00a0nodes");
		}
		if (nodeUptimeSeconds > (48 * 60 * 60)) { // 48 hours
			overviewList.addItem("opennetSizeEstimate48h:\u00a0" + opennetSizeEstimate48h + "\u00a0nodes");
		}
		if ((numberOfRemotePeerLocationsSeenInSwaps > 0.0) && ((swaps > 0.0) || (noSwaps > 0.0))) {
			overviewList.addItem("avrConnPeersPerNode:\u00a0" + fix6p6.format(numberOfRemotePeerLocationsSeenInSwaps / (swaps + noSwaps)) + "\u00a0peers");
		}
		overviewList.addItem("nodeUptimeSession:\u00a0" + TimeUtil.formatTime(nodeUptimeSeconds * 1000));
		overviewList.addItem("nodeUptimeTotal:\u00a0" + TimeUtil.formatTime(nodeUptimeTotal));
		overviewList.addItem("routingMissDistanceLocal:\u00a0" + fix1p4.format(routingMissDistanceLocal));
		overviewList.addItem("routingMissDistanceRemote:\u00a0" + fix1p4.format(routingMissDistanceRemote));
		overviewList.addItem("routingMissDistanceOverall:\u00a0" + fix1p4.format(routingMissDistanceOverall));
		overviewList.addItem("routingMissDistanceBulk:\u00a0" + fix1p4.format(routingMissDistanceBulk));
		overviewList.addItem("routingMissDistanceRT:\u00a0" + fix1p4.format(routingMissDistanceRT));
		overviewList.addItem("backedOffPercent:\u00a0" + fix3p1pct.format(backedOffPercent));
		overviewList.addItem("pInstantReject:\u00a0" + fix3p1pct.format(stats.pRejectIncomingInstantly()));
		overviewList.addItem("unclaimedFIFOSize:\u00a0" + node.getUnclaimedFIFOSize());
		overviewList.addItem("RAMBucketPoolSize:\u00a0" + SizeUtil.formatSize(core.tempBucketFactory.getRamUsed()) + " / " + SizeUtil.formatSize(core.tempBucketFactory.getMaxRamUsed()));
		overviewList.addItem("uptimeAverage:\u00a0" + fix3p1pct.format(node.uptime.getUptime()));
		
		long[] decoded = IncomingPacketFilterImpl.getDecodedPackets();
		if(decoded != null) {
			overviewList.addItem("packetsDecoded:\u00a0" + fix3p1pct.format(((double) decoded[0]) / ((double) decoded[1])) + "\u00a0(" + decoded[1] + ")");
		}
		
	}

	private void drawBandwidthBox(InfoboxWidget bandwidthInfobox, long nodeUptimeSeconds, boolean isAdvancedModeEnabled) {
		bandwidthInfobox.setTitle(l10n("bandwidthTitle"));
		OutputList bandwidthList = new OutputList();
		bandwidthInfobox.body.addChild(bandwidthList);
		drawBandwidth(bandwidthList, node, nodeUptimeSeconds, isAdvancedModeEnabled);
	}

	// FIXME this should probably be moved to nodestats so it can be used by FCP??? would have to make ThreadBunch public :<
	private void getThreadNames(OutputList threadUsageList) {
		Thread[] threads = stats.getThreads();

		LinkedHashMap<String, ThreadBunch> map = new LinkedHashMap<String, ThreadBunch>();
		int totalCount = 0;
		for(int i=0;i<threads.length;i++) {
			if(threads[i] == null) break;
			String name = NativeThread.normalizeName(threads[i].getName());
			ThreadBunch bunch = map.get(name);
			if(bunch != null) {
				bunch.count++;
			} else {
				map.put(name, new ThreadBunch(name, 1));
			}
			totalCount++;
		}
		ThreadBunch[] bunches = map.values().toArray(new ThreadBunch[map.size()]);
		Arrays.sort(bunches, new Comparator<ThreadBunch>() {
			@Override
			public int compare(ThreadBunch b0, ThreadBunch b1) {
				if(b0.count > b1.count) return -1;
				if(b0.count < b1.count) return 1;
				return b0.name.compareTo(b1.name);
			}

		});
		double thisThreadPercentOfTotal;
		for(int i=0; i<bunches.length; i++) {
			thisThreadPercentOfTotal = ((double) bunches[i].count) / ((double) totalCount);
			threadUsageList.addItem("" + bunches[i].name + ":\u00a0" + Integer.toString(bunches[i].count) + "\u00a0(" + fix3p1pct.format(thisThreadPercentOfTotal) + ')');
		}
	}

	private static class ThreadBunch {
		public ThreadBunch(String name2, int i) {
			this.name = name2;
			this.count = i;
		}
		String name;
		int count;
	}

	private final static int PEER_CIRCLE_RADIUS = 100;
	private final static int PEER_CIRCLE_INNER_RADIUS = 60;
	private final static int PEER_CIRCLE_ADDITIONAL_FREE_SPACE = 10;
	private final static long MAX_CIRCLE_AGE_THRESHOLD = 24l*60*60*1000;   // 24 hours
	private final static int HISTOGRAM_LENGTH = 10;

	private void addNodeCircle (Table circleTable, double myLocation) {
		int[] histogram = new int[HISTOGRAM_LENGTH];
		for (int i = 0; i < HISTOGRAM_LENGTH; i++) {
			histogram[i] = 0;
		}
		Row nodeCircleTableRow = circleTable.addRow();
		Row nodeHistogramLegendTableRow = circleTable.addRow();
		Row nodeHistogramGraphTableRow = circleTable.addRow();
		Cell nodeCircleTableCell = nodeCircleTableRow.addCell(10, Category.FIRST);
		Cell nodeHistogramLegendCell;
		Cell nodeHistogramGraphCell;
		Box nodeCircleInfoboxContent = nodeCircleTableCell.addBox(Category.PEERCIRCLE);
		nodeCircleInfoboxContent.addAttribute("style", "position: relative; height: " + ((PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) * 2) + "px; width: " + ((PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) * 2) + "px");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0, false, 1.0), Category.MARK, "|");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.125, false, 1.0), Category.MARK, "+");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.25, false, 1.0),  Category.MARK, "--");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.375, false, 1.0), Category.MARK, "+");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.5, false, 1.0),   Category.MARK, "|");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.625, false, 1.0), Category.MARK, "+");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.75, false, 1.0),  Category.MARK, "--");
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.875, false, 1.0), Category.MARK, "+");
		nodeCircleInfoboxContent.addInlineBox("position: absolute; top: " + PEER_CIRCLE_RADIUS + "px; left: " + (PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) + "px", Category.MARK, "+");
		final Object[] knownLocsCopy = stats.getKnownLocations(-1);
		final Double[] locations = (Double[])knownLocsCopy[0];
		final Long[] timestamps = (Long[])knownLocsCopy[1];
		Double location;
		Long locationTime;
		double strength = 1.0;
		long now = System.currentTimeMillis();
		long age = 1;
		int histogramIndex;
		int nodeCount = 0;
		for(int i=0; i<locations.length; i++){
			nodeCount += 1;
			location = locations[i];
			locationTime = timestamps[i];
			age = now - locationTime.longValue();
			if( age > MAX_CIRCLE_AGE_THRESHOLD ) {
				age = MAX_CIRCLE_AGE_THRESHOLD;
			}
			strength = 1 - ((double) age / MAX_CIRCLE_AGE_THRESHOLD );
			histogramIndex = (int) (Math.floor(location.doubleValue() * HISTOGRAM_LENGTH));
			histogram[histogramIndex]++;
			
			nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(location.doubleValue(), false, strength), Category.CONNECTED, "x");
		}
		nodeCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(myLocation, true, 1.0), Category.ME, "x");
		//
		double histogramPercent;
		for (int i = 0; i < HISTOGRAM_LENGTH; i++) {
			nodeHistogramLegendCell = nodeHistogramLegendTableRow.addCell();
			nodeHistogramGraphCell = nodeHistogramGraphTableRow.addCell();
			nodeHistogramGraphCell.addAttribute("style", "height: 100px;");
			nodeHistogramLegendCell.addBox(Category.HISTOGRAMLABEL).addText(fix1p1.format(((double) i) / HISTOGRAM_LENGTH));
			histogramPercent = nodeCount==0 ? 0 : ((double)histogram[ i ] / nodeCount);
			
			// Don't use HTMLNode here to speed things up
			nodeHistogramGraphCell.addChild("%", "<div class=\"histogramConnected\" style=\"height: " + fix3pctUS.format(histogramPercent) + "; width: 100%;\">\u00a0</div>");
		}
	}
	
	private void addSpecialisation(Table table, double peerLocation, int incomingRequestsCount, int[] incomingRequestLocation) {
		Row nodeHistogramLegendTableRow = table.addRow();
		Row nodeHistogramGraphTableRow = table.addRow();
		int myIndex = (int)(peerLocation * incomingRequestLocation.length);
		for (int i = 0; i<incomingRequestLocation.length; i++) {
			Cell nodeHistogramLegendCell = nodeHistogramLegendTableRow.addCell();
			Cell nodeHistogramGraphCell = nodeHistogramGraphTableRow.addCell();
			nodeHistogramGraphCell.addAttribute("style", "height: 100px;");
			OutputNode nodeHistogramGraphCell2 = nodeHistogramLegendCell.addBox(Category.HISTOGRAMLABEL);
			if(i == myIndex) {
				 nodeHistogramGraphCell2 = nodeHistogramGraphCell2.addInlineBox(Category.ME);
			}
			nodeHistogramGraphCell2.addText(fix1p1.format(((double) i) / incomingRequestLocation.length));
			Box graphCell = new Box(Category.HISTOGRAMCONNECTED, "\u00a0");
			graphCell.addAttribute("style", "height: " + fix3pctUS.format(((double)incomingRequestLocation[i]) / incomingRequestsCount) + "; width: 100%;");
			nodeHistogramGraphCell.addChild(graphCell);
		}
	}
	
	private void addCombinedSpecialisation(Table table, double peerLocation, int locallyOriginatingRequestsCount, int[] locallyOriginatingRequests, int remotelyOriginatingRequestsCount, int[] remotelyOriginatingRequests) {
		assert(locallyOriginatingRequests.length == remotelyOriginatingRequests.length);
		Row nodeHistogramLegendTableRow = table.addRow();
		Row nodeHistogramGraphTableRow = table.addRow();
		int myIndex = (int)(peerLocation * locallyOriginatingRequests.length);
		for (int i = 0; i<locallyOriginatingRequests.length; i++) {
			Cell nodeHistogramLegendCell = nodeHistogramLegendTableRow.addCell();
			Cell nodeHistogramGraphCell = nodeHistogramGraphTableRow.addCell();
			nodeHistogramGraphTableRow.addAttribute("style", "height: 100px;");
			OutputNode nodeHistogramGraphCell2 = nodeHistogramLegendCell.addBox(Category.HISTOGRAMLABEL);
			if(i == myIndex) {
				 nodeHistogramGraphCell2 = nodeHistogramGraphCell2.addInlineBox(Category.ME);
			}
			nodeHistogramGraphCell2.addText(fix1p1.format(((double) i) / locallyOriginatingRequests.length));
			Box graphCell = new Box(Category.HISTOGRAMCONNECTED, "\u00a0");
			graphCell.addAttribute("style", "height: " + fix3pctUS.format(((double)locallyOriginatingRequests[i]) / locallyOriginatingRequestsCount) + "; width: 100%;");
			nodeHistogramGraphCell.addChild(graphCell);
			graphCell = new Box(Category.HISTOGRAMDISCONNECTED, "\u00a0");
			graphCell.addAttribute("style", "height: " + fix3pctUS.format(((double) remotelyOriginatingRequests[i]) / remotelyOriginatingRequestsCount) + "; width: 100%;");
			nodeHistogramGraphCell.addChild(graphCell);
		}
	}

	private void addPeerCircle(Table circleTable, PeerNodeStatus[] peerNodeStatuses, double myLocation) {
		int[] histogramConnected = new int[HISTOGRAM_LENGTH];
		int[] histogramDisconnected = new int[HISTOGRAM_LENGTH];
		for (int i = 0; i < HISTOGRAM_LENGTH; i++) {
			histogramConnected[i] = 0;
			histogramDisconnected[i] = 0;
		}
		Row peerCircleTableRow = circleTable.addRow();
		Row peerHistogramLegendTableRow = circleTable.addRow();
		Row peerHistogramGraphTableRow = circleTable.addRow();
		Cell peerCircleTableCell = peerCircleTableRow.addCell(10, Category.FIRST);
		Cell peerHistogramLegendCell;
		Cell peerHistogramGraphCell;
		Box peerCircleInfoboxContent = peerCircleTableCell.addBox(Category.PEERCIRCLE);
		peerCircleInfoboxContent.addAttribute("style", "position: relative; height: " + ((PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) * 2) + "px; width: " + ((PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) * 2) + "px");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0, false, 1.0), Category.MARK, "|");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.125, false, 1.0), Category.MARK, "+");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.25, false, 1.0),  Category.MARK, "--");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.375, false, 1.0), Category.MARK, "+");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.5, false, 1.0),   Category.MARK, "|");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.625, false, 1.0), Category.MARK, "+");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.75, false, 1.0),  Category.MARK, "--");
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(0.875, false, 1.0), Category.MARK, "+");
		peerCircleInfoboxContent.addInlineBox("position: absolute; top: " + PEER_CIRCLE_RADIUS + "px; left: " + (PEER_CIRCLE_RADIUS + PEER_CIRCLE_ADDITIONAL_FREE_SPACE) + "px", Category.MARK, "+");

		PeerNodeStatus peerNodeStatus;
		double peerLocation;
		double peerDistance;
		int histogramIndex;
		int peerCount = peerNodeStatuses.length;
		int newPeerCount = 0;
		for (int peerIndex = 0; peerIndex < peerCount; peerIndex++) {
			peerNodeStatus = peerNodeStatuses[peerIndex];
			peerLocation = peerNodeStatus.getLocation();
			if(!peerNodeStatus.isSearchable()) continue;
			if(peerLocation < 0.0 || peerLocation > 1.0) continue;
			double[] foafLocations=peerNodeStatus.getPeersLocation();
			if (foafLocations!=null && peerNodeStatus.isRoutable()) {
				for (double foafLocation : foafLocations) {
					//one grey dot for each "Friend-of-a-friend"
					peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(foafLocation, false, 0.9), Category.DISCONNECTED, ".");
				}
			}
			newPeerCount++;
			peerDistance = Location.distance( myLocation, peerLocation );
			histogramIndex = (int) (Math.floor(peerDistance * HISTOGRAM_LENGTH * 2));
			if (peerNodeStatus.isConnected()) {
				histogramConnected[histogramIndex]++;
			} else {
				histogramDisconnected[histogramIndex]++;
			}
			peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(peerLocation, false, (1.0 - peerNodeStatus.getPReject())), ((peerNodeStatus.isConnected())? Category.CONNECTED :


				Category.DISCONNECTED), ((peerNodeStatus.isOpennet())?"o":"x"));
		}
		peerCircleInfoboxContent.addInlineBox(generatePeerCircleStyleString(myLocation, true, 1.0), Category.ME, "x");
		//
		double histogramPercent;
		for (int i = 0; i < HISTOGRAM_LENGTH; i++) {
			peerHistogramLegendCell = peerHistogramLegendTableRow.addCell();
			peerHistogramGraphCell = peerHistogramGraphTableRow.addCell();
			peerHistogramGraphCell.addAttribute("style", "height: 100px;");
			peerHistogramLegendCell.addChild(new Box(Category.HISTOGRAMLABEL)).addText(fix1p2.format(((double) i) / (HISTOGRAM_LENGTH * 2)));
			//
			histogramPercent = ((double) histogramConnected[ i ] ) / newPeerCount;
			Box graphCell = new Box(Category.HISTOGRAMCONNECTED, "\u00a0");
			graphCell.addAttribute("style", "height: " + fix3pctUS.format(histogramPercent) + "; width: 100%;");
			peerHistogramGraphCell.addChild(graphCell);
			//
			histogramPercent = ((double) histogramDisconnected[ i ] ) / newPeerCount;
			graphCell = new Box(Category.HISTOGRAMDISCONNECTED, "\u00a0");
			graphCell.addAttribute("style", "height: " + fix3pctUS.format(histogramPercent) + "; width: 100%;");
			peerHistogramGraphCell.addChild(graphCell);
		}
	}

	private String generatePeerCircleStyleString (double peerLocation, boolean offsetMe, double strength) {
		peerLocation *= Math.PI * 2;
		//
		int offset = 0;
		if( offsetMe ) {
			// Make our own peer stand out from the crowd better so we can see it easier
			offset = -10;
		} else {
			offset = (int) (PEER_CIRCLE_INNER_RADIUS * (1.0 - strength));
		}
		double x = PEER_CIRCLE_ADDITIONAL_FREE_SPACE + PEER_CIRCLE_RADIUS + Math.sin(peerLocation) * (PEER_CIRCLE_RADIUS - offset);
		double y = PEER_CIRCLE_RADIUS - Math.cos(peerLocation) * (PEER_CIRCLE_RADIUS - offset);  // no PEER_CIRCLE_ADDITIONAL_FREE_SPACE for y-disposition
		//
		return "position: absolute; top: " + fix3p1US.format(y) + "px; left: " + fix3p1US.format(x) + "px";
	}

	@Override
	public String path() {
		return "/stats/";
	}
}
