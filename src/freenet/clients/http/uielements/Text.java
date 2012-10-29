package freenet.clients.http.uielements;

/**
 * Creates an inline text element
 * Don't try to apply HTML attributes to this.
 */
public class Text extends OutputNode {

	public Text(long content) {
		this();
		this.setContent(Long.toString(content));
	}

	public Text(short content) {
		this();
		this.setContent(Short.toString(content));
	}

	public Text(int content) {
		this();
		this.setContent(Integer.toString(content));
	}

	public Text(String content) {
		this();
		this.setContent(content);
	}

	public Text() {
		super("#");
	}
}
