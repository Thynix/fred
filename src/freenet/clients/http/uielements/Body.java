package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
/**
 * Creates a body element
 */
public class Body extends OutputNode {

	public Body(Category category) {
		this();
		addClass(category);
	}

	public Body() {
		super("body");
	}

	@Deprecated
	public void setID(String id) {
		addAttribute("id", id);
	}

}
