package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.config.Config;
import freenet.config.InvalidConfigValueException;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.support.*;
import freenet.support.api.HTTPRequest;

/**
 * Allows the user to set bandwidth limits with an emphasis on capping to a monthly total.
 */
public class BANDWIDTH_MONTHLY extends BandwidthManipulator implements Step {

	private long[] caps;
	private final long GB = 1000000000;

	public BANDWIDTH_MONTHLY(NodeClientCore core, Config config) {
		super(core, config);
		caps = new long[] { 25*GB, 50*GB, 100*GB, 150*GB };
	}

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		HTMLNode contentNode = helper.getPageContent(WizardL10n.l10n("bandwidthLimit"));

		if (request.isParameterSet("parseError")) {
			parseErrorBox(contentNode, helper, request.getParam("parseTarget"));
		}

		//Box for prettiness and explanation of function.
		HTMLNode infoBox = helper.getInfobox("infobox-normal", WizardL10n.l10n("bandwidthLimitMonthlyTitle"),
		        contentNode, null, false);
		NodeL10n.getBase().addL10nSubstitution(infoBox, "FirstTimeWizardToadlet.bandwidthLimitMonthly",
		        new String[] { "bold", "coreSettings" }, new HTMLNode[] { HTMLNode.STRONG, 
		                new HTMLNode("#", NodeL10n.getBase().getString("ConfigToadlet.node"))});

		//TODO: Might want to detect bandwidth limit and hide those too high to reach.
		//TODO: The user can always set a custom limit. At least one limit should be displayed in order to
		//TODO: demonstrate how to specify the limit, though.

		//Table header
		HTMLNode table = infoBox.addChild("table");
		HTMLNode headerRow = table.addChild("tr");
		headerRow.addChild("th", WizardL10n.l10n("bandwidthLimitMonthlyTitle"));
		headerRow.addChild("th", WizardL10n.l10n("bandwidthSelect"));

		//Row for each cap
		for (long cap : caps) {
			HTMLNode row = table.addChild("tr");
			//ISPs are likely to list limits in GB instead of GiB, so display GB here.
			row.addChild("td", String.valueOf(cap/GB)+" GB");
			HTMLNode selectForm = helper.addFormChild(row.addChild("td"), ".", "limit");
			selectForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "hidden", "capTo", String.valueOf(cap)});
			selectForm.addChild("input",
			        new String[] { "type", "value" },
			        new String[] { "submit", WizardL10n.l10n("bandwidthSelect")});
		}

		//Row for custom entry
		HTMLNode customForm = helper.addFormChild(table.addChild("tr"), ".", "custom-form");
		customForm.addChild("td").addChild("input",
			new String[]{"type", "name"},
			new String[]{"text", "capTo"});
		customForm.addChild("td").addChild("input",
			new String[]{"type", "value"},
			new String[]{"submit", WizardL10n.l10n("bandwidthSelect")});

		HTMLNode backForm = helper.addFormChild(infoBox, ".", "backForm");
		backForm.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "back", NodeL10n.getBase().getString("Toadlet.back")});
	}

	@Override
	public String postStep(HTTPRequest request)  {
		long bytesMonth;
		String capTo = request.getPartAsStringFailsafe("capTo", 4096);
		try {
			bytesMonth = Fields.parseLong(capTo);
		} catch (NumberFormatException e) {
			StringBuilder target = new StringBuilder(FirstTimeWizardToadlet.TOADLET_URL);
			target.append("?step=BANDWIDTH_MONTHLY&parseError=true&parseTarget=");
			target.append(URLEncoder.encode(capTo, true));
			return target.toString();
		}
		//Seconds in 30 days
		final double bytesSecondTotal = bytesMonth/2592000d;
		//Assume ratio between upload and download is about 1 to 1: each will use half.
		final String limit = String.valueOf(bytesSecondTotal/2);

		try {
			setBandwidthLimit(limit, false);
		} catch (InvalidConfigValueException e) {
			freenet.support.Logger.error(this, "Failed to set download limit "+limit+": "+e);
		}

		try {
			setBandwidthLimit(limit, true);
		} catch (InvalidConfigValueException e) {
			freenet.support.Logger.error(this, "Failed to set upload limit "+limit+": "+e);
		}

		setWizardComplete();

		return FirstTimeWizardToadlet.WIZARD_STEP.COMPLETE.name();
	}
}
