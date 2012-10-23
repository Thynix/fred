package freenet.clients.http.uielements;

/**
 * Creates an <i> tag
 */
public class I extends OutputNode {

	@Deprecated
    public I(String content) {
		this();
		this.setContent(content);
	}

	@Deprecated
    public I() {
		super("i");
	}
}
