package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Table extends OutputNode {

	//Constructors
	public Table(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Table() {
		super("table");
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
