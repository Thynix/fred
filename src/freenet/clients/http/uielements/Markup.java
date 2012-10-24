package freenet.clients.http.uielements;

/**
 * Creates an html element
 */
public class Markup extends OutputNode {

	public Head head;
	public Body body;
	public String Lang;

	public Markup(String Title, String Lang) {
		super("html");
		setLang(Lang);
		addHead(Title);
		addBody();
	}

	private void setLang(String Lang) {
		this.Lang = Lang;
		this.addAttribute("xml:lang", Lang);
	}

	private void addBody() {
		body = new Body();
		this.addChild(body);
	}

	private void addHead(String Title) {
		head = new Head(Title);
		this.addChild(head);
	}
}
