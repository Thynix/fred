package freenet.support.uielements;

import freenet.support.htmlprimitives.Div;
import freenet.support.htmlprimitives.HTMLClass;

public class AlertLine extends InfoboxWidget {

	public Div header = new Div(HTMLClass.INFOBOXHEADER);
	public Div body = new Div(HTMLClass.INFOBOXCONTENT);

	public enum Type {
		ALERT(HTMLClass.CONTAINSALERT),
		INFORMATION(HTMLClass.CONTAINSINFORMATION),
		WARNING(HTMLClass.CONTAINSWARNING);

		public final HTMLClass htmlclass;

		private Type(HTMLClass ID) {
			this.htmlclass = ID;
		}
	}

	public AlertLine(Type type, String title) {
		super(type.htmlclass, title);
	}


}
