package freenet.clients.http.uielements;

import freenet.support.HTMLNode;

public class InfoboxWidget extends Box {

	public Box header;
	public Box body;

	public enum Type {
		ALERT(HTMLClass.INFOBOXALERT),
		ERROR(HTMLClass.INFOBOXERROR),
		INFORMATION(HTMLClass.INFOBOXINFORMATION),
		MINOR(HTMLClass.INFOBOXMINOR),
		NORMAL(HTMLClass.INFOBOXNORMAL),
		NONE(HTMLClass.NONE),
		QUERY(HTMLClass.INFOBOXQUERY),
		SUCCESS(HTMLClass.INFOBOXSUCCESS),
		WARNING(HTMLClass.INFOBOXWARNING);

		public final HTMLClass htmlclass;

		private Type(HTMLClass ID) {
			this.htmlclass = ID;
		}
	}

	public InfoboxWidget(Type type, String title) {
		this(type.htmlclass, title);
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
