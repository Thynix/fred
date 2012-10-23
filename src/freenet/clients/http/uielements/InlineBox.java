package freenet.clients.http.uielements;

/**
 * Creates a block element
 */
public class InlineBox extends OutputNode {
	public String title;

	public InlineBox(HTMLID ID, String content) {
		this(ID);
		this.setContent(content);
	}

	public InlineBox(HTMLID ID) {
		this();
		setID(ID);
	}

	public InlineBox(String style, HTMLClass CLASS, String content) {
		this(CLASS, content);
		addAttribute("style", style);
	}

	public InlineBox(HTMLClass CLASS, String Title, String content) {
		this(CLASS, content);
		setTitle(Title);
	}

	public InlineBox(HTMLClass CLASS, String content) {
		this(CLASS);
		this.setContent(content);
	}

	public InlineBox(String title, HTMLClass CLASS) {
		this(CLASS);
		setTitle(title);
	}

	public InlineBox(HTMLClass CLASS) {
		this();
		addClass(CLASS);
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
