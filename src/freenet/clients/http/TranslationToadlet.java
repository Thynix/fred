/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.uielements.*;
import freenet.l10n.BaseL10n;
import freenet.l10n.NodeL10n;
import freenet.l10n.PluginL10n;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.FredPluginBaseL10n;
import freenet.pluginmanager.PluginInfoWrapper;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.MultiValueTable;
import freenet.support.SimpleFieldSet;
import freenet.support.SimpleFieldSet.KeyIterator;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;

import java.io.IOException;
import java.net.URI;
import java.util.Vector;

/**
 * A toadlet dedicated to translations ... and easing the work of translators
 * 
 * @author Florent Daigni&egrave;re &lt;nextgens@freenetproject.org&gt;
 * @author Artefact2
 */
public class TranslationToadlet extends Toadlet {
	public static final String TOADLET_URL = "/translation/";
	private final NodeClientCore core;
	private BaseL10n base;
	private String translatingFor;

	TranslationToadlet(HighLevelSimpleClient client, NodeClientCore core) {
		super(client);
		this.core = core;
		this.base = NodeL10n.getBase();
		this.translatingFor = "Node";
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		boolean showEverything = !request.isParameterSet("toTranslateOnly");
		
		
		if (request.isParameterSet("getOverrideTranlationFile")) {
			SimpleFieldSet sfs = this.base.getOverrideForCurrentLanguageTranslation();
			if(sfs == null) {
				super.sendErrorPage(ctx, 503 /* Service Unavailable */, "Service Unavailable", l10n("noCustomTranslations"));
				return;
			}
			byte[] data = sfs.toOrderedString().getBytes("UTF-8");
			MultiValueTable<String, String> head = new MultiValueTable<String, String>();
			head.put("Content-Disposition", "attachment; filename=\"" + this.base.getL10nOverrideFileName(this.base.getSelectedLanguage()) + '"');
			ctx.sendReplyHeaders(200, "Found", head, "text/plain; charset=utf-8", data.length);
			ctx.writeData(data);
			return;
		} else if (request.isParameterSet("translation_updated")) {
			String key = request.getParam("translation_updated");
			PageNode page = ctx.getPageMaker().getPageNode(l10n("translationUpdatedTitle"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;

			Box translationNode = new Box(Category.TRANSLATION);
			contentNode.addChild(translationNode);
			Table legendTable = translationNode.addTable(Category.TRANSLATION);
			
			Row legendRow = legendTable.addRow();
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("translationKeyLabel"));
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("originalVersionLabel"));
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("currentTranslationLabel"));
			
			Row contentRow = legendTable.addRow();
			contentRow.addCell(Category.TRANSLATIONKEY, key);
			contentRow.addCell(Category.TRANSLATIONORIG, this.base.getDefaultString(key));
			contentRow.addCell(Category.TRANSLATIONNEW, this.base.getString(key));
			
			Box footer = translationNode.addBox(Category.WARNING);
			footer.addLink(TOADLET_URL + "?getOverrideTranlationFile").addText(l10n("downloadTranslationsFile"));
			footer.addChild("%", "&nbsp;&nbsp;");
			footer.addLink(TOADLET_URL + "?translate=" + key + (showEverything ? "" : "&toTranslateOnly")).addText(l10n("reEdit"));
			footer.addChild("%", "&nbsp;&nbsp;");
			footer.addLink(TOADLET_URL + (showEverything ? "" : "?toTranslateOnly")).addText(l10n("returnToTranslations"));

			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		} else if (request.isParameterSet("translate")) {
			boolean gotoNext = request.isParameterSet("gotoNext");
			String key = request.getParam("translate");
			PageNode page = ctx.getPageMaker().getPageNode(l10n("translationUpdateTitle"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;

			HTMLNode translationNode = contentNode.addChild(new Box(Category.TRANSLATION));
			HTMLNode updateForm =  ctx.addFormChild(translationNode, TOADLET_URL, "trans_update");
			Table legendTable = new Table(Category.TRANSLATION);
			updateForm.addChild(legendTable);
			
			Row legendRow = legendTable.addRow();
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("translationKeyLabel"));
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("originalVersionLabel"));
			legendRow.addCell(Category.TRANSLATIONKEY, l10n("currentTranslationLabel"));
			
			Row contentRow = legendTable.addRow();
			contentRow.addCell(Category.TRANSLATIONKEY, key);
			contentRow.addCell(Category.TRANSLATIONORIG, this.base.getDefaultString(key));
			
			contentRow.addCell(Category.TRANSLATIONNEW).addChild(
					"textarea",
					new String[] { "name", "rows", "cols" },
					new String[] { "trans", "20", "80" },
					this.base.getString(key));
			
			contentRow.addChild("input", 
					new String[] { "type", "name", "value" }, 
					new String[] { "hidden", "key", key
			});

			updateForm.addChild("input", 
					new String[] { "type", "name", "value" }, 
					new String[] { "submit", "translation_update", l10n("updateTranslationCommand")
			});
			updateForm.addChild("input", new String[] { "type", "name" , (gotoNext ? "checked" : "unchecked") } , new String[] { "checkbox", "gotoNext", ""}, l10n("gotoNext"));
			if(!showEverything)
				updateForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "toTranslateOnly", key });
			
			updateForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "cancel", NodeL10n.getBase().getString("Toadlet.cancel") });
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		} else if (request.isParameterSet("remove")) {
			String key = request.getParam("remove");
			PageNode page = ctx.getPageMaker().getPageNode(l10n("removeOverrideTitle"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			InfoboxWidget ConfirmRemove =
				new InfoboxWidget(InfoboxWidget.Type.WARNING, Identifier.TRANSLATIONOVERRIDE,
					l10n("removeOverrideWarningTitle"));
			contentNode.addInfobox(ConfirmRemove);
			ConfirmRemove.body.addBlockText(NodeL10n.getBase()
				.getString("TranslationToadlet.confirmRemoveOverride", new String[]{"key", "value"},
					new String[]{key, this.base.getString(key)}));
			HTMLNode removeForm =
				ctx.addFormChild(ConfirmRemove.body.addChild(new BlockText()), TOADLET_URL,
					"remove_confirmed");
			if (! showEverything) {
				removeForm.addChild("input", new String[]{"type", "name", "value"},
					new String[]{"hidden", "toTranslateOnly", key});
			}
			removeForm.addChild("input", new String[]{"type", "name", "value"},
				new String[]{"hidden", "remove_confirm", key});
			removeForm.addChild("input", new String[]{"type", "name", "value"},
				new String[]{"submit", "remove_confirmed", l10n("remove")});
			removeForm.addChild("input", new String[]{"type", "name", "value"},
				new String[]{"submit", "cancel", NodeL10n.getBase().getString("Toadlet.cancel")});
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
		PageNode page = ctx.getPageMaker().getPageNode(l10n("translationUpdateTitle"), ctx);
		HTMLNode pageNode = page.outer;
		HTMLNode contentNode = page.content;
		InfoboxWidget SelectTranslation = new InfoboxWidget(l10n("selectTranslation"));
		contentNode.addInfobox(SelectTranslation);
		Vector<String> elementsToTranslate = new Vector<String>();
		elementsToTranslate.add("Node");
		for (PluginInfoWrapper pluginInfo : this.core.node.pluginManager.getPlugins()) {
			if (! pluginInfo.isBaseL10nPlugin()) {
				continue;
			}
			elementsToTranslate.add(pluginInfo.getPluginClassName());
		}
		final HTMLNode translatingForForm =
			ctx.addFormChild(SelectTranslation.body, TOADLET_URL, "ChooseWhatToTranslate")
				.addChild(new BlockText(l10n("showTranslationOf")));
		final HTMLNode translatingForOption = translatingForForm.addChild("select", "name",
			"translating_for");
		for (String element : elementsToTranslate) {
			final HTMLNode option = translatingForOption.addChild("option", "name", element, element);
			if (element.equals(this.translatingFor)) {
				option.addAttribute("selected", "selected");
			}
		}
		translatingForForm.addChild("input", "type", "submit");
		Box translationNode = new Box(Category.TRANSLATION);
		contentNode.addChild(translationNode);
		BlockText translationHeaderNode = translationNode.addBlockText();
		translationHeaderNode
			.addText(l10n("contributingToLabelWithLang", "lang", this.base.getSelectedLanguage()
				.fullName));
		translationHeaderNode.addLink(TOADLET_URL + "?getOverrideTranlationFile")
			.addText(l10n("downloadTranslationsFile"));
		translationHeaderNode.addText(" ");
		if (showEverything) {
			translationHeaderNode.addLink(TOADLET_URL + "?toTranslateOnly")
				.addText(l10n("hideAlreadyTranslated"));
		} else {
			translationHeaderNode.addLink(TOADLET_URL).addText(l10n("showEverything"));
		}
		Table legendTable = translationNode.addTable(Category.TRANSLATION);

		Row legendRow = legendTable.addRow();
		legendRow.addCell(Category.TRANSLATIONKEY, l10n("translationKeyLabel"));
		legendRow.addCell(Category.TRANSLATIONKEY, l10n("originalVersionLabel"));
		legendRow.addCell(Category.TRANSLATIONKEY, l10n("currentTranslationLabel"));
		KeyIterator it = this.base.getDefaultLanguageTranslation().keyIterator("");
		while (it.hasNext()) {
			String key = it.nextKey();
			boolean isOverriden = this.base.isOverridden(key);
			if (! showEverything && (isOverriden || (this.base.getString(key, true) != null))) {
				continue;
			}
			Row contentRow = legendTable.addRow();
			contentRow.addCell(Category.TRANSLATIONKEY, key);
			contentRow.addCell(Category.TRANSLATIONORIG, this.base.getDefaultString(key));
			contentRow.addCell(Category.TRANSLATIONNEW)
				.addChild(_setOrRemoveOverride(key, isOverriden, showEverything));
		}
		this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		final boolean logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
		final String passwd = request.getPartAsStringFailsafe("formPassword", 32);
		boolean noPassword = (passwd == null) || !passwd.equals(core.formPassword);
		if(noPassword) {
			if(logMINOR) Logger.minor(this, "No password ("+passwd+" should be "+core.formPassword+ ')');
			redirectTo(ctx, "/");
			return;
		}

		if(request.isPartSet("translating_for")) {
			final String translateFor = request.getPartAsStringFailsafe("translating_for", 255);

			for(PluginInfoWrapper pluginInfo : this.core.node.pluginManager.getPlugins()) {
				if(translateFor.equals(pluginInfo.getPluginClassName()) && pluginInfo.isBaseL10nPlugin()) {
					FredPluginBaseL10n plugin = (FredPluginBaseL10n) pluginInfo.getPlugin();
					this.translatingFor = translateFor;
					this.base = new PluginL10n(plugin).getBase();
				}
			}

			if(translateFor.equals("Node")) {
				this.translatingFor = "Node";
				this.base = NodeL10n.getBase();
			}

			redirectTo(ctx, TOADLET_URL);
			return;
		}
		
		boolean toTranslateOnly = request.isPartSet("toTranslateOnly");
		
		if(request.getPartAsStringFailsafe("translation_update", 32).length() > 0){
			String key = request.getPartAsStringFailsafe("key", 256);
			this.base.setOverride(key, new String(BucketTools.toByteArray(request.getPart("trans")), "UTF-8").trim());
			
			if("on".equalsIgnoreCase(request.getPartAsStringFailsafe("gotoNext", 7))) {
				KeyIterator it = base.getDefaultLanguageTranslation().keyIterator("");
				
				while(it.hasNext()) {
					String newKey = it.nextKey();
					boolean isOverriden = this.base.isOverridden(newKey);
					if(isOverriden || (this.base.getString(newKey, true) != null))
						continue;
					redirectTo(ctx, TOADLET_URL+"?gotoNext&translate="+newKey+ (toTranslateOnly ? "&toTranslateOnly" : ""));
					return;
				}
			}
			
			redirectTo(ctx, TOADLET_URL+"?translation_updated="+key+ (toTranslateOnly ? "&toTranslateOnly" : ""));
			return;
		} else if(request.getPartAsStringFailsafe("remove_confirmed", 32).length() > 0) {
			String key = request.getPartAsStringFailsafe("remove_confirm", 256).trim();
			this.base.setOverride(key, "");
			
			redirectTo(ctx, TOADLET_URL+"?translation_updated="+key+ (toTranslateOnly ? "&toTranslateOnly" : ""));
			return;
		}else // Shouldn't reach that point!
			redirectTo(ctx, "/");
	}
	
	private void redirectTo(ToadletContext ctx, String target) throws ToadletContextClosedException, IOException {
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", target);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
		return;
	}

	private HTMLNode _setOrRemoveOverride(String key, boolean isOverriden, boolean showEverything) {
		String value = this.base.getString(key, true);
		
		InlineBox translationField = new InlineBox(isOverriden ? Category.TRANSLATED : Category.TRANSLATEIT);
		if(value == null) {
			translationField.addText(this.base.getDefaultString(key));
			translationField.addLink(TranslationToadlet.TOADLET_URL + "?translate=" + key + (showEverything ? "" : "&toTranslateOnly")).addChild("small", l10n("bracketTranslateIt"));
		} else {
			translationField.addText(this.base.getString(key));
			translationField.addLink(TranslationToadlet.TOADLET_URL + "?translate=" + key + (showEverything ? "" : "&toTranslateOnly")).addChild("small", l10n("bracketUpdateTranslation"));
			if(isOverriden)
				translationField.addLink(TranslationToadlet.TOADLET_URL + "?remove=" + key + (showEverything ? "" : "&toTranslateOnly")).addChild("small", l10n("bracketRemoveOverride"));
		}
		
		return translationField;
	}
	
	private String l10n(String key) {
		return NodeL10n.getBase().getString("TranslationToadlet."+key);
	}
	
	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("TranslationToadlet."+key, new String[] { pattern }, new String[] { value });
	}

	@Override
	public String path() {
		return TOADLET_URL;
	}
}
