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
		Div newDiv = new Div();
		addChild(newDiv);
		return new Div(CLASS);
	}
	public Div addDiv(HTMLClass CLASS, String content) {
		Div newDiv = new Div();
		addChild(newDiv);
		return new Div(CLASS, content);
	}

	//methods for creating Infobox children
	public InfoboxWidget addInfobox(InfoboxWidget.Type type, String title) {
		InfoboxWidget newInfobox = new InfoboxWidget(type, title);
		addChild(newInfobox);
		return newInfobox;
	}
}
