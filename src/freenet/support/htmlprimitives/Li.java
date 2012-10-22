package freenet.support.htmlprimitives;

/**
 * Creates a list item
 */
public class Li extends OutputNode {

	public Li(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Li(String content) {
		this();
		this.setContent(content);
	}

	public Li(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Li() {
		super("li");
	}

}
