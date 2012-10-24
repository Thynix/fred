/* Copyright 2007 Freenet Project Inc.
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.uielements.*;
import freenet.io.AddressTracker;
import freenet.io.AddressTrackerItem;
import freenet.io.AddressTrackerItem.Gap;
import freenet.io.InetAddressAddressTrackerItem;
import freenet.io.PeerAddressTrackerItem;
import freenet.io.comm.UdpSocketHandler;
import freenet.l10n.NodeL10n;
import freenet.node.FSParseException;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.support.SimpleFieldSet;
import freenet.support.TimeUtil;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

/**
 * Toadlet displaying information on the node's connectivity status.
 * Eventually this will include all information gathered by the node on its
 * connectivity from plugins, local IP detection, packet monitoring etc.
 * For the moment it's just a dump of the AddressTracker.
 * @author toad
 */
public class ConnectivityToadlet extends Toadlet {
	
	private final Node node;
	private final NodeClientCore core;

	protected ConnectivityToadlet(HighLevelSimpleClient client, Node node, NodeClientCore core) {
		super(client);
		this.node = node;
		this.core = core;
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		PageMaker pageMaker = ctx.getPageMaker();
		Page connectivityPage =
			pageMaker.getPage(NodeL10n.getBase().getString("ConnectivityToadlet.title"), ctx);
		/* add alert summary box */
		if (ctx.isAllowedFullAccess()) {
			connectivityPage.content.addChild(core.alerts.createSummary());
		}
		// our ports
		InfoboxWidget portInfobox =
			connectivityPage.content.addInfobox(InfoboxWidget.Type.NORMAL, l10nConn("nodePortsTitle"));
		OutputList portInfoList = portInfobox.body.addList();
		SimpleFieldSet fproxyConfig = node.config.get("fproxy").exportFieldSet(true);
		SimpleFieldSet fcpConfig = node.config.get("fcp").exportFieldSet(true);
		SimpleFieldSet tmciConfig = node.config.get("console").exportFieldSet(true);
		portInfoList.addItem(NodeL10n.getBase()
			.getString("DarknetConnectionsToadlet.darknetFnpPort", new String[]{"port"},
				new String[]{Integer.toString(node.getFNPPort())}));
		int opennetPort = node.getOpennetFNPPort();
		if (opennetPort > 0) {
			portInfoList.addItem(NodeL10n.getBase()
				.getString("DarknetConnectionsToadlet.opennetFnpPort", new String[]{"port"},
					new String[]{Integer.toString(opennetPort)}));
		}
		try {
			if (fproxyConfig.getBoolean("enabled", false)) {
				portInfoList.addItem(NodeL10n.getBase()
					.getString("DarknetConnectionsToadlet.fproxyPort", new String[]{"port"},
						new String[]{Integer.toString(fproxyConfig.getInt("port"))}));
			} else {
				portInfoList.addItem(l10nConn("fproxyDisabled"));
			}
			if (fcpConfig.getBoolean("enabled", false)) {
				portInfoList.addItem(NodeL10n.getBase()
					.getString("DarknetConnectionsToadlet.fcpPort", new String[]{"port"},
						new String[]{Integer.toString(fcpConfig.getInt("port"))}));
			} else {
				portInfoList.addItem(l10nConn("fcpDisabled"));
			}
			if (tmciConfig.getBoolean("enabled", false)) {
				portInfoList.addItem(NodeL10n.getBase()
					.getString("DarknetConnectionsToadlet.tmciPort", new String[]{"port"},
						new String[]{Integer.toString(tmciConfig.getInt("port"))}));
			} else {
				portInfoList.addItem(l10nConn("tmciDisabled"));
			}
		} catch (FSParseException e) {
			// ignore
		}
		// Add connection type box.
		node.ipDetector.addConnectionTypeBox(connectivityPage.content);
		UdpSocketHandler[] handlers = node.getPacketSocketHandlers();
		InfoboxWidget ConnectivitySummary = connectivityPage.content
			.addInfobox(InfoboxWidget.Type.WTF, Identifier.CONNECTIVITYSUMMARY,
				NodeL10n.getBase().getString("ConnectivityToadlet.summaryTitle"));
		Table SummaryTable = ConnectivitySummary.body.addTable();
		for (int i = 0; i < handlers.length; i++) {
			UdpSocketHandler handler = handlers[i];
			AddressTracker tracker = handlers[i].getAddressTracker();
			Row row = SummaryTable.addRow();
			row.addCell(handler.getTitle());
			row.addCell(AddressTracker.statusString(tracker.getPortForwardStatus()));
		}
		if (ctx.getContainer().isAdvancedModeEnabled()) {
			// One box per port
			String noreply = l10n("noreply");
			String local = l10n("local");
			String remote = l10n("remote");
			long now = System.currentTimeMillis();
			for (int i = 0; i < handlers.length; i++) {
				// Peers
				AddressTracker tracker = handlers[i].getAddressTracker();
				SummaryTable = connectivityPage.content
					.addInfobox(InfoboxWidget.Type.WTF, Category.CONNECTIVITYPORT,
						NodeL10n.getBase().getString("ConnectivityToadlet.byPortTitle",
							new String[]{"port", "status", "tunnelLength"},
							new String[]{handlers[i].getTitle(), AddressTracker
								.statusString(tracker.getPortForwardStatus()),
								TimeUtil.formatTime(
									tracker.getLongestSendReceiveGap())})).body
					.addTable();
				Row row = SummaryTable.addRow();
				row.addHeader(l10n("addressTitle"));
				row.addHeader(l10n("sentReceivedTitle"));
				row.addHeader(l10n("localRemoteTitle"));
				row.addHeader(l10n("firstSendLeadTime"));
				row.addHeader(l10n("firstReceiveLeadTime"));
				for (int j = 0; j < AddressTrackerItem.TRACK_GAPS; j++) {
					row.addHeader();
				}
				PeerAddressTrackerItem[] items = tracker.getPeerAddressTrackerItems();
				for (int j = 0; j < items.length; j++) {
					row = SummaryTable.addRow();
					PeerAddressTrackerItem item = items[j];
					// Address
					row.addCell(item.peer.toString());
					// Sent/received packets
					row.addCell(item.packetsSent() + "/ " + item.packetsReceived());
					// Initiator: local/remote FIXME something more graphical e.g. colored cells
					row.addCell(item.packetsReceived() == 0 ? noreply :
						(item.weSentFirst() ? local : remote));
					// Lead in time to first packet sent
					row.addCell(TimeUtil.formatTime(item.timeFromStartupToFirstSentPacket()));
					// Lead in time to first packet received
					row.addCell(TimeUtil.formatTime(item.timeFromStartupToFirstReceivedPacket()));
					Gap[] gaps = item.getGaps();
					for (int k = 0; k < AddressTrackerItem.TRACK_GAPS; k++) {
						row.addCell(gaps[k].receivedPacketAt == 0 ? "" :
							(TimeUtil.formatTime(gaps[k].gapLength) + " @ " +
								TimeUtil.formatTime(now - gaps[k].receivedPacketAt) +
								" ago" /* fixme l10n */));
					}
				}
				// IPs
				SummaryTable = connectivityPage.content
					.addInfobox(InfoboxWidget.Type.WTF, Category.CONNECTIVITYIP,
						NodeL10n.getBase()
						.getString("ConnectivityToadlet.byIPTitle",
							new String[]{"ip", "status", "tunnelLength"},
							new String[]{handlers[i].getTitle(), AddressTracker
								.statusString(tracker.getPortForwardStatus()),
								TimeUtil.formatTime(
									tracker.getLongestSendReceiveGap())})).body
					.addTable();
				row = SummaryTable.addRow();
				row.addHeader(l10n("addressTitle"));
				row.addHeader(l10n("sentReceivedTitle"));
				row.addHeader(l10n("localRemoteTitle"));
				row.addHeader(l10n("firstSendLeadTime"));
				row.addHeader(l10n("firstReceiveLeadTime"));
				InetAddressAddressTrackerItem[] ipItems = tracker.getInetAddressTrackerItems();
				for (int j = 0; j < AddressTrackerItem.TRACK_GAPS; j++) {
					row.addHeader();
				}
				for (int j = 0; j < ipItems.length; j++) {
					row = SummaryTable.addRow();
					InetAddressAddressTrackerItem item = ipItems[j];
					// Address
					row.addCell(item.addr.toString());
					// Sent/received packets
					row.addCell(item.packetsSent() + "/ " + item.packetsReceived());
					// Initiator: local/remote FIXME something more graphical e.g. colored cells
					row.addCell(item.packetsReceived() == 0 ? noreply :
						(item.weSentFirst() ? local : remote));
					// Lead in time to first packet sent
					row.addCell(TimeUtil.formatTime(item.timeFromStartupToFirstSentPacket()));
					// Lead in time to first packet received
					row.addCell(TimeUtil.formatTime(item.timeFromStartupToFirstReceivedPacket()));
					Gap[] gaps = item.getGaps();
					for (int k = 0; k < AddressTrackerItem.TRACK_GAPS; k++) {
						row.addCell(gaps[k].receivedPacketAt == 0 ? "" :
							(TimeUtil.formatTime(gaps[k].gapLength) + " @ " +
								TimeUtil.formatTime(now - gaps[k].receivedPacketAt) +
								" ago" /* fixme l10n */));
					}
				}
			}
		}
		writeHTMLReply(ctx, 200, "OK", connectivityPage.generate());
	}

	private String l10nConn(String string) {
		return NodeL10n.getBase().getString("DarknetConnectionsToadlet."+string);
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("ConnectivityToadlet."+key);
	}

	public static final String PATH = "/connectivity/";
	
	@Override
	public String path() {
		return PATH;
	}
}
