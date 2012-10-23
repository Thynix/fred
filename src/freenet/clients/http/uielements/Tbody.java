package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Tbody extends OutputNode {

	//Constructors
	public Tbody(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Tbody() {
		super("thead");
	}
	//Methods for adding rows
	public Row addRow() {
		Row newRow = new Row();
		this.addChild(newRow);
		return newRow;
	}
	public Row addRow(HTMLClass CLASS) {
		Row newRow = new Row(CLASS);
		this.addChild(newRow);
		return newRow;
	}
}
