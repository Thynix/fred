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
	public OutputList(Type type, Category category) {
		super(type.tagName);
		addClass(category);
	}

	public OutputList(Category category) {
		this();
		addClass(category);
	}

	public OutputList(Identifier id) {
		this();
		setID(id);
	}

	public OutputList() {
		super(Type.UNORDERED.tagName);
	}

	//Methods for adding list items
	public Item addItem(Category category, String content) {
		Item newListItem = new Item(category, content);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem(String content) {
		Item newListItem = new Item(content);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem(Category category) {
		Item newListItem = new Item(category);
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem(Item newListItem) {
		addChild(newListItem);
		return newListItem;
	}
	public Item addItem() {
		Item newListItem = new Item();
		addChild(newListItem);
		return newListItem;
	}
}
