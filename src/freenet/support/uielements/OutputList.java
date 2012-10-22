package freenet.support.uielements;

import freenet.support.htmlprimitives.HTMLClass;
import freenet.support.htmlprimitives.HTMLID;
import freenet.support.htmlprimitives.Li;
import freenet.support.htmlprimitives.OutputNode;

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
	public Li addItem(HTMLClass CLASS, String content) {
		Li newListItem = new Li(CLASS, content);
		addChild(newListItem);
		return newListItem;
	}
	public Li addItem(String content) {
		Li newListItem = new Li(content);
		addChild(newListItem);
		return newListItem;
	}
	public Li addItem(HTMLClass CLASS) {
		Li newListItem = new Li(CLASS);
		addChild(newListItem);
		return newListItem;
	}
	public Li addItem() {
		Li newListItem = new Li();
		addChild(newListItem);
		return newListItem;
	}
}

