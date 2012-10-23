package freenet.clients.http.uielements;

/**
 * Creates a block element
 */
public class Box extends OutputNode {

	public Box(HTMLID ID, String content) {
		this(ID);
		this.setContent(content);
	}

	public Box(HTMLID ID) {
		this();
		setID(ID);
	}

	public Box(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Box(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Box() {
		super("div");
	}
}
