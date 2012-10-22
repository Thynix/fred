package freenet.clients.http.uielements;

import freenet.support.HTMLNode;

public class OutputNode extends HTMLNode {

	public OutputNode(String Name) {
		super(Name);
	}

	public OutputNode(String Name, String Content) {
		super(Name, Content);
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
	public Div addDiv(HTMLID ID) {
		Div newDiv = new Div(ID);
		addChild(newDiv);
		return newDiv;
	}
	public Div addDiv(HTMLID ID, String content) {
		Div newDiv = new Div(ID, content);
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
	public OutputList addList() {
		OutputList newList = new OutputList();
		addChild(newList);
		return newList;
	}
	public OutputList addList(HTMLClass CLASS) {
		OutputList newList = new OutputList(CLASS);
		addChild(newList);
		return newList;
	}
	public OutputList addList(HTMLID ID) {
		OutputList newList = new OutputList(ID);
		addChild(newList);
		return newList;
	}
	public OutputList addList(OutputList.Type type, HTMLClass CLASS) {
		OutputList newList = new OutputList(type, CLASS);
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
