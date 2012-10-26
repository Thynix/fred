package freenet.clients.http.wizardsteps;

import freenet.clients.http.FirstTimeWizardToadlet;
import freenet.clients.http.uielements.Box;
import freenet.clients.http.uielements.Infobox;
import freenet.clients.http.uielements.Input;
import freenet.config.Config;
import freenet.config.ConfigException;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.api.HTTPRequest;

/**
 * Allows the user to choose whether to enable auto-updating, and what official utility plugins to install.
 */
public class MISC implements Step {

	private final Config config;
	private final NodeClientCore core;

	public MISC(NodeClientCore core, Config config) {
		this.core = core;
		this.config = config;
	}

	@Override
	public void getStep(HTTPRequest request, PageHelper helper) {
		HTMLNode form = helper.addFormChild(helper.getPageContent(WizardL10n.l10n("stepMiscTitle")), ".", "miscForm");

		Infobox autoUpdate = form.addInfobox(Infobox.Type.NORMAL, WizardL10n.l10n("autoUpdate"));
		Box miscInfoboxContent = autoUpdate.body;

		miscInfoboxContent.addBlockText(WizardL10n.l10n("autoUpdateLong"));
		miscInfoboxContent.addText().addInput(Input.Type.RADIO, "autodeploy", "true", true).setContent(WizardL10n.l10n("autoUpdateAutodeploy"));
		miscInfoboxContent.addBlockText().addInput(Input.Type.RADIO, "autodeploy", "false").setContent(WizardL10n.l10n("autoUpdateNoAutodeploy"));

		Infobox plugins = form.addInfobox(Infobox.Type.NORMAL, WizardL10n.l10n("plugins"));
		miscInfoboxContent = plugins.body;

		miscInfoboxContent.addBlockText(WizardL10n.l10n("pluginsLong"));
		miscInfoboxContent.addBlockText().addInput(Input.Type.CHECKBOX, "upnp", "true", true).setContent(WizardL10n.l10n("enableUPnP"));
		miscInfoboxContent.addInput(Input.Type.SUBMIT, "back", NodeL10n.getBase().getString("Toadlet.back"));
		miscInfoboxContent.addInput(Input.Type.SUBMIT, "next", NodeL10n.getBase().getString("Toadlet.next"));
	}

	@Override
	public String postStep(HTTPRequest request) {
		setAutoUpdate(Boolean.parseBoolean(request.getPartAsStringFailsafe("autodeploy", 10)));
		setUPnP(request.isPartSet("upnp"));
		return FirstTimeWizardToadlet.WIZARD_STEP.OPENNET.name();
	}

	/**
	 * Sets whether auto-update should be enabled.
	 * @param enabled whether auto-update should be enabled.
	 */
	public void setAutoUpdate(boolean enabled) {
		try {
			config.get("node.updater").set("autoupdate", enabled);
		} catch (ConfigException e) {
			Logger.error(this, "Should not happen, please report!" + e, e);
		}
	}

	/**
	 * Enables or disables the UPnP plugin asynchronously. If the plugin's state would not change for the given
	 * argument, it does nothing.
	 * @param enableUPnP whether UPnP should be enabled.
	 */
	public void setUPnP(final boolean enableUPnP) {
		//If its state would not change, don't do anything.
		if(enableUPnP == core.node.pluginManager.isPluginLoaded("plugins.UPnP.UPnP")) {
				return;
		}

		core.node.executor.execute(new Runnable() {

			private final boolean enable = enableUPnP;

			@Override
			public void run() {
				if(enable) {
					core.node.pluginManager.startPluginOfficial("UPnP", true, false, false);
				} else {
					core.node.pluginManager.killPluginByClass("plugins.UPnP.UPnP", 5000);
				}
			}

		});
	}
}
