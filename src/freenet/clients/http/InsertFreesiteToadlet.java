package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.constants.Path;
import freenet.clients.http.constants.Site;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.useralerts.UserAlertManager;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

/** This is just documentation, it will be replaced with a plugin wizard eventually. */
public class InsertFreesiteToadlet extends Toadlet {

	private final UserAlertManager alerts;
	
	protected InsertFreesiteToadlet(HighLevelSimpleClient client, UserAlertManager alerts) {
		super(client);
		this.alerts = alerts;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		Page page = ctx.getPageMaker().getPage(l10n("title"), ctx);
		page.content.addChild(alerts.createSummary());
		Infobox insertFreesite =
			new Infobox(InfoboxType.INFORMATION, Identifier.FREESITEINSERT, l10n("title"));
		page.content.addInfobox(insertFreesite);
		insertFreesite.body.addBlockText(l10n("content1"));
		NodeL10n.getBase().addL10nSubstitution(insertFreesite.body.addBlockText(),
			"InsertFreesiteToadlet.contentFlogHelper", new String[]{"plugins"},
			new HTMLNode[]{new Link(PproxyToadlet.PATH)});
		NodeL10n.getBase().addL10nSubstitution(insertFreesite.body.addBlockText(),
			"InsertFreesiteToadlet.content2",
			new String[]{"jsite-http", "jsite-freenet", "jsite-freenet-version", "jsite-info"},
			new HTMLNode[]{new Link(
				ExternalLinkToadlet.escape(Site.JSITE.url)),
				new Link("/" + Site.JSITE.key),
				new Text("0.6.2"),
				new Link("/" + Site.JSITE.helpKey),
			});
		insertFreesite.body.addBlockText(l10n("content3"));
		OutputList uploadTutorialList = new OutputList();
		insertFreesite.body.addList(uploadTutorialList);
		Item uploadTutorial = uploadTutorialList.addItem();
		uploadTutorial.addLink("/" + Site.PUBLISH.key, "Publish!");
		uploadTutorial.addText(" - " + l10n("publishExplanation"));
		uploadTutorial = uploadTutorialList.addItem();
		uploadTutorial.addLink("/" + Site.FREESITEHOWTO.key,"Freesite HOWTO");
		uploadTutorial.addText(" - " + l10n("freesiteHowtoExplanation"));
		NodeL10n.getBase().addL10nSubstitution(insertFreesite.body.addBlockText(),
			"InsertFreesiteToadlet.contentThingamablog",
			new String[]{"thingamablog", "thingamablog-freenet"},
			new HTMLNode[]{new Link(ExternalLinkToadlet
				.escape(Site.THINGAMABLOG.url)),
				new Link("/" + Site.THINGAMABLOG.key)});
		this.writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	private static String l10n(String string) {
		return NodeL10n.getBase().getString("InsertFreesiteToadlet." + string);
	}

	@Override
	public String path() {
		return Path.INSERT.url;
	}
}
