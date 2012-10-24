package freenet.clients.http.uielements;

/**
 * Creates a block text element
 */
public class BlockText extends OutputNode {

	public BlockText(Identifier id, String content) {
		this(id);
		this.setContent(content);
	}

	public BlockText(Identifier id) {
		this();
		setID(id);
	}

	public BlockText(Category category, String content) {
		this(category);
		this.setContent(content);
	}

	public BlockText(Category category) {
		this();
		addClass(category);
	}

	public BlockText(String content) {
		this();
		this.setContent(content);
	}

	public BlockText() {
		super("p");
	}
}
