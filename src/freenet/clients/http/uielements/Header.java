package freenet.clients.http.uielements;

/**
 * Creates table header cells
 */
public class Header extends OutputNode {

	public Header(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Header(String content) {
		this();
		this.setContent(content);
	}

	public Header(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Header() {
		super("th");
	}
}