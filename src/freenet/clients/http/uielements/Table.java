package freenet.clients.http.uielements;

/**
 * Creates table cells
 */
public class Table extends OutputNode {

	public Tbody body;

	//Constructors
	public Table(Category category) {
		this();
		addClass(category);
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
	public Row addRow(Row newRow) {
		this.addChild(newRow);
		return newRow;
	}
	public Row addRow(Category category) {
		Row newRow = new Row(category);
		this.addChild(newRow);
		return newRow;
	}
	//Methods for adding tbody
	public Tbody addBody() {
		Tbody newBody = new Tbody();
		this.body = newBody;
		this.addChild(newBody);
		return body;
	}
}
