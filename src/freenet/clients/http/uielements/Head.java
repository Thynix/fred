package freenet.clients.http.uielements;

/**
 * Creates a meta element
 */
public class Head extends OutputNode {

	public Title title;

	public Head(String title) {
		super("head");
		this.title = new Title(title);
		this.addChild(this.title);
	}

	public void setTitle(String title) {
		this.title.setContent(title);
	}

	public Meta addMeta(String equiv, String content) {
		Meta newMeta = new Meta(equiv, content);
		this.addChild(newMeta);
		return newMeta;
	}
}
