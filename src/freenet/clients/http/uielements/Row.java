package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Row extends OutputNode {

	//Constructors
	public Row(Category category) {
		this();
		addClass(category);
	}

	public Row() {
		super("tr");
	}

	//Methods for adding cells
	public Cell addCell() {
		Cell newCell = new Cell();
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(int colspan) {
		Cell newCell = new Cell(colspan);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(Category category) {
		Cell newCell = new Cell(category);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(int colspan, Category category) {
		Cell newCell = new Cell(colspan, category);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(int colspan, String content) {
		Cell newCell = new Cell(colspan, content);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(String content) {
		Cell newCell = new Cell(content);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(Category category, String content) {
		Cell newCell = new Cell(category, content);
		this.addChild(newCell);
		return newCell;
	}
	//Methods for adding header cells
	public Header addHeader() {
		Header newCell = new Header();
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(Category category) {
		Header newCell = new Header(category);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(String width, String content) {
		Header newCell = new Header(width, content);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(int colspan, String content) {
		Header newCell = new Header(colspan, content);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(String content) {
		Header newCell = new Header(content);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(Category category, String content) {
		Header newCell = new Header(category, content);
		this.addChild(newCell);
		return newCell;
	}
}
