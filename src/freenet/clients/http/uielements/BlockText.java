package freenet.clients.http.uielements;

/**
 * Creates a block text element
 */
public class BlockText extends OutputNode {

	public BlockText(HTMLID ID, String content) {
		this(ID);
		this.setContent(content);
	}

	public BlockText(HTMLID ID) {
		this();
		setID(ID);
	}

	public BlockText(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public BlockText(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public BlockText(String content) {
		this();
		this.setContent(content);
	}

	public BlockText() {
		super("p");
	}
}
