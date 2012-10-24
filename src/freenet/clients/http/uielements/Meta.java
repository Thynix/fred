package freenet.clients.http.uielements;

/**
 * Creates a meta element
 */
public class Meta extends OutputNode {

	public String MetaEquiv;
	public String MetaContent;

	public Meta(String Equiv, String Content) {
		this();
		setMetaEquiv(Equiv);
		setMetaContent(Content);
	}
	public Meta() {
		super("meta");
	}

	public void setMetaEquiv(String Equiv) {
		this.MetaEquiv = Equiv;
		this.addAttribute("http-equiv", Equiv);
	}
	public void setMetaContent(String Content) {
		this.MetaContent = Content;
		this.addAttribute("content", Content);
	}
}
