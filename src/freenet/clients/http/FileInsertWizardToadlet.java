package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertContext;
import freenet.client.InsertContext.CompatibilityMode;
import freenet.clients.http.uielements.Identifier;
import freenet.clients.http.uielements.InfoboxWidget;
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
	
	public void handleMethodGET (URI uri, final HTTPRequest request, final ToadletContext ctx)
	        throws ToadletContextClosedException, IOException, RedirectException {

//		// We ensure that we have a FCP server running
//		if(!fcp.enabled){
//			writeError(NodeL10n.getBase().getString("QueueToadlet.fcpIsMissing"), NodeL10n.getBase().getString("QueueToadlet.pleaseEnableFCP"), ctx, false);
//			return;
//		}
//		if(!core.hasLoadedQueue()) {
//			writeError(NodeL10n.getBase().getString("QueueToadlet.notLoadedYetTitle"), NodeL10n.getBase().getString("QueueToadlet.notLoadedYet"), ctx, false);
//			return;
//		}

		if (container.publicGatewayMode() && !ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"),
			        NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		final PageMaker pageMaker = ctx.getPageMaker();

		PageNode page = pageMaker.getPageNode(l10n("pageTitle"), ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;

		/* add alert summary box */
		if (ctx.isAllowedFullAccess()) contentNode.addChild(core.alerts.createSummary());

		contentNode.addChild(createInsertBox(pageMaker, ctx, ctx.getContainer().isAdvancedModeEnabled()));
		
		writeHTMLReply(ctx, 200, "OK", null, pageNode.generate());
	}
	
	private HTMLNode createInsertBox (PageMaker pageMaker, ToadletContext ctx, boolean isAdvancedModeEnabled) {
		/* the insert file box */
		InfoboxWidget InsertFileBox = new InfoboxWidget(Identifier.INSERTQUEUE, NodeL10n.getBase().getString("QueueToadlet.insertFile"));
		InsertFileBox.body.addText(l10n("insertIntro"));
		NETWORK_THREAT_LEVEL seclevel = core.node.securityLevels.getNetworkThreatLevel();
		HTMLNode insertForm = ctx.addFormChild(InsertFileBox.body, QueueToadlet.PATH_UPLOADS, "queueInsertForm");
		HTMLNode input = insertForm.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "radio", "keytype", "CHK" });
		if ((!rememberedLastTime && seclevel == NETWORK_THREAT_LEVEL.LOW) ||
		        (rememberedLastTime && wasCanonicalLastTime && seclevel != NETWORK_THREAT_LEVEL.MAXIMUM)) {
			input.addAttribute("checked", "checked");
		}
		insertForm.addB(l10n("insertCanonicalTitle"));
		insertForm.addText(": " + l10n("insertCanonical"));
		insertForm.addLineBreak();
		input = insertForm.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "radio", "keytype", "SSK" });
		if (seclevel == NETWORK_THREAT_LEVEL.MAXIMUM || (rememberedLastTime && !wasCanonicalLastTime)) {
			input.addAttribute("checked", "checked");
		}
		insertForm.addB(l10n("insertRandomTitle"));
		insertForm.addText(": " + l10n("insertRandom"));
		if (isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "radio", "keytype", "specify" });
			insertForm.addB(l10n("insertSpecificKeyTitle"));
			insertForm.addText(": " + l10n("insertSpecificKey") + " ");
			insertForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "text", "key", "KSK@" });
		}
		if (isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addLineBreak();
			insertForm.addChild("input",
			        new String[] { "type", "name", "checked" },
			        new String[] { "checkbox", "compress", "checked" });
			insertForm.addText(' ' +
				NodeL10n.getBase().getString("QueueToadlet.insertFileCompressLabel"));
		} else {
			insertForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "hidden", "compress", "true" });
		}
		if(isAdvancedModeEnabled) {
			insertForm.addLineBreak();
			insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.compatModeLabel") + ": ");
			HTMLNode select = insertForm.addChild("select", "name", "compatibilityMode");
			for(CompatibilityMode mode : InsertContext.CompatibilityMode.values()) {
				if(mode == CompatibilityMode.COMPAT_UNKNOWN) continue;
				// FIXME l10n???
				HTMLNode option = select.addChild("option", "value", mode.name(),
				        NodeL10n.getBase().getString("InsertContext.CompatibilityMode."+mode.name()));
				if (mode == CompatibilityMode.COMPAT_CURRENT) option.addAttribute("selected", "");
			}
			insertForm.addLineBreak();
			insertForm.addText(l10n("splitfileCryptoKeyLabel") + ": ");
			insertForm.addChild("input",
			        new String[] { "type", "name", "maxlength" },
			        new String[] { "text", "overrideSplitfileKey", "64" });
		}
		insertForm.addLineBreak();
		insertForm.addLineBreak();
		// Local file browser
		if (ctx.isAllowedFullAccess()) {
			insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseLabel") + ": ");
			insertForm.addChild("input",
			        new String[] { "type", "name", "value" },
			        new String[] { "submit", "insert-local",
			                NodeL10n.getBase().getString("QueueToadlet.insertFileBrowseButton") + "..." });
			insertForm.addLineBreak();
		}
		insertForm.addText(NodeL10n.getBase().getString("QueueToadlet.insertFileLabel") + ": ");
		insertForm.addChild("input",
		        new String[] { "type", "name", "value" },
		        new String[] { "file", "filename", "" });
		insertForm.addText(" \u00a0 ");
		insertForm.addChild("input", 
		        new String[] { "type", "name", "value" },
		        new String[] { "submit", "insert",
		                NodeL10n.getBase().getString("QueueToadlet.insertFileInsertFileLabel") });
		insertForm.addText(" \u00a0 ");
		return InsertFileBox;
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
