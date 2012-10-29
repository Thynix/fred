package freenet.clients.http.uielements;

/**
 * Creates a noscript element
 */
public class Noscript extends OutputNode {

	public Noscript() {
		super("noscript");
	}

	public Meta addMeta(String equiv, String content) {
		Meta newMeta = new Meta(equiv, content);
		this.addChild(newMeta);
		return newMeta;
	}
	public Meta addMeta(String equiv) {
		Meta newMeta = new Meta(equiv);
		this.addChild(newMeta);
		return newMeta;
	}

}
