package freenet.clients.http.uielements;

public class AlertLine extends InfoboxWidget {

	public Box header = new Box(HTMLClass.INFOBOXHEADER);
	public Box body = new Box(HTMLClass.INFOBOXCONTENT);

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
