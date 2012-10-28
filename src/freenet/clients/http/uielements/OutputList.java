package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.ListType;
public class OutputList extends OutputNode {

	//Constructors
	public OutputList(ListType type, Category category) {
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
		super(ListType.UNORDERED.tagName);
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
