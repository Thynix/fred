package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
import freenet.support.HTMLNode;

public class OutputNode extends HTMLNode {

	public OutputNode(String name) {
		super(name);
	}

	public OutputNode(String name, Category category) {
		super(name);
		this.addClass(category);
	}

	public OutputNode(String name, String attribute, String value) {
		super(name, attribute, value);
	}

	public OutputNode(String name, String content) {
		super(name, content);
	}

	public OutputNode addChild(String name, String attribute, String value, String content) {
		OutputNode childNode = new OutputNode(name, content);
		childNode.addAttribute(attribute, value);
		this.addChild(childNode);
		return childNode;
	}

	public OutputNode addChild(String name, String attribute, String value) {
		OutputNode childNode = new OutputNode(name);
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
}