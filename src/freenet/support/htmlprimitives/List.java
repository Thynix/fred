package freenet.support.htmlprimitives;

public class List extends OutputNode {

	public enum Type {
		ORDERED("ol"),
		UNORDERED("ul");

		public final String tagName;

		private Type(String type) {
			this.tagName = type;
		}
	}

	//Constructors
	public List(Type type, HTMLClass CLASS) {
		super(type.tagName);
		addClass(CLASS);
	}

	public List(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public List() {
		super(Type.UNORDERED.tagName);
	}

	//Methods for adding list items
	public ListItem addListItem(HTMLClass CLASS, String content) {
		ListItem newListItem = new ListItem(CLASS, content);
		addChild(newListItem);
		return newListItem;
	}
	public ListItem addListItem(HTMLClass CLASS) {
		ListItem newListItem = new ListItem(CLASS);
		addChild(newListItem);
		return newListItem;
	}
	public ListItem addListItem() {
		ListItem newListItem = new ListItem();
		addChild(newListItem);
		return newListItem;
	}
}

