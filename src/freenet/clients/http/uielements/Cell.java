package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Cell extends OutputNode {

	public int Colspan;
	public String Width;

	public Cell(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public Cell(int colspan, String content) {
		this();
		this.setContent(content);
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

	public Cell(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Cell() {
		super("td");
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