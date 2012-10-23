package freenet.clients.http.uielements;

import freenet.support.HTMLNode;

public class OutputNode extends HTMLNode {

	public OutputNode(String Name) {
		super(Name);
	}

	public OutputNode(String Name, String Content) {
		super(Name, Content);
	}

	public OutputNode addChild(String name, String attribute, String value, String content) {
		OutputNode childNode = new OutputNode(name, content);
		childNode.addAttribute(attribute, value);
		this.addChild(childNode);
		return childNode;
	}

	public OutputNode addChild(OutputNode childNode) {
		if (this.readOnly)
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

	//methods for creating Box children
	public Box addDiv() {
		Box newBox = new Box();
		addChild(newBox);
		return newBox;
	}
	public Box addDiv(HTMLID ID) {
		Box newBox = new Box(ID);
		addChild(newBox);
		return newBox;
	}
	public Box addDiv(HTMLID ID, String content) {
		Box newBox = new Box(ID, content);
		addChild(newBox);
		return newBox;
	}
	public Box addDiv(HTMLClass CLASS) {
		Box newBox = new Box(CLASS);
		addChild(newBox);
		return newBox;
	}
	public Box addDiv(HTMLClass CLASS, String content) {
		Box newBox = new Box(CLASS, content);
		addChild(newBox);
		return newBox;
	}

	//methods for creating blockText children
	public BlockText addBlockText() {
		BlockText newBlockText = new BlockText();
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(String content) {
		BlockText newBlockText = new BlockText(content);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(HTMLID ID) {
		BlockText newBlockText = new BlockText(ID);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(HTMLID ID, String content) {
		BlockText newBlockText = new BlockText(ID, content);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(HTMLClass CLASS) {
		BlockText newBlockText = new BlockText(CLASS);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(HTMLClass CLASS, String content) {
		BlockText newBlockText = new BlockText(CLASS, content);
		addChild(newBlockText);
		return newBlockText;
	}

	//methods for creating InlineBox children
	public InlineBox addInlineBox() {
		InlineBox newInlineBox = new InlineBox();
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String content) {
		InlineBox newInlineBox = new InlineBox(content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(HTMLID ID) {
		InlineBox newInlineBox = new InlineBox(ID);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(HTMLID ID, String content) {
		InlineBox newInlineBox = new InlineBox(ID, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String title, HTMLClass CLASS) {
		InlineBox newInlineBox = new InlineBox(title, CLASS);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(HTMLClass CLASS) {
		InlineBox newInlineBox = new InlineBox(CLASS);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(HTMLClass CLASS, String content) {
		InlineBox newInlineBox = new InlineBox(CLASS, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(HTMLClass CLASS, String title, String content) {
		InlineBox newInlineBox = new InlineBox(CLASS, title, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String style, HTMLClass CLASS, String content) {
		InlineBox newInlineBox = new InlineBox(style, CLASS, content);
		addChild(newInlineBox);
		return newInlineBox;
	}

	//methods for creating Infobox children
	public InfoboxWidget addInfobox(InfoboxWidget.Type type, String title) {
		InfoboxWidget newInfobox = new InfoboxWidget(type, title);
		addChild(newInfobox);
		return newInfobox;
	}

    //methods for creating <i> tags
    @Deprecated
    public I addI() {
        I newI = new I();
        addChild(newI);
        return newI;
    }
    public I addI(String content) {
        I newI = new I(content);
        addChild(newI);
        return newI;
    }

    //methods for creating Link children
	public Link addLink() {
		Link newLink = new Link();
		addChild(newLink);
		return newLink;
	}
	public Link addLink(Link.linkType type, HTMLID data) {
		Link newLink = new Link(type, data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(Link.linkType type, String data) {
		Link newLink = new Link(type, data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(Link.linkType type, String data, String ID) {
		Link newLink = new Link(type, data, ID);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(HTMLClass CLASS, String title, String content) {
		Link newLink = new Link(CLASS, title, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data) {
		Link newLink = new Link(data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String content) {
		Link newLink = new Link(data, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, HTMLClass CLASS, String content) {
		Link newLink = new Link(data, CLASS, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String title, String content) {
		Link newLink = new Link(data, title, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, Link.target target,  String content) {
		Link newLink = new Link(data, target, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String title, HTMLClass CLASS, String content) {
		Link newLink = new Link(data, title, CLASS, content);
		addChild(newLink);
		return newLink;
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

	//methods for creating Table children
	public Table addTable() {
		Table newTable = new Table();
		addChild(newTable);
		return newTable;
	}
	public Table addTable(HTMLClass CLASS) {
		Table newTable = new Table(CLASS);
		addChild(newTable);
		return newTable;
	}
}
