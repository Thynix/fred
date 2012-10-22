package freenet.clients.http.uielements;

public class OutputList extends OutputNode {

	public enum Type {
		ORDERED("ol"),
		UNORDERED("ul");

		public final String tagName;

		private Type(String type) {
			this.tagName = type;
		}
	}

	//Constructors
	public OutputList(Type type, HTMLClass CLASS) {
		super(type.tagName);
		addClass(CLASS);
	}

	public OutputList(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public OutputList(HTMLID ID) {
		this();
		setID(ID);
	}

	public OutputList() {
		super(Type.UNORDERED.tagName);
	}

	//Methods for adding list items
	public Item addItem(HTMLClass CLASS, String content) {
		Item newListItem = new Item(CLASS, content);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem(String content) {
		Item newListItem = new Item(content);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem(HTMLClass CLASS) {
		Item newListItem = new Item(CLASS);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem() {
		Item newListItem = new Item();
		addChild(newListItem);
		return newListItem;
	}
}
