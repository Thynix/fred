package freenet.support.htmlprimitives;

import freenet.support.HTMLNode;
import freenet.support.uielements.InfoboxWidget;

public class OutputNode extends HTMLNode {
	OutputNode(String Name) {
		super(Name);
	}

	public OutputNode addChild(OutputNode childNode) {
		if(this.readOnly)
			throw new IllegalArgumentException("Read only");
		if (childNode == null) throw new NullPointerException();
		//since an efficient algorithm to check the loop presence
		//is not present, at least it checks if we are trying to
		//addChild the node itself as a child
		if (childNode == this)
			throw new IllegalArgumentException("A HTMLNode cannot be child of himself");
		if (children.contains(childNode))
			throw new IllegalArgumentException("Cannot add twice the same HTMLNode as child");
		children.add(childNode);
		return childNode;
	}

	//methods for creating Div children
	public Div addDiv() {
		Div newDiv = new Div();
		addChild(newDiv);
		return newDiv;
	}
	public Div addDiv(HTMLClass CLASS) {
		Div newDiv = new Div(CLASS);
		addChild(newDiv);
		return newDiv;
	}
	public Div addDiv(HTMLClass CLASS, String content) {
		Div newDiv = new Div(CLASS, content);
		addChild(newDiv);
		return newDiv;
	}

	//methods for creating List children
	public List addList() {
		List newList = new List();
		addChild(newList);
		return newList;
	}
	public List addList(HTMLClass CLASS) {
		List newList = new List(CLASS);
		addChild(newList);
		return newList;
	}
	public Div addList(HTMLClass CLASS, String content) {
		Div newList = new Div();
		addChild(newList);
		return newList;
	}

	//methods for creating Infobox children
	public InfoboxWidget addInfobox(InfoboxWidget.Type type, String title) {
		InfoboxWidget newInfobox = new InfoboxWidget(type, title);
		addChild(newInfobox);
		return newInfobox;
	}
}
