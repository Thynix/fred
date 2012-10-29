/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.useralerts;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.constants.Path;
import freenet.clients.http.uielements.*;
import freenet.config.Option;
import freenet.config.SubConfig;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.support.HTMLNode;

public class IPUndetectedUserAlert extends AbstractUserAlert {
	
	public IPUndetectedUserAlert(Node n) {
		super(true, null, null, null, null, (short) 0, true, NodeL10n.getBase().getString("UserAlert.hide"), false, null);
		this.node = n;
	}
	
	final Node node;
	
	@Override
	public String getTitle() {
		return l10n("unknownAddressTitle");
	}

	@Override
	public String getText() {
		if(node.ipDetector.noDetectPlugins())
			return l10n("noDetectorPlugins");
		if(node.ipDetector.isDetecting())
			return l10n("detecting");
		else
			return l10n("unknownAddress", "port", Integer.toString(node.getDarknetPortNumber())) + ' ' + textPortForwardSuggestion();
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("IPUndetectedUserAlert."+key);
	}

	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("IPUndetectedUserAlert."+key, pattern, value);
	}

	private String l10n(String key, String[] patterns, String[] values) {
		return NodeL10n.getBase().getString("IPUndetectedUserAlert."+key, patterns, values);
	}
	
	@Override
	public boolean isValid() {
		if(node.isOpennetEnabled())
			return false;
		if(node.peers.countConnectiblePeers() >= 5 && (node.getUptime() < 60*1000 || node.ipDetector.isDetecting()))
			return false;
		return true;
	}

	@Override
	public HTMLNode getHTMLText() {
		Box textNode = new Box();
		SubConfig sc = node.config.get("node");
		Option<?> o = sc.getOption("tempIPAddressHint");
		
		NodeL10n.getBase().addL10nSubstitution(textNode, "IPUndetectedUserAlert."+(node.ipDetector.isDetecting() ? "detectingWithConfigLink" : "unknownAddressWithConfigLink"), 
				new String[] { "link" },
				new HTMLNode[] { new Link("/config/"+sc.getPrefix()) });
		
		int peers = node.peers.getDarknetPeers().length;
		if(peers > 0)
			textNode.addBlockText( l10n("noIPMaybeFromPeers", "number", Integer.toString(peers)));
		
		if(node.ipDetector.noDetectPlugins()) {
			HTMLNode p = textNode.addBlockText();
			NodeL10n.getBase().addL10nSubstitution(p, "IPUndetectedUserAlert.loadDetectPlugins", new String[] { "plugins", "config", },
					new HTMLNode[] { new Link(Path.PLUGINS.url), new Link("/config/node") });
		} else if(!node.ipDetector.hasJSTUN() && !node.ipDetector.isDetecting()) {
			HTMLNode p = textNode.addBlockText();
			NodeL10n.getBase().addL10nSubstitution(p, "IPUndetectedUserAlert.loadJSTUN", new String[] { "plugins" },
					new HTMLNode[] { new Link(Path.PLUGINS.url) });
		}
		
		addPortForwardSuggestion(textNode);
		
		Form formNode = textNode.addForm("/config/"+sc.getPrefix(), "post");
		formNode.addInput(InputType.HIDDEN, "formPassword", node.clientCore.formPassword);
		formNode.addInput(InputType.HIDDEN, "subconfig", sc.getPrefix());
		OutputList listNode =formNode.addList(Category.CONFIG);
		Item itemNode = listNode.addItem();
		itemNode.addInlineBox(Category.CONFIGSHORTDESC, NodeL10n.getBase().getString(o.getShortDesc())).addInput(
			InputType.TEXT, sc.getPrefix() + ".tempIPAddressHint", o.getValueString());
		itemNode.addInlineBox(Category.CONFIGLONGDESC, NodeL10n.getBase().getString(o.getLongDesc()));
		formNode.addInput(InputType.SUBMIT, NodeL10n.getBase().getString("UserAlert.apply"));
		formNode.addInput(InputType.RESET, NodeL10n.getBase().getString("UserAlert.reset"));
		
		return textNode;
	}

	private void addPortForwardSuggestion(HTMLNode textNode) {
		// FIXME we should support any number of ports, UDP or TCP, and pick them up from the node as we do with the forwarding plugin ... that would be a bit of a pain for L10n though ...
		int darknetPort = node.getDarknetPortNumber();
		int opennetPort = node.getOpennetFNPPort();
		if(opennetPort <= 0) {
			textNode.addText(" " + l10n("suggestForwardPort", "port", Integer.toString(darknetPort)));
		} else {
			textNode.addText(" " + l10n("suggestForwardTwoPorts", new String[]{"port1", "port2"},
				new String[]{Integer.toString(darknetPort), Integer.toString(opennetPort)}));
		}
	}

	private String textPortForwardSuggestion() {
		// FIXME we should support any number of ports, UDP or TCP, and pick them up from the node as we do with the forwarding plugin ... that would be a bit of a pain for L10n though ...
		int darknetPort = node.getDarknetPortNumber();
		int opennetPort = node.getOpennetFNPPort();
		if(opennetPort <= 0) {
			return l10n("suggestForwardPort", "port", Integer.toString(darknetPort));
		} else {
			return " "+l10n("suggestForwardTwoPorts", new String[] { "port1", "port2" }, 
					new String[] { Integer.toString(darknetPort), Integer.toString(opennetPort) });
		}
	}

	@Override
	public short getPriorityClass() {
		if(node.ipDetector.isDetecting())
			return UserAlert.WARNING;
		else
			return UserAlert.ERROR;
	}

	@Override
	public String getShortText() {
		if(node.ipDetector.noDetectPlugins())
			return l10n("noDetectorPlugins");
		if(node.ipDetector.isDetecting())
			return l10n("detectingShort");
		else
			return l10n("unknownAddressShort");
	}

}
