package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
public class AlertLine extends Infobox {

	public Box header = new Box(Category.INFOBOXHEADER);
	public Box body = new Box(Category.INFOBOXCONTENT);

	public enum Type {
		ALERT(Category.CONTAINSALERT),
		INFORMATION(Category.CONTAINSINFORMATION),
		WARNING(Category.CONTAINSWARNING);

		public final Category category;

		private Type(Category category) {
			this.category = category;
		}
	}

	public AlertLine(Type type, String title) {
		super(type.category, title);
	}


}
