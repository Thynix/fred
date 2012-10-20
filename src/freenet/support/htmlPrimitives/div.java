package freenet.support.htmlPrimitives;

import freenet.support.HTMLNode;

public class div extends HTMLNode {

	public div(HTMLCLASS CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public div(HTMLCLASS CLASS) {
		this();
		addClass(CLASS);
	}

	public div() {
		super("div");
	}
}
