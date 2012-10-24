package freenet.clients.http.uielements;

/**
 * Creates a meta element
 */
public class Head extends OutputNode {

	public Title Title;

	public Head(String Title) {
		super("head", Title);
	}

	public void setTitle(String Title) {
		this.Title.setContent(Title);
	}

	public Meta addMeta(String Equiv, String Content) {
		Meta newMeta = new Meta(Equiv, Content);
		this.addChild(newMeta);
		return newMeta;
	}
}
