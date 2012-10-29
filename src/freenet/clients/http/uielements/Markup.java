package freenet.clients.http.uielements;

/**
 * Creates an html element
 */
public class Markup extends OutputNode {

	public Head head;
	public Body body;
	public String lang;

	public Markup(String title, String lang) {
		super("html");
		setLang(lang);
		addHead(title);
		addBody();
	}

	private void setLang(String lang) {
		this.lang = lang;
		this.addAttribute("xml:lang", lang);
	}

	private void addBody() {
		body = new Body();
		this.addChild(body);
	}

	private void addHead(String title) {
		head = new Head(title);
		this.addChild(head);
	}
}
