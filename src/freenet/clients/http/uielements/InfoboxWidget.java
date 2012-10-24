package freenet.clients.http.uielements;

import freenet.support.HTMLNode;

public class InfoboxWidget extends Box {

	public Box header;
	public Box body;

	public enum Type {
		ALERT(HTMLClass.INFOBOXALERT),
		ERROR(HTMLClass.INFOBOXERROR),
		FAILEDREQUESTS(HTMLClass.FAILEDREQUESTS),
		INFORMATION(HTMLClass.INFOBOXINFORMATION),
		LEGEND(HTMLClass.LEGEND),
		MINOR(HTMLClass.INFOBOXMINOR),
		NAVBAR(HTMLClass.NAVBAR),
		NORMAL(HTMLClass.INFOBOXNORMAL),
		NONE(HTMLClass.NONE),
		PROGRESSING(HTMLClass.REQUESTSINPROGRESS),
		QUERY(HTMLClass.INFOBOXQUERY),
		REQUESTCOMPLETE(HTMLClass.REQUESTCOMPLETED),
		SUCCESS(HTMLClass.INFOBOXSUCCESS),
		WARNING(HTMLClass.INFOBOXWARNING),
		WTF(HTMLClass.WTF);

		public final HTMLClass htmlclass;

		private Type(HTMLClass ID) {
			this.htmlclass = ID;
		}
	}

	public InfoboxWidget(Type type, HTMLClass Class, String title, String content) {
		this(type, Class, title);
		this.body.setContent(content);
	}

	public InfoboxWidget(Type type, HTMLClass Class, String title, OutputNode content) {
		this(type, Class, title);
		this.body.addChild(content);
	}

	public InfoboxWidget(Type type, HTMLClass Class, String title) {
		this(type, title);
		this.addClass(Class);
	}

	public InfoboxWidget(HTMLID ID, String title) {
		this(Type.NONE, ID, title);
	}

	public InfoboxWidget(Type type, HTMLID ID, String title, String Content) {
		this(type, ID, title);
		this.body.setContent(Content);
	}

	public InfoboxWidget(Type type, HTMLID ID, String title, OutputNode Content) {
		this(type, ID, title);
		this.body.addChild(Content);
	}

	public InfoboxWidget(Type type, HTMLID ID, String title) {
		this(type, title);
		this.setID(ID);
	}

	public InfoboxWidget(Type type, String title) {
		this(type.htmlclass, title);
	}

	public InfoboxWidget(String title) {
		this(Type.NONE, title);
	}

	protected InfoboxWidget(HTMLClass type, String title) {
		super(HTMLClass.INFOBOX);
		if (type != HTMLClass.NONE) {
			this.addClass(type);
		}
		this.header = new Box(HTMLClass.INFOBOXHEADER);
		this.addChild(header);
		this.body = new Box(HTMLClass.INFOBOXCONTENT);
		this.addChild(body);
		header.setContent(title);
	}

	public HTMLNode addContentNode() {
		return this.addChild(new Box(HTMLClass.INFOBOXCONTENT));
	}

	public void setTitle(String newtitle) {
		header.setContent(newtitle);
	}
}
