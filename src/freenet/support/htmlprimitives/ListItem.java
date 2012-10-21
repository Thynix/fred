package freenet.support.htmlprimitives;

/**
 * Creates a list item
 */
public class ListItem extends OutputNode {

	public ListItem(HTMLClass CLASS, String content) {
		this(CLASS);
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
