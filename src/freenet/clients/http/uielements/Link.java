package freenet.clients.http.uielements;

import freenet.clients.http.constants.*;
/**
 * Creates an link
 */
public class Link extends OutputNode {

	public LinkType type;
	public String data;
	public Target target;
	public String title;

	public Link(Category category) {
		this();
		addClass(category);
	}

	public Link (String data, Media media, String title) {
		this();
		this.addAttribute("rel", "stylesheet");
		this.addAttribute(LinkType.HYPERLINK.attribute, data);
		this.addAttribute("type", "text/css");
		this.addAttribute("media", media.name);
		this.setTitle(title);

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
		this.addAttribute(LinkType.HYPERLINK.attribute, data);
	}

	public Link(LinkType type, String data) {
		this();
		this.addAttribute(LinkType.HYPERLINK.attribute, data);
	}

	public Link(LinkType type, Identifier data) {
		this();
		this.addAttribute(LinkType.HYPERLINK.attribute, data.name);
	}

	public Link(LinkType type, String data, String ID) {
		this();
		this.addAttribute(LinkType.HYPERLINK.attribute, data);
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
