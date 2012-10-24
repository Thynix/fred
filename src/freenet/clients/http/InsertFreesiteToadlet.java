package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
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
		InfoboxWidget InsertFreesite =
			new InfoboxWidget(InfoboxWidget.Type.INFORMATION, Identifier.FREESITEINSERT, l10n("title"));
		page.content.addInfobox(InsertFreesite);
		InsertFreesite.body.addBlockText(l10n("content1"));
		NodeL10n.getBase().addL10nSubstitution(InsertFreesite.body.addChild(new BlockText()),
			"InsertFreesiteToadlet.contentFlogHelper", new String[]{"plugins"},
			new HTMLNode[]{new Link(PproxyToadlet.PATH)});
		NodeL10n.getBase().addL10nSubstitution(InsertFreesite.body.addChild(new BlockText()),
			"InsertFreesiteToadlet.content2",
			new String[]{"jsite-http", "jsite-freenet", "jsite-freenet-version", "jsite-info"},
			new HTMLNode[]{new Link(
				ExternalLinkToadlet.escape("http://downloads.freenetproject.org/alpha/jSite/")),
				new Link(
					"/CHK@2gVK8i-oJ9bqmXOZfkRN1hqgveSUrOdzSxtkndMbLu8," +
						"OPKeK9ySG7RcKXadzNN4npe8KSDb9EbGXSiH1Me~6rQ,AAIC--8/jSite.jar"),
				new Text("0.6.2"), new Link(
				"/SSK@ugb~uuscsidMI-Ze8laZe~o3BUIb3S50i25RIwDH99M," +
					"9T20t3xoG-dQfMO94LGOl9AxRTkaz~TykFY-voqaTQI,AQACAAE/FAFS-49/files/jsite" +
					".htm"),
			});
		InsertFreesite.body.addBlockText(l10n("content3"));
		OutputList uploadTutorialList = new OutputList();
		InsertFreesite.body.addList(uploadTutorialList);
		Item uploadTutorial = uploadTutorialList.addItem();
		uploadTutorial.addLink(
			"/SSK@940RYvj1-aowEHGsb5HeMTigq8gnV14pbKNsIvUO~-0," +
				"FdTbR3gIz21QNfDtnK~MiWgAf2kfwHe-cpyJXuLHdOE,AQACAAE/publish-3/",
			"Publish!");
		uploadTutorial.addText(" - " + l10n("publishExplanation"));
		uploadTutorial = uploadTutorialList.addItem();
		uploadTutorial.addLink(
			"/SSK@8r-uSRcJPkAr-3v3YJR16OCx~lyV2XOKsiG4MOQQBMM," +
				"P42IgNemestUdaI7T6z3Og6P-Hi7g9U~e37R3kWGVj8,AQACAAE/freesite-HOWTO-4/",
			"Freesite HOWTO");
		uploadTutorial.addText(" - " + l10n("freesiteHowtoExplanation"));
		NodeL10n.getBase().addL10nSubstitution(InsertFreesite.body.addChild(new BlockText()),
			"InsertFreesiteToadlet.contentThingamablog",
			new String[]{"thingamablog", "thingamablog-freenet"},
			new HTMLNode[]{new Link(ExternalLinkToadlet
				.escape("http://downloads.freenetproject.org/alpha/thingamablog/thingamablog.zip")),
				new Link(
					"/CHK@o8j9T2Ghc9cfKMLvv9aLrHbvW5XiAMEGwGDqH2UANTk," +
						"sVxLdxoNL-UAsvrlXRZtI5KyKlp0zv3Ysk4EcO627V0," +
						"AAIC--8/thingamablog.zip")});
		this.writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	private static String l10n(String string) {
		return NodeL10n.getBase().getString("InsertFreesiteToadlet." + string);
	}

	@Override
	public String path() {
		return "/insertsite/";
	}
}
