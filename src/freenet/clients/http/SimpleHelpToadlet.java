/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.uielements.Cell;
import freenet.clients.http.uielements.Page;
import freenet.clients.http.uielements.Table;
import freenet.clients.http.uielements.Text;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

/**
 * Simple Help Toadlet.  Provides an offline means of looking up some basic info, howtos, and FAQ
 * Likely to be superceded someday by an offical Freesite and binary blob included in install package.
 * @author Juiceman
 */
public class SimpleHelpToadlet extends Toadlet {
	SimpleHelpToadlet(HighLevelSimpleClient client, NodeClientCore c) {
		super(client);
		this.core=c;
	}
	
	final NodeClientCore core;

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		Page page = ctx.getPageMaker()
			.getPage("Freenet " + NodeL10n.getBase().getString("FProxyToadlet.help"), ctx);
		if (ctx.isAllowedFullAccess()) {
			page.content.addChild(core.alerts.createSummary());
		}
		// Description infobox
		Text Description = new Text(NodeL10n.getBase().getString("SimpleHelpToadlet.descriptionText"));
		page.content.addInfobox(InfoboxType.INFORMATION, Identifier.FREENETDESCRIPTION,
			NodeL10n.getBase().getString("SimpleHelpToadlet.descriptionTitle"), Description);
		// Definitions infobox
		Table Definitions = new Table();
		Cell row = Definitions.addRow().addCell();
		row.addText(NodeL10n.getBase().getString("SimpleHelpToadlet.CHK"));
		row.addLineBreak();
		row.addText(NodeL10n.getBase().getString("SimpleHelpToadlet.SSK"));
		row.addLineBreak();
		row.addText(NodeL10n.getBase().getString("SimpleHelpToadlet.USK"));
		page.content.addInfobox(InfoboxType.INFORMATION, Identifier.FREENETDESCRIPTION,
			NodeL10n.getBase().getString("SimpleHelpToadlet.definitionsTitle"), Definitions);
		// Port forwarding, etc.
		Text Connectivity = new Text(NodeL10n.getBase().getString("SimpleHelpToadlet.connectivityText"));
		page.content.addInfobox(InfoboxType.INFORMATION, Identifier.FREENETDESCRIPTION,
			NodeL10n.getBase().getString("SimpleHelpToadlet.connectivityTitle"), Connectivity);
		this.writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	@Override
	public String path() {
		return "/help/";
	}
	
}
