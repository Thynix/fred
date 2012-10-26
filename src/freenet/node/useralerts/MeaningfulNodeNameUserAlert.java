/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.useralerts;

import freenet.clients.http.uielements.*;
import freenet.config.Option;
import freenet.config.SubConfig;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.support.HTMLNode;

public class MeaningfulNodeNameUserAlert extends AbstractUserAlert {
	private final Node node;

	public MeaningfulNodeNameUserAlert(Node n) {
		super(true, null, null, null, null, UserAlert.WARNING, true, NodeL10n.getBase().getString("UserAlert.hide"), true, null);
		this.node = n;
	}
	
	@Override
	public String getTitle() {
		return l10n("noNodeNickTitle");
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("MeaningfulNodeNameUserAlert."+key);
	}

	@Override
	public String getText() {
		return l10n("noNodeNick");
	}
	
	@Override
	public String getShortText() {
		return l10n("noNodeNickShort");
	}

	@Override
	public HTMLNode getHTMLText() {
		SubConfig sc = node.config.get("node");
		Option<?> o = sc.getOption("name");
		Box alertNode = new Box();
		HTMLNode textNode = alertNode.addBox();
		textNode.addText(l10n("noNodeNick"));
		Form formNode = alertNode.addForm("/config/" + sc.getPrefix(), "post");
		formNode.addInput(Input.Type.HIDDEN, "formPassword", node.clientCore.formPassword);
		formNode.addInput(Input.Type.HIDDEN, "subconfig", sc.getPrefix());
		OutputList listNode = formNode.addList(Category.CONFIG);
		Item itemNode = listNode.addItem();
		itemNode.addInlineBox(NodeL10n.getBase().getString("ConfigToadlet.defaultIs"), Category.CONFIGSHORTDESC).addChild(NodeL10n.getBase().getHTMLNode(o.getShortDesc()));
		itemNode.addInput(Input.Type.TEXT, "node.name", o.getValueString(), Category.CONFIG, o.getShortDesc());
		itemNode.addInlineBox(Category.CONFIGLONGDESC).addChild(NodeL10n.getBase().getHTMLNode(o.getLongDesc()));
		formNode.addInput(Input.Type.SUBMIT, NodeL10n.getBase().getString("UserAlert.apply"));
		formNode.addInput(Input.Type.RESET, NodeL10n.getBase().getString("UserAlert.reset"));
		return alertNode;
	}

	@Override
	public boolean isValid() {
		return node.peers.anyDarknetPeers();
	}
}
