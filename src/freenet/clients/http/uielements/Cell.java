package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Cell extends OutputNode {

	public Cell(HTMLClass CLASS, String content) {
		this(CLASS);
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
}