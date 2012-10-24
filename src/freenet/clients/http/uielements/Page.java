package freenet.clients.http.uielements;

import freenet.clients.http.PageMaker;
import freenet.clients.http.ToadletContext;
import freenet.l10n.NodeL10n;

public class Page extends Doctype {

	public ToadletContext Context;
	public Markup Root;
	public Box Content;

	Page(String Title, ToadletContext context, PageMaker.RenderParameters renderParameters) {
		super("html", "-//W3C//DTD XHTML 1.1//EN");
		addHTML(Title + " - Freenet", NodeL10n.getBase().getSelectedLanguage().isoCode);
		Root.head.addMeta("Content-Type", "text/html; charset=utf-8");
		Root.head.addChild("noscript").addChild("style",".jsonly {display:none;}");
		Root.head.addLink(getStylesheet());
		Root.body.addClass(HTMLClass.FPROXYPAGE);
		Content = Root.body.addBox();
		Context = context;
	}

	private void addHTML(String Title, String Lang) {
		Root = new Markup(Title, Lang);
		this.addChild(Root);
	}


	private Link getStylesheet() {
		Link StyleSheet = new Link();
		return StyleSheet;
	}
}
