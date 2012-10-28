package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
/**
 * Creates table header cells
 */
public class Header extends OutputNode {

	public int colspan;
	public String width;

	public Header(Category category, String content) {
		this(category);
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

	public Header(Category category) {
		this();
		addClass(category);
	}

	public Header() {
		super("th");
	}

	public void setColspan(int colspan) {
		this.colspan = colspan;
		this.addAttribute("colspan", Integer.toString(colspan));
	}
	public void setWidth(String width) {
		this.width = width;
		this.addAttribute("width", width);
	}
}