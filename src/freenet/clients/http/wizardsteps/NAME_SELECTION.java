package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.Infobox;
import freenet.config.Config;
import freenet.config.ConfigException;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * Allows the user to choose a node name for Darknet.
 */
public class NAME_SELECTION implements Step {

	private final Config config;

	public NAME_SELECTION(Config config) {
		this.config = config;
	}

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		Infobox nnameInfoboxContent = helper.getPageContent(WizardL10n.l10n("step2Title")).addInfobox(
			InfoboxType.NORMAL,
			WizardL10n.l10n("chooseNodeName"));

		nnameInfoboxContent.body.addText(WizardL10n.l10n("chooseNodeNameLong"));
		HTMLNode nnameForm = helper.addFormChild(nnameInfoboxContent.body, ".", "nnameForm");
		nnameForm.addInput("nname");

		HTMLNode lineBelow = nnameForm.addBox();
		lineBelow.addInput(InputType.SUBMIT, "back", NodeL10n.getBase().getString("Toadlet.back"));
		lineBelow.addInput(InputType.SUBMIT, "next", NodeL10n.getBase().getString("Toadlet.next"));
	}

	@Override
	public String postStep(HTTPRequest request) {
		String selectedNName = request.getPartAsStringFailsafe("nname", 128);

		//Prompt again when provided with a blank node name.
		if (selectedNName.isEmpty()) {
			return FirstTimeWizardToadlet.WIZARD_STEP.NAME_SELECTION.name();
		}

		try {
			config.get("node").set("name", selectedNName);
			Logger.normal(this, "The node name has been set to " + selectedNName);
		} catch (ConfigException e) {
			Logger.error(this, "Should not happen, please report!" + e, e);
		}
		return FirstTimeWizardToadlet.WIZARD_STEP.DATASTORE_SIZE.name();
	}
}
