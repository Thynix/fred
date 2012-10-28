package freenet.clients.http.constants;

public enum Media {
	ALL("all"),
	AURAL("aural"),
	BRAILLE("braille"),
	EMBOSSED("embossed"),
	HANDHELD("handheld"),
	PRINT("print"),
	PROJECTION("projection"),
	SCREEN("screen"),
	TTY("tty"),
	TV("tv");

	public final String name;

	private Media(String type) {
		this.name = type;
	}
}

