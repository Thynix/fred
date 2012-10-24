package freenet.clients.http.uielements;

/**
 * Creates an link
 */
public class Link extends OutputNode {

	public enum Type {
		HYPERLINK("href"),
		ANCHOR("name");

		public final String attribute;

		private Type(String type) {
			this.attribute = type;
		}
	}

	public enum Target {
		BLANK("_blank"),
		NEW("_new");

		public final String value;

		private Target(String type) {
			this.value = type;
		}
	}

	public Type type;
	public String data;
	public Target target;
	public String title;

	public Link(Category category) {
		this();
		addClass(category);
	}

	public Link(String data, String title, Category category, String content) {
		this(data, title, content);
		addClass(category);
	}

	public Link(String data, String title, String content) {
		this(data, content);
		this.setTitle(title);
	}

	public Link(String data, Target target, String content) {
		this(data, content);
		this.setTarget(target);
	}

	public Link(String data, Target target) {
		this(data);
		this.setTarget(target);
	}

	public Link(String data, Category category, String content) {
		this(data, content);
		addClass(category);
	}

	public Link(Category category, String title, String content) {
		this();
		setTitle(title);
		addClass(category);
		setContent(content);
	}

	public Link(String data, String content) {
		this(data);
		this.setContent(content);
	}

	public Link(String data) {
		this();
		this.addAttribute(Type.HYPERLINK.attribute, data);
	}

	public Link(Type type, String data) {
		this();
		this.addAttribute(Type.HYPERLINK.attribute, data);
	}

	public Link(Type type, Identifier data) {
		this();
		this.addAttribute(Type.HYPERLINK.attribute, data.name);
	}

	public Link(Type type, String data, String ID) {
		this();
		this.addAttribute(Type.HYPERLINK.attribute, data);
		this.addAttribute("id", ID);
	}

	public Link() {
		super("a");
	}

	public void setTitle(String title) {
		this.title = title;
		this.addAttribute("title", title);
	}

	public void setTarget(Target target) {
		this.target = target;
		this.addAttribute("target", target.value);
	}
}
