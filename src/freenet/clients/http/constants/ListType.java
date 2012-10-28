package freenet.clients.http.constants;

public enum ListType {
	ORDERED("ol"),
	UNORDERED("ul");

	public final String tagName;

	private ListType(String type) {
		this.tagName = type;
	}
}

