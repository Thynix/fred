package freenet.clients.http.uielements;

/**
 * Creates a list item
 */
public class Item extends OutputNode {

	public Item(Category category, String content) {
		this(category);
		this.setContent(content);
	}

	public Item(String content) {
		this();
		this.setContent(content);
	}

	public Item(Category category) {
		this();
		addClass(category);
	}

	public Item() {
		super("li");
	}

}
