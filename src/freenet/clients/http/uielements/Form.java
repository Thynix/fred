package freenet.clients.http.uielements;

/**
 * Creates a form element
 */
public class Form extends OutputNode {

	private String action;
	private String method;
	private Link.Target target;
	private String enctype;
	private String charset;
	private String name;

	@Deprecated
	public Form(String action, String method, String enctype, String charset, String id, String name) {
		this(action, method, enctype, charset, id);
		setName(name);
	}

	public Form(String action, String method, String enctype, String charset, Identifier id, String name) {
		this(action, method, enctype, charset, id);
		setName(name);
	}

	@Deprecated
	public Form(String action, String method, String enctype, String charset, String id) {
		this(action, method);
		this.setEnctype(enctype);
		this.setCharset(charset);
		this.addAttribute("id", id);
	}

	public Form(String action, String method, String enctype, String charset, Identifier id) {
		this(action, method);
		this.setEnctype(enctype);
		this.setCharset(charset);
		this.setID(id);
	}

	public Form(String action, String method, Link.Target target) {
		this(action, method);
		setTarget(target);
	}

	public Form(String action, String method) {
		this();
		this.setMethod(action, method);
	}

	private Form() {
		super("form");
	}

	private void setMethod(String action, String method) {
		this.action = action;
		this.method = method;
		this.addAttributes(new String[]{"action", "method"}, new String[]{action, method});
	}

	private void setTarget(Link.Target target) {
		this.target = target;
		this.addAttribute("target", target.value);
	}

	private void setEnctype(String enctype) {
		this.enctype = enctype;
		this.addAttribute("enctype", enctype);
	}

	private void setCharset(String charset) {
		this.charset = charset;
		this.addAttribute("accept-charset", charset);
	}

	private void setName(String name) {
		this.name = name;
		this.addAttribute("name", name);
	}
}
