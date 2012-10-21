package freenet.support.uielements;

import freenet.support.HTMLNode;
import freenet.support.htmlprimitives.Div;
import freenet.support.htmlprimitives.HTMLClass;

public class InfoboxWidget extends Div {

	public Div header = new Div(HTMLClass.INFOBOXHEADER);
	public Div body = new Div(HTMLClass.INFOBOXCONTENT);

	public enum Type {
		ALERT(HTMLClass.INFOBOXALERT),
		ERROR(HTMLClass.INFOBOXERROR),
		INFORMATION(HTMLClass.INFOBOXINFORMATION),
		MINOR(HTMLClass.INFOBOXMINOR),
		NORMAL(HTMLClass.INFOBOXNORMAL),
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
		header.setContent(title);
		this.addClass(type);
		this.addChild(header);
		this.addChild(body);
	}

	public HTMLNode addContentNode() {
		return this.addChild(new Div(HTMLClass.INFOBOXCONTENT));
	}

	public void setTitle(String newtitle) {
		header.setContent(newtitle);
	}
}
