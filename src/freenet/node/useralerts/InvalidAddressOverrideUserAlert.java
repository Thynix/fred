/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.useralerts;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.*;
import freenet.config.Option;
import freenet.config.SubConfig;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.support.HTMLNode;

public class InvalidAddressOverrideUserAlert extends AbstractUserAlert {
	
	public InvalidAddressOverrideUserAlert(Node n) {
		super(false, null, null, null, null, (short) 0, true, null, false, null);
		this.node = n;
	}
	
	final Node node;
	
	@Override
	public String getTitle() {
		return l10n("unknownAddressTitle");
	}

	@Override
	public String getText() {
		return l10n("unknownAddress");
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("InvalidAddressOverrideUserAlert."+key);
	}

	@Override
	public HTMLNode getHTMLText() {
		SubConfig sc = node.config.get("node");
		Option<?> o = sc.getOption("ipAddressOverride");
		
		Box textNode = new Box();
		NodeL10n.getBase().addL10nSubstitution(textNode, "InvalidAddressOverrideUserAlert.unknownAddressWithConfigLink", 
				new String[] { "link" }, 
				new HTMLNode[] { new Link("/config/node")});
		Form formNode = textNode.addForm("/config/node", "post");
		formNode.addInput(InputType.HIDDEN, "formPassword", node.clientCore.formPassword);
		formNode.addInput(InputType.HIDDEN, "subconfig", sc.getPrefix());
		OutputList listNode = new OutputList(Category.CONFIG);
		formNode.addChild(listNode);
		Item itemNode = listNode.addItem();
		itemNode.addInlineBox(Category.CONFIGSHORTDESC, NodeL10n.getBase().getString(o.getShortDesc())).addInput(
			InputType.TEXT, sc.getPrefix() + ".ipAddressOverride", o.getValueString());
		itemNode.addInlineBox(Category.CONFIGLONGDESC, NodeL10n.getBase().getString(o.getLongDesc()));
		formNode.addInput(InputType.SUBMIT, NodeL10n.getBase().getString("UserAlert.apply"));
		formNode.addInput(InputType.RESET, NodeL10n.getBase().getString("UserAlert.reset"));
		return textNode;
	}

	@Override
	public short getPriorityClass() {
		return UserAlert.ERROR;
	}

	@Override
	public String getShortText() {
		return l10n("unknownAddressShort");
	}

}
