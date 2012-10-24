package freenet.clients.http.uielements;

/**
 * Creates a meta element
 */
public class Meta extends OutputNode {

	public String equiv;
	public String equivContent;

	public Meta(String equiv, String content) {
		this();
		setEquiv(equiv);
		setEquivContent(content);
	}
	public Meta() {
		super("meta");
	}

	public void setEquiv(String equiv) {
		this.equiv = equiv;
		this.addAttribute("http-equiv", equiv);
	}
	public void setEquivContent(String content) {
		this.equivContent = content;
		this.addAttribute("content", content);
	}
}
