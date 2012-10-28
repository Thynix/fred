package freenet.clients.http.constants;

public enum LinkType {
	HYPERLINK("href"),
	ANCHOR("name");

	public final String attribute;

	private LinkType(String type) {
		this.attribute = type;
	}
}