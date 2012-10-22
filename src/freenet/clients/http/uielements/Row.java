package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Row extends OutputNode {

	//Constructors
	public Row(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Row() {
		super("td");
	}

	//Methods for adding cells
	public Cell addCell() {
		Cell newCell = new Cell();
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(HTMLClass CLASS) {
		Cell newCell = new Cell(CLASS);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(String content) {
		Cell newCell = new Cell(content);
		this.addChild(newCell);
		return newCell;
	}
	public Cell addCell(HTMLClass CLASS, String content) {
		Cell newCell = new Cell(CLASS, content);
		this.addChild(newCell);
		return newCell;
	}
	//Methods for adding header cells
	public Header addHeader() {
		Header newCell = new Header();
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(HTMLClass CLASS) {
		Header newCell = new Header(CLASS);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(String content) {
		Header newCell = new Header(content);
		this.addChild(newCell);
		return newCell;
	}
	public Header addHeader(HTMLClass CLASS, String content) {
		Header newCell = new Header(CLASS, content);
		this.addChild(newCell);
		return newCell;
	}
}
