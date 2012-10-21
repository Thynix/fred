package freenet.support.htmlprimitives;

/**
 * Creates a list item
 */
public class ListItem extends OutputNode {

	public ListItem(String content) {
		this();
		this.setContent(content);
	}

	public ListItem(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public ListItem() {
		super("li");
	}

}
