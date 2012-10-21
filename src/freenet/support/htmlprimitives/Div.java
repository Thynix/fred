package freenet.support.htmlprimitives;

/**
 * Creates a block element
 */
public class Div extends OutputNode {

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
