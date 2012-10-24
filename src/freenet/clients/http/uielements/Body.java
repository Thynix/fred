package freenet.clients.http.uielements;

/**
 * Creates a body element
 */
public class Body extends OutputNode {

	public Body(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Body() {
		super("body");
	}
}
