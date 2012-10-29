package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.Box;
import freenet.clients.http.uielements.Infobox;
import freenet.clients.http.uielements.Input;
import freenet.clients.http.uielements.Text;
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

			Infobox networkThreadLevel = contentNode.addInfobox(InfoboxType.INFORMATION,
			        WizardL10n.l10n("networkThreatLevelConfirmTitle."+newThreatLevel));

			HTMLNode formNode = helper.addFormChild(networkThreadLevel.body, ".", "configFormSecLevels");
			formNode.addInput(InputType.HIDDEN, "security-levels.networkThreatLevel", networkThreatLevel);
			if(newThreatLevel == SecurityLevels.NETWORK_THREAT_LEVEL.MAXIMUM) {
				HTMLNode p = formNode.addBlockText();
				NodeL10n.getBase().addL10nSubstitution(p, "SecurityLevels.maximumNetworkThreatLevelWarning",
				        new String[] { "bold" },
				        new HTMLNode[] { HTMLNode.STRONG });
				p.addText(" ");
				NodeL10n.getBase().addL10nSubstitution(p, "SecurityLevels.maxSecurityYouNeedFriends",
				        new String[] { "bold" },
				        new HTMLNode[] { HTMLNode.STRONG });
				formNode.addBlockText().addInput(InputType.CHECKBOX, "security-levels.networkThreatLevel.confirm", "off").setContent(WizardL10n.l10nSec("maximumNetworkThreatLevelCheckbox"));
			} else /*if(newThreatLevel == NETWORK_THREAT_LEVEL.HIGH)*/ {
				HTMLNode p = formNode.addBlockText();
				NodeL10n.getBase().addL10nSubstitution(p, "FirstTimeWizardToadlet.highNetworkThreatLevelWarning",
				        new String[] { "bold", "addAFriend", "friends" },
				        new HTMLNode[] { HTMLNode.STRONG,
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.addFriendTitle")),
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.categoryFriends"))});
				HTMLNode checkbox = formNode.addBlockText().addInput(InputType.CHECKBOX, "security-levels.networkThreatLevel.confirm", "off");
				NodeL10n.getBase().addL10nSubstitution(checkbox,
				        "FirstTimeWizardToadlet.highNetworkThreatLevelCheckbox",
				        new String[] { "bold", "addAFriend" },
				        new HTMLNode[] { HTMLNode.STRONG,
				                new Text(NodeL10n.getBase().getString("FProxyToadlet.addFriendTitle")),});
			}
			formNode.addInput(InputType.HIDDEN, "security-levels.networkThreatLevel.tryConfirm", "on");
			formNode.addInput(InputType.SUBMIT, "return-from-confirm", NodeL10n.getBase().getString("Toadlet.back"));
			formNode.addInput(InputType.SUBMIT, "next", NodeL10n.getBase().getString("Toadlet.next"));
			return;
		}

		//Add choices and description depending on whether opennet was selected.
		HTMLNode form;
		if(opennet) {
			Infobox Opennet = contentNode.addInfobox(InfoboxType.NORMAL,
			        WizardL10n.l10n("networkThreatLevelHeaderOpennet"));
			Opennet.body.addBlockText(WizardL10n.l10n("networkThreatLevelIntroOpennet"));

			form = helper.addFormChild(Opennet.body, ".", "networkSecurityForm");
			HTMLNode div = form.addBox(Category.OPENNETDIV);
			for(SecurityLevels.NETWORK_THREAT_LEVEL level : SecurityLevels.NETWORK_THREAT_LEVEL.OPENNET_VALUES) {
				securityLevelChoice(div, level);
			}
		} else {
			Infobox darknet = contentNode.addInfobox(InfoboxType.NORMAL,
			        WizardL10n.l10n("networkThreatLevelHeaderDarknet"));
			darknet.body.addBlockText(WizardL10n.l10n("networkThreatLevelIntroDarknet"));

			form = helper.addFormChild(darknet.body, ".", "networkSecurityForm");
			HTMLNode div = form.addBox(Category.DARKNETDIV);
			for(SecurityLevels.NETWORK_THREAT_LEVEL level : SecurityLevels.NETWORK_THREAT_LEVEL.DARKNET_VALUES) {
				securityLevelChoice(div, level);
			}
			form.addBlockText(Category.BOLD, WizardL10n.l10nSec("networkThreatLevel.opennetFriendsWarning"));
		}
		form.addInput(InputType.SUBMIT, "back", NodeL10n.getBase().getString("Toadlet.back"));
		form.addInput(InputType.SUBMIT, "next", NodeL10n.getBase().getString("Toadlet.next"));
	}

	/**
	 * Adds to the given parent node description and a radio button for the selected security level.
	 * @param parent to add content to.
	 * @param level to add content about.
	 */
	private void securityLevelChoice(HTMLNode parent, SecurityLevels.NETWORK_THREAT_LEVEL level) {
		Input input = parent.addBlockText().addInput(InputType.RADIO, "security-levels.networkThreatLevel", level.name());
		input.addInlineBox(Category.BOLD, WizardL10n.l10nSec("networkThreatLevel.name." + level));
		input.addText(": ");
		NodeL10n.getBase().addL10nSubstitution(input, "SecurityLevels.networkThreatLevel.choice."+level,
		        new String[] { "bold" },
		        new HTMLNode[] { HTMLNode.STRONG });
		HTMLNode inner = input.addBlockText(Category.ITALIC);
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
