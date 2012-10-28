package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
import freenet.l10n.NodeL10n;

public class Page extends Doctype {

	public Markup root;
	public Box page;
	public Box content;

	public Page(String title) {
		super("html", "-//W3C//DTD XHTML 1.1//EN");
		addHTML(title, NodeL10n.getBase().getSelectedLanguage().isoCode);
		root.body.addClass(Category.FPROXYPAGE);
		page = new Box(Identifier.PAGE);
		page = root.body.addBox(page);
	}

	private void addHTML(String title, String lang) {
		root = new Markup(title, lang);
		this.addChild(root);
	}

	/**
	 * Creates a content node as a child of the page node. Do not call this method until everything that must be
	 * inserted above the content node is in place.
	 *
	 * @return content node for the page
	 */
	public Box addContent() {
		content = new Box(Identifier.CONTENT);
		page.addChild(content);
		return content;
	}
}
