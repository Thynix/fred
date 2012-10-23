package freenet.clients.http.uielements;

/**
 * Creates an <b> tag
 */
public class B extends OutputNode {

	@Deprecated
    public B(String content) {
		this();
		this.setContent(content);
	}

	@Deprecated
    public B() {
		super("b");
	}
}
