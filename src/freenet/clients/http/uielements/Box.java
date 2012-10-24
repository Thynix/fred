package freenet.clients.http.uielements;

/**
 * Creates a block element
 */
public class Box extends OutputNode {

	public Box(Identifier id, String content) {
		this(id);
		this.setContent(content);
	}

	public Box(Identifier id) {
		this();
		setID(id);
	}

	public Box(Category category, String content) {
		this(category);
		this.setContent(content);
	}

	public Box(Category category) {
		this();
		addClass(category);
	}

	public Box() {
		super("div");
	}
}
