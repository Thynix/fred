package freenet.clients.http.uielements;

import freenet.l10n.NodeL10n;

public class Page extends Doctype {

	public Markup Root;
	public Box Content;

	public Page(String Title) {
		super("html", "-//W3C//DTD XHTML 1.1//EN");
		addHTML(Title, NodeL10n.getBase().getSelectedLanguage().isoCode);
		Root.body.addClass(HTMLClass.FPROXYPAGE);
		Content = Root.body.addBox(HTMLID.PAGE);
	}

	private void addHTML(String Title, String Lang) {
		Root = new Markup(Title, Lang);
		this.addChild(Root);
	}
}
