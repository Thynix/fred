package freenet.clients.http.uielements;

/**
 * Creates a list item
 */
public class Item extends OutputNode {

	public Item(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Item(String content) {
		this();
		this.setContent(content);
	}

	public Item(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Item() {
		super("li");
	}

}
