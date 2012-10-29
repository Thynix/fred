package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
/**
 * Creates table cells
 */
public class Tbody extends OutputNode {

	//Constructors
	public Tbody(Category category) {
		this();
		addClass(category);
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
	public Row addRow(Category category) {
		Row newRow = new Row(category);
		this.addChild(newRow);
		return newRow;
	}
}
