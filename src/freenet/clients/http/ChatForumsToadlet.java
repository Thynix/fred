package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.useralerts.UserAlertManager;
import freenet.pluginmanager.PluginManager;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

public class ChatForumsToadlet extends Toadlet implements LinkEnabledCallback {

	private final UserAlertManager alerts;
	private final PluginManager plugins;
	private final Node node;
	
	protected ChatForumsToadlet(HighLevelSimpleClient client, UserAlertManager alerts, PluginManager plugins, Node node) {
		super(client);
		this.alerts = alerts;
		this.plugins = plugins;
		this.node = node;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		Page chatForum = ctx.getPageMaker().getPage(l10n("title"), ctx);
		chatForum.content.addChild(alerts.createSummary());
		Infobox chatList = chatForum.content
			.addInfobox(InfoboxType.INFORMATION, Identifier.CHATLIST, l10n("title"));
		chatList.body.addBlockText(l10n("freetalkRecommended"));
		chatList.body.addBlockText(l10n("freetalkCaveat"));
		ctx.addFormChild(chatList.body, path(), "loadFreetalkButton")
			.addInput(InputType.SUBMIT, "loadFreetalk", l10n("freetalkButton"));
		chatList.body.addBlockText(l10n("othersIntro"));
		OutputList chatPluginList = new OutputList();
		chatList.body.addChild(chatPluginList);
		Item chatPlugin = chatPluginList.addItem();
		chatPlugin.addLink("/" + Site.FMS.key, NodeL10n.getBase().getString("ChatForumsToadlet.fmsname"));
		chatPlugin.addText(NodeL10n.getBase().getString("ChatForumsToadlet.fmsdescription"));
		chatPlugin = chatPluginList.addItem();
		NodeL10n.getBase().addL10nSubstitution(chatPlugin, "ChatForumsToadlet.frost",
			new String[]{"frost-freenet", "frost-web", "frost-help"},
			new HTMLNode[]{
				new Link("/" + Site.FROST.key),
				new Link(ExternalLinkToadlet.escape(Site.FROST.url)),
				new Link("/" + Site.FROST.helpKey)});
		chatPlugin = chatPluginList.addItem();
		NodeL10n.getBase().addL10nSubstitution(chatPlugin, "ChatForumsToadlet.sone",
			new String[]{"sone"},
			new HTMLNode[]{new Link("/" + Site.SONE.key)});
		chatList.body.addBlockText(l10n("content2"));
		this.writeHTMLReply(ctx, 200, "OK", chatForum.generate());
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		
		// FIXME we should really refactor this boilerplate stuff out somehow...
		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		
		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if((pass == null) || !pass.equals(node.clientCore.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}
		
		if(request.isPartSet("loadFreetalk")) {
			node.executor.execute(new Runnable() {

				@Override
				public void run() {
					if(!node.pluginManager.isPluginLoaded("plugins.WebOfTrust.WebOfTrust")) {
						node.pluginManager.startPluginOfficial("WebOfTrust", true, false, false);
					}
				}
			});
			node.executor.execute(new Runnable() {

				@Override
				public void run() {
					if(!node.pluginManager.isPluginLoaded("plugins.Freetalk.Freetalk")) {
						node.pluginManager.startPluginOfficial("Freetalk", true, false, false);
					}
				}
			});
			try {
				// Wait a little to ensure we have at least started loading them.
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// Ignore
			}
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", PproxyToadlet.PATH);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
		} else {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", path());
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
		}
	}

	private static String l10n(String string) {
		return NodeL10n.getBase().getString("ChatForumsToadlet." + string);
	}

	@Override
	public String path() {
		return Path.CHAT.url;
	}

	@Override
	public boolean isEnabled(ToadletContext ctx) {
		return !plugins.isPluginLoaded("plugins.Freetalk.Freetalk");
	}

	
}
