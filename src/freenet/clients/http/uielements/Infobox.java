package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InfoboxType;
public class Infobox extends Box {

	public Box header;
	public Box body;

	public Infobox(InfoboxType type, Category category, String title, String content) {
		this(type, category, title);
		this.body.setContent(content);
	}

	public Infobox(InfoboxType type, Category category, String title, OutputNode content) {
		this(type, category, title);
		this.body.addChild(content);
	}

	public Infobox(InfoboxType type, Category category, String title) {
		this(type, title);
		this.addClass(category);
	}

	public Infobox(Identifier id, String title) {
		this(InfoboxType.NONE, id, title);
	}

	public Infobox(InfoboxType type, Identifier id, String title, String content) {
		this(type, id, title);
		this.body.setContent(content);
	}

	public Infobox(InfoboxType type, Identifier id, String title, OutputNode content) {
		this(type, id, title);
		this.body.addChild(content);
	}

	public Infobox(InfoboxType type, Identifier id, String title) {
		this(type, title);
		this.setID(id);
	}

	public Infobox(InfoboxType type, String title) {
		this(type.htmlclass, title);
	}

	public Infobox(String title) {
		this(InfoboxType.NONE, title);
	}

	protected Infobox(Category type, String title) {
		super(Category.INFOBOX);
		if (type != Category.NONE) {
			this.addClass(type);
		}
		this.header = new Box(Category.INFOBOXHEADER);
		this.addChild(header);
		this.body = new Box(Category.INFOBOXCONTENT);
		this.addChild(body);
		header.setContent(title);
	}

	public Box addContentNode() {
		Box newContentNode = new Box(Category.INFOBOXCONTENT);
		this.addChild(newContentNode);
		return newContentNode;
	}

	public void setTitle(String newtitle) {
		header.setContent(newtitle);
	}
}
