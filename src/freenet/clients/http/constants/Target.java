package freenet.clients.http.constants;

public enum Target {
	BLANK("_blank"),
	NEW("_new");

	public final String value;

	private Target(String type) {
		this.value = type;
	}
}
