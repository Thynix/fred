package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.node.SecurityLevels;
import freenet.support.Fields;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * This step allows the user to choose between security levels. If opennet is disabled, only high and maximum are shown.
 * If opennet is enabled, only low and normal are shown.
 */
public class SECURITY_NETWORK implements Step {

	private final NodeClientCore core;

	public SECURITY_NETWORK(NodeClientCore core) {
		this.core = core;
	}

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		Box contentNode = helper.getPageContent(WizardL10n.l10n("networkSecurityPageTitle"));
		String opennetParam = request.getParam("opennet", "false");
		boolean opennet = Fields.stringToBool(opennetParam, false);

		if (request.isParameterSet("confirm")) {
			String networkThreatLevel = request.getParam("security-levels.networkThreatLevel");
			SecurityLevels.NETWORK_THREAT_LEVEL newThreatLevel = SecurityLevels.parseNetworkThreatLevel(networkThreatLevel);

			InfoboxWidget networkThreadLevel = contentNode.addInfobox(InfoboxWidget.Type.INFORMATION,
			        WizardL10n.l10n("networkThreatLevelConfirmTitle."+newThreatLevel));

			HTMLNode formNode = helper.addFormChild(networkThreadLevel.body, ".", "configFormSecLevels");
			formNode.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "hidden", "security-levels.networkThreatLevel", networkThreatLevel });
			if(newThreatLevel == SecurityLevels.NETWORK_THREAT_LEVEL.MAXIMUM) {
				HTMLNode p = formNode.addChild(new BlockText());
				NodeL10n.getBase().addL10nSubstitution(p, "SecurityLevels.maximumNetworkThreatLevelWarning",
				        new String[] { "bold" },
				        new HTMLNode[] { HTMLNode.STRONG });
				p.addText(" ");
				NodeL10n.getBase().addL10nSubstitution(p, "SecurityLevels.maxSecurityYouNeedFriends",
				        new String[] { "bold" },
				        new HTMLNode[] { HTMLNode.STRONG });
				formNode.addChild(new BlockText()).addChild("input",
					new String[]{"type", "name", "value"},
					new String[]{"checkbox", "security-levels.networkThreatLevel.confirm", "off"},
					WizardL10n.l10nSec("maximumNetworkThreatLevelCheckbox"));
			} else /*if(newThreatLevel == NETWORK_THREAT_LEVEL.HIGH)*/ {
				HTMLNode p = formNode.addChild(new BlockText());
				NodeL10n.getBase().addL10nSubstitution(p, "FirstTimeWizardToadlet.highNetworkThreatLevelWarning",
				        new String[] { "bold", "addAFriend", "friends" },
				        new HTMLNode[] { HTMLNode.STRONG,
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.addFriendTitle")),
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.categoryFriends"))});
				HTMLNode checkbox = formNode.addChild(new BlockText()).addChild("input",
					new String[]{"type", "name", "value"},
					new String[]{"checkbox", "security-levels.networkThreatLevel.confirm", "off"});
				NodeL10n.getBase().addL10nSubstitution(checkbox,
				        "FirstTimeWizardToadlet.highNetworkThreatLevelCheckbox",
				        new String[] { "bold", "addAFriend" },
				        new HTMLNode[] { HTMLNode.STRONG,
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.addFriendTitle")),});
			}
			formNode.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "hidden", "security-levels.networkThreatLevel.tryConfirm", "on" });
			formNode.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "submit", "return-from-confirm", NodeL10n.getBase().getString("Toadlet.back")});
			formNode.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "submit", "next", NodeL10n.getBase().getString("Toadlet.next")});
			return;
		}

		//Add choices and description depending on whether opennet was selected.
		HTMLNode form;
		if(opennet) {
			InfoboxWidget Opennet = contentNode.addInfobox(InfoboxWidget.Type.NORMAL,
			        WizardL10n.l10n("networkThreatLevelHeaderOpennet"));
			Opennet.body.addChild(new BlockText(WizardL10n.l10n("networkThreatLevelIntroOpennet")));

			form = helper.addFormChild(Opennet.body, ".", "networkSecurityForm");
			HTMLNode div = form.addChild(new Box(Category.OPENNETDIV));
			for(SecurityLevels.NETWORK_THREAT_LEVEL level : SecurityLevels.NETWORK_THREAT_LEVEL.OPENNET_VALUES) {
				securityLevelChoice(div, level);
			}
		} else {
			InfoboxWidget Darknet = contentNode.addInfobox(InfoboxWidget.Type.NORMAL,
			        WizardL10n.l10n("networkThreatLevelHeaderDarknet"));
			Darknet.body.addChild(new BlockText(WizardL10n.l10n("networkThreatLevelIntroDarknet")));

			form = helper.addFormChild(Darknet.body, ".", "networkSecurityForm");
			HTMLNode div = form.addChild(new Box(Category.DARKNETDIV));
			for(SecurityLevels.NETWORK_THREAT_LEVEL level : SecurityLevels.NETWORK_THREAT_LEVEL.DARKNET_VALUES) {
				securityLevelChoice(div, level);
			}
			form.addChild(new BlockText()).addB(WizardL10n.l10nSec("networkThreatLevel.opennetFriendsWarning"));
		}
		form.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "back", NodeL10n.getBase().getString("Toadlet.back")});
		form.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "next", NodeL10n.getBase().getString("Toadlet.next")});
	}

	/**
	 * Adds to the given parent node description and a radio button for the selected security level.
	 * @param parent to add content to.
	 * @param level to add content about.
	 */
	private void securityLevelChoice(HTMLNode parent, SecurityLevels.NETWORK_THREAT_LEVEL level) {
		HTMLNode input = parent.addChild(new BlockText()).addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"radio", "security-levels.networkThreatLevel", level.name()});
		input.addB(WizardL10n.l10nSec("networkThreatLevel.name." + level));
		input.addText(": ");
		NodeL10n.getBase().addL10nSubstitution(input, "SecurityLevels.networkThreatLevel.choice."+level,
		        new String[] { "bold" },
		        new HTMLNode[] { HTMLNode.STRONG });
		HTMLNode inner = input.addChild(new BlockText()).addI();
		NodeL10n.getBase().addL10nSubstitution(inner, "SecurityLevels.networkThreatLevel.desc."+level,
		        new String[] { "bold" },
		        new HTMLNode[] { HTMLNode.STRONG });
	}

	@Override
	public String postStep(HTTPRequest request) {
		String networkThreatLevel = request.getPartAsStringFailsafe("security-levels.networkThreatLevel", 128);
		SecurityLevels.NETWORK_THREAT_LEVEL newThreatLevel = SecurityLevels.parseNetworkThreatLevel(networkThreatLevel);

		//Used in case of redirect either for retry or confirmation.
		StringBuilder redirectTo = new StringBuilder(FirstTimeWizardToadlet.WIZARD_STEP.SECURITY_NETWORK.name());

		/*If the user didn't select a network security level before clicking continue or the selected
		* security level could not be determined, redirect to the same page.*/
		if(newThreatLevel == null || !request.isPartSet("security-levels.networkThreatLevel")) {
			return redirectTo.toString();
		}

		PersistFields persistFields = new PersistFields(request);
		boolean isInPreset = persistFields.isUsingPreset();
		if (request.isPartSet("return-from-confirm")) {
			//User clicked back from a confirmation page
			if (isInPreset) {
				//In a preset, go back a step
				return FirstTimeWizardToadlet.getPreviousStep(
				        FirstTimeWizardToadlet.WIZARD_STEP.SECURITY_NETWORK, persistFields.preset).name();
			}

			//Not in a preset, redisplay level choice.
			return FirstTimeWizardToadlet.WIZARD_STEP.SECURITY_NETWORK.name();
		}
		if((newThreatLevel == SecurityLevels.NETWORK_THREAT_LEVEL.MAXIMUM || newThreatLevel == SecurityLevels.NETWORK_THREAT_LEVEL.HIGH)) {
			//Make the user aware of the effects of high or maximum network threat if selected.
			//They must check a box acknowledging its affects to proceed.
			if((!request.isPartSet("security-levels.networkThreatLevel.confirm")) &&
			        (!request.isPartSet("security-levels.networkThreatLevel.tryConfirm"))) {
				displayConfirmationBox(redirectTo, networkThreatLevel);
				return redirectTo.toString();
			} else if((!request.isPartSet("security-levels.networkThreatLevel.confirm")) &&
				        request.isPartSet("security-levels.networkThreatLevel.tryConfirm")) {
				//If the user did not check the box and clicked next, redisplay the prompt.
				displayConfirmationBox(redirectTo, networkThreatLevel);
				return redirectTo.toString();
			}
		}
		//The user selected low or normal security, or confirmed high or maximum. Set the configuration
		//and continue to the physical security step.
		setThreatLevel(newThreatLevel);
		return FirstTimeWizardToadlet.WIZARD_STEP.SECURITY_PHYSICAL.name();
	}

	private void displayConfirmationBox(StringBuilder redirectTo, String networkThreatLevel) {
		redirectTo.append("&confirm=true&security-levels.networkThreatLevel=").append(networkThreatLevel);
	}

	public void setThreatLevel(SecurityLevels.NETWORK_THREAT_LEVEL level) {
		core.node.securityLevels.setThreatLevel(level);
		core.storeConfig();
	}
}
