package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
/**
 * Creates table cells
 */
public class Cell extends OutputNode {

	public int colspan;
	public String width;

	public Cell(Category category, String content) {
		this(category);
		this.setContent(content);
	}

	public Cell(int colspan, String content) {
		this(content);
		setColspan(colspan);
	}

	public Cell(int colspan, Category category) {
		this(category);
		setColspan(colspan);
	}

	public Cell(int colspan) {
		this();
		setColspan(colspan);
	}

	public Cell(String width, String content) {
		this();
		this.setWidth(width);
		this.setContent(content);
	}

	public Cell(String content) {
		this();
		this.setContent(content);
	}

	public Cell(Category category) {
		this();
		addClass(category);
	}

	public Cell() {
		super("td");
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