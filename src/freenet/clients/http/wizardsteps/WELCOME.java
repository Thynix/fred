package freenet.clients.http.wizardsteps;

import freenet.clients.http.ConfigToadlet;
import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.Box;
import freenet.clients.http.uielements.Cell;
import freenet.clients.http.uielements.Row;
import freenet.clients.http.uielements.Table;
import freenet.config.Config;
import freenet.config.EnumerableOptionCallback;
import freenet.config.Option;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * This step is the first, and provides a small welcome screen and an option to change the language.
 */
public class WELCOME implements Step {

	private final Config config;

	/**
	 * Constructs a new WELCOME GET handler.
	 * @param config Node config; cannot be null. Used to build language drop-down and change language.
	 */
	public WELCOME(Config config) {
		this.config = config;
	}
	/**
	 * Renders the first page of the wizard into the given content node.
	 * @param request used to check whether the user is using a browser with incognito mode.
	 */
	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		Box contentNode = helper.getPageContent(WizardL10n.l10n("homepageTitle"));
		boolean incognito = request.isParameterSet("incognito");

		Table optionsTable = new Table();
		contentNode.addChild(optionsTable);
		Row tableHeader = optionsTable.addRow();
		Row tableRow = optionsTable.addRow();

		//Low security option
		addSecurityTableCell(tableHeader, tableRow, "Low", helper, incognito);

		//High security option
		addSecurityTableCell(tableHeader, tableRow, "High", helper, incognito);

		//Detailed wizard option
		addSecurityTableCell(tableHeader, tableRow, "None", helper, incognito);

		HTMLNode languageForm = helper.addFormChild(contentNode, ".", "languageForm");
		//Add option dropdown for languages
		Option language = config.get("node").getOption("l10n");
		EnumerableOptionCallback l10nCallback = (EnumerableOptionCallback)language.getCallback();
		HTMLNode dropDown = ConfigToadlet.addComboBox(language.getValueString(), l10nCallback, language.getName(), false);
		//Submit automatically upon selection if Javascript.
		dropDown.addAttribute("onchange", "this.form.submit()");
		languageForm.addChild(dropDown);
		//Otherwise fall back to submit button if no Javascript
		languageForm.addChild("noscript").addInput(InputType.SUBMIT);
	}

	@Override
	public String postStep(HTTPRequest request) {
		//The user changed their language on the welcome page. Change the language and re-render the page.
		//Presets are handled within FirstTimeWizardToadlet because it can access all steps.
		String desiredLanguage = request.getPartAsStringFailsafe("l10n", 4096);
		try {
			config.get("node").set("l10n", desiredLanguage);
		} catch (freenet.config.InvalidConfigValueException e) {
			Logger.error(this, "Failed to set language to " + desiredLanguage + ". " + e);
		} catch (freenet.config.NodeNeedRestartException e) {
			//Changing language doesn't require a restart, at least as of version 1385.
			//Doing so would be really annoying as the node would have to start up again
			//which could be very slow.
		}
		return FirstTimeWizardToadlet.WIZARD_STEP.WELCOME.name();
	}

	/**
	 * Adds a table cell with information about a given security level and button.
	 * @param row Row object to add cell content to
	 * @param header Header object to add header to
	 * @param preset suffix for security level keys.
	 * @param helper used to add a form
	 * @param incognito whether incognito mode is enabled
	 */
	private void addSecurityTableCell(Row header, Row row, String preset, PageHelper helper, boolean incognito) {
		header.addHeader("33%", WizardL10n.l10n("presetTitle"+preset));
		Cell tableCell = row.addCell();
		tableCell.addBlockText( WizardL10n.l10n("preset" + preset));
		Box centerForm = tableCell.addBox();
		centerForm.addAttribute("style", "text-align:center;");
		HTMLNode secForm = helper.addFormChild(centerForm, ".", "SecForm"+preset);
		secForm.addInput(InputType.HIDDEN, "incognito", String.valueOf(incognito));
		secForm.addInput(InputType.SUBMIT, "preset" + preset, WizardL10n.l10n("presetChoose" + preset));
	}
}
