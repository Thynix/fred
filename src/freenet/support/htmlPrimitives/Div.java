package freenet.support.htmlPrimitives;

import freenet.support.HTMLNode;

public class Div extends HTMLNode {

	public Div(HTMLCLASS CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Div(HTMLCLASS CLASS) {
		this();
		addClass(CLASS);
	}

	public Div() {
		super("div");
	}
}
