package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.uielements.Box;
import freenet.clients.http.uielements.InfoboxWidget;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

/**
 * Asks the user whether their connection has a monthly cap to inform how to prompt for bandwidth limits.
 */
public class BANDWIDTH implements Step {

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		InfoboxWidget bandwidthInfoboxContent = helper.getPageContent(WizardL10n.l10n("step3Title"))
			.addInfobox(
				InfoboxWidget.Type.NORMAL,
				WizardL10n.l10n("bandwidthLimit"));
		bandwidthInfoboxContent.body.addText(WizardL10n.l10n("bandwidthCapPrompt"));
		HTMLNode bandwidthForm = helper.addFormChild(bandwidthInfoboxContent.body, ".", "bwForm");
		bandwidthForm.addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "yes", NodeL10n.getBase().getString("Toadlet.yes")});
		bandwidthForm.addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "no", NodeL10n.getBase().getString("Toadlet.no")});
		bandwidthForm.addChild(new Box()).addChild("input",
			new String[]{"type", "name", "value"},
			new String[]{"submit", "back", NodeL10n.getBase().getString("Toadlet.back")});
	}

	@Override
	public String postStep(HTTPRequest request) {
		//Yes: Set for monthly data limit.
		if (request.isPartSet("yes")) {
			return FirstTimeWizardToadlet.WIZARD_STEP.BANDWIDTH_MONTHLY.name();
		}
		//No: Set for data rate limit.
		/*else if (request.isPartSet("no"))*/
		return FirstTimeWizardToadlet.WIZARD_STEP.BANDWIDTH_RATE.name();
		//Back: FirstTimeWizardToadlet handles that.
	}
}
