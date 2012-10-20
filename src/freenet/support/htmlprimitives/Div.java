package freenet.support.htmlprimitives;

import freenet.support.HTMLNode;

public class Div extends HTMLNode {

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
