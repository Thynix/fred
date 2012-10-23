package freenet.clients.http.uielements;

/**
 * Creates table header cells
 */
public class Header extends OutputNode {

	public int Colspan;
	public String Width;

	public Header(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Header(String width, String content) {
		this();
		this.setWidth(width);
		this.setContent(content);
	}

	public Header(int colspan, String content) {
		this();
		this.setColspan(colspan);
		this.setContent(content);
	}

	public Header(String content) {
		this();
		this.setContent(content);
	}

	public Header(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Header() {
		super("th");
	}

	public void setColspan(int colspan) {
		this.Colspan = colspan;
		this.addAttribute("colspan", Integer.toString(colspan));
	}
	public void setWidth(String width) {
		this.Width = width;
		this.addAttribute("width", width);
	}
}