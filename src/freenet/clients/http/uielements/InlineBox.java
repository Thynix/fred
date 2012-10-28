package freenet.clients.http.uielements;

import freenet.clients.http.constants.Category;
import freenet.clients.http.constants.Identifier;
/**
 * Creates a block element
 */
public class InlineBox extends OutputNode {
	public String title;

	public InlineBox(Identifier id, String content) {
		this(id);
		this.setContent(content);
	}

	public InlineBox(Identifier id) {
		this();
		setID(id);
	}

	public InlineBox(String style, Category category, String content) {
		this(category, content);
		addAttribute("style", style);
	}

	public InlineBox(Category category, String title, String content) {
		this(category, content);
		setTitle(title);
	}

	public InlineBox(Category category, String content) {
		this(category);
		this.setContent(content);
	}

	public InlineBox(String title, Category category) {
		this(category);
		setTitle(title);
	}

	public InlineBox(Category category) {
		this();
		addClass(category);
	}

	public InlineBox(String content) {
		this();
		this.setContent(content);
	}

	public InlineBox() {
		super("span");
	}

	public void setTitle(String title) {
		this.title = title;
		this.addAttribute("title", title);
	}
}
