package freenet.clients.http.uielements;

/**
 * Creates a block element
 */
public class Div extends OutputNode {

	public Div(HTMLID ID, String content) {
		this(ID);
		this.setContent(content);
	}

	public Div(HTMLID ID) {
		this();
		setID(ID);
	}

	public Div(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Div(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Div() {
		super("div");
	}
}
