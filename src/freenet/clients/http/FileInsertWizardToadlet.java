package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertContext;
import freenet.client.InsertContext.CompatibilityMode;
import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.Infobox;
import freenet.clients.http.uielements.Page;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.node.SecurityLevels.NETWORK_THREAT_LEVEL;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

public class FileInsertWizardToadlet extends Toadlet implements LinkEnabledCallback {

	protected FileInsertWizardToadlet (HighLevelSimpleClient client, NodeClientCore clientCore) {
		super(client);
		this.core = clientCore;
	}

	final NodeClientCore core;
	
	// IMHO there isn't much point synchronizing these.
	private boolean rememberedLastTime;
	private boolean wasCanonicalLastTime;
	
	static final String PATH = "/insertfile/";
	
	@Override
	public String path() {
		return PATH;
	}
	
	public void reportCanonicalInsert() {
		rememberedLastTime = true;
		wasCanonicalLastTime = true;
	}
	
	public void reportRandomInsert() {
		rememberedLastTime = true;
		wasCanonicalLastTime = false;
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, final ToadletContext ctx)
		throws ToadletContextClosedException, IOException, RedirectException {
		if (container.publicGatewayMode() && ! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"),
				NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		final PageMaker pageMaker = ctx.getPageMaker();
		Page insertFilePage = pageMaker.getPage(l10n("pageTitle"), ctx);
		/* add alert summary box */
		if (ctx.isAllowedFullAccess()) {
			insertFilePage.content.addChild(core.alerts.createSummary());
		}
		insertFilePage.content.addInfobox(createInsertBox(ctx, ctx.getContainer().isAdvancedModeEnabled()));
		writeHTMLReply(ctx, 200, "OK", null, insertFilePage.generate());
	}

	private Infobox createInsertBox(ToadletContext ctx, boolean isAdvancedModeEnabled) {
		/* the insert file box */
		Infobox insertFileBox = new Infobox(Identifier.INSERTQUEUE,
			NodeL10n.getBase().getString("QueueToadlet.insertFile"));
		insertFileBox.body.addText(l10n("insertIntro"));
		NETWORK_THREAT_LEVEL seclevel = core.node.securityLevels.getNetworkThreatLevel();
		HTMLNode insertForm =
			ctx.addFormChild(insertFileBox.body, QueueToadlet.PATH_UPLOADS, "queueInsertForm");
		HTMLNode input = insertForm.addInput(InputType.RADIO, "keytype", "CHK");
		if ((! rememberedLastTime && seclevel == NETWORK_THREAT_LEVEL.LOW) ||
			(rememberedLastTime && wasCanonicalLastTime && seclevel != NETWORK_THREAT_LEVEL.MAXIMUM)) {
			input.addAttribute("checked", "checked");
		}
		insertForm.addInlineBox(Category.BOLD, l10n("insertCanonicalTitle"));
		insertForm.addText(": " + l10n("insertCanonical"));
		insertForm.addLineBreak();
		input = insertForm.addInput(InputType.RADIO, "keytype", "SSK");
		if (seclevel == NETWORK_THREAT_LEVEL.MAXIMUM || (rememberedLastTime && ! wasCanonicalLastTime)) {
			input.addAttribute("checked", "checked");
		}
		insertForm.addInlineBox(Category.BOLD, l10n("insertRandomTitle"));
		insertForm.addText(": " + l10n("insertRandom"));
		if (isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addInput(InputType.RADIO, "keytype", "specify");
			insertForm.addInlineBox(Category.BOLD, l10n("insertSpecificKeyTitle"));
			insertForm.addText(": " + l10n("insertSpecificKey") + " ");
			insertForm.addInput(InputType.TEXT, "key", "KSK@");
		}
		if (isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addLineBreak();
			insertForm.addInput(InputType.CHECKBOX, "compress", true);
			insertForm.addText(' ' +
				NodeL10n.getBase().getString("QueueToadlet.insertFileCompressLabel"));
		} else {
			insertForm.addInput(InputType.HIDDEN, "compress", "true");
		}
		if (isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.compatModeLabel") + ": ");
			HTMLNode select = insertForm.addChild("select", "name", "compatibilityMode");
			for (CompatibilityMode mode : InsertContext.CompatibilityMode.values()) {
				if (mode == CompatibilityMode.COMPAT_UNKNOWN) {
					continue;
				}
				// FIXME l10n???
				HTMLNode option = select.addChild("option", "value", mode.name(),
					NodeL10n.getBase().getString("InsertContext.CompatibilityMode." + mode.name
						()));
				if (mode == CompatibilityMode.COMPAT_CURRENT) {
					option.addAttribute("selected", "");
				}
			}
			insertForm.addLineBreak();
			insertForm.addText(l10n("splitfileCryptoKeyLabel") + ": ");
			insertForm.addInput(InputType.TEXT, "overrideSplitfileKey", (short) 64);
		}
		insertForm.addLineBreak();
		insertForm.addLineBreak();
		// Local file browser
		if (ctx.isAllowedFullAccess()) {
			insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseLabel") + ": ");
			insertForm.addInput(InputType.SUBMIT, "insert-local",
					NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseButton") + "...");
			insertForm.addLineBreak();
		}
		insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileLabel") + ": ");
		insertForm.addInput(InputType.FILE, "filename", "");
		insertForm.addText(" \u00a0 ");
		insertForm.addInput(InputType.SUBMIT, "insert",
			NodeL10n.getBase().getString("QueueToadlet.insertFileInsertFileLabel"));
		insertForm.addText(" \u00a0 ");
		return insertFileBox;
	}

	String l10n (String key) {
		return NodeL10n.getBase().getString("FileInsertWizardToadlet."+key);
	}
	
	String l10n (String key, String pattern, String value) {
		return NodeL10n.getBase().getString("FileInsertWizardToadlet."+key, pattern, value);
	}

	@Override
	public boolean isEnabled (ToadletContext ctx) {
		return (!container.publicGatewayMode()) || ((ctx != null) && ctx.isAllowedFullAccess());
	}
}
