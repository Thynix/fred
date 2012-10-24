package freenet.clients.http.uielements;

/**
 * Creates an link
 */
public class Link extends OutputNode {

	public enum linkType {
		HYPERLINK("href"),
		ANCHOR("name");

		public final String name;

		private linkType(String type) {
			this.name = type;
		}
	}

	public enum linkTarget {
		BLANK("_blank"),
		NEW("_new");

		public final String name;

		private linkTarget(String type) {
			this.name = type;
		}
	}

	public linkType type;
	public String data;
	public linkTarget target;
	public String title;

	public Link(HTMLClass CLASS) {
		this();
		addClass(CLASS);
	}

	public Link(String data, String title, HTMLClass CLASS, String content) {
		this(data, title, content);
		addClass(CLASS);
	}

	public Link(String data, String title, String content) {
		this(data, content);
		this.setTitle(title);
	}

	public Link(String data, linkTarget target, String content) {
		this(data, content);
		this.setTarget(target);
	}

	public Link(String data, linkTarget target) {
		this(data);
		this.setTarget(target);
	}

	public Link(String data, HTMLClass CLASS, String content) {
		this(data, content);
		addClass(CLASS);
	}

	public Link(HTMLClass CLASS, String title, String content) {
		this();
		setTitle(title);
		addClass(CLASS);
		setContent(content);
	}

	public Link(String data, String content) {
		this(data);
		this.setContent(content);
	}

	public Link(String data) {
		this();
		this.addAttribute(linkType.HYPERLINK.name, data);
	}

	public Link(linkType type, String data) {
		this();
		this.addAttribute(linkType.HYPERLINK.name, data);
	}

	public Link(linkType type, HTMLID data) {
		this();
		this.addAttribute(linkType.HYPERLINK.name, data.name);
	}

	public Link(linkType type, String data, String ID) {
		this();
		this.addAttribute(linkType.HYPERLINK.name, data);
		this.addAttribute("id", ID);
	}

	public Link() {
		super("a");
	}

	public void setTitle(String title) {
		this.title = title;
		this.addAttribute("title", title);
	}

	public void setTarget(linkTarget target) {
		this.target = target;
		this.addAttribute("target", target.name);
	}
}
