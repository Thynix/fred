package freenet.clients.http.uielements;

import freenet.support.HTMLNode;

public class InfoboxWidget extends Box {

	public Box header;
	public Box body;

	public enum Type {
		ALERT(Category.INFOBOXALERT),
		ERROR(Category.INFOBOXERROR),
		FAILEDREQUESTS(Category.FAILEDREQUESTS),
		INFORMATION(Category.INFOBOXINFORMATION),
		LEGEND(Category.LEGEND),
		MINOR(Category.INFOBOXMINOR),
		NAVBAR(Category.NAVBAR),
		NORMAL(Category.INFOBOXNORMAL),
		NONE(Category.NONE),
		PROGRESSING(Category.REQUESTSINPROGRESS),
		QUERY(Category.INFOBOXQUERY),
		REQUESTCOMPLETE(Category.REQUESTCOMPLETED),
		SUCCESS(Category.INFOBOXSUCCESS),
		WARNING(Category.INFOBOXWARNING),
		WTF(Category.WTF);

		public final Category htmlclass;

		private Type(Category id) {
			this.htmlclass = id;
		}
	}

	public InfoboxWidget(Type type, Category category, String title, String content) {
		this(type, category, title);
		this.body.setContent(content);
	}

	public InfoboxWidget(Type type, Category category, String title, OutputNode content) {
		this(type, category, title);
		this.body.addChild(content);
	}

	public InfoboxWidget(Type type, Category category, String title) {
		this(type, title);
		this.addClass(category);
	}

	public InfoboxWidget(Identifier id, String title) {
		this(Type.NONE, id, title);
	}

	public InfoboxWidget(Type type, Identifier id, String title, String content) {
		this(type, id, title);
		this.body.setContent(content);
	}

	public InfoboxWidget(Type type, Identifier id, String title, OutputNode content) {
		this(type, id, title);
		this.body.addChild(content);
	}

	public InfoboxWidget(Type type, Identifier id, String title) {
		this(type, title);
		this.setID(id);
	}

	public InfoboxWidget(Type type, String title) {
		this(type.htmlclass, title);
	}

	public InfoboxWidget(String title) {
		this(Type.NONE, title);
	}

	protected InfoboxWidget(Category type, String title) {
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

	public HTMLNode addContentNode() {
		return this.addChild(new Box(Category.INFOBOXCONTENT));
	}

	public void setTitle(String newtitle) {
		header.setContent(newtitle);
	}
}
