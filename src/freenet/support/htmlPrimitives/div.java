package freenet.support.htmlPrimitives;

import freenet.support.HTMLNode;

public class div extends HTMLNode {

	public div(String ID, String CLASS) {
		this(ID);
		addClass(CLASS);
	}

	public div(String ID) {
		this();
		setID(ID);
	}

	public div() {
		super("div");
	}
}
