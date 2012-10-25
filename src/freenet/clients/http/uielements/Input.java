package freenet.clients.http.uielements;

/**
 * Creates a form element
 */
public class Input extends OutputNode {

	private String type;
	private String name;
	private String value;
	private String alt;
	private String size;
	private String maxlength;
	private String checked;

	public Input(String type, String name, String value) {
		this(type, name);
		this.setValue(value);
	}

	public Input(String type, String name) {
		this(type);
		this.setName(name);
	}

	private Input(String type) {
		super("input");
		this.setType(type);
	}

	private void setMaxLength(String length) {
		this.maxlength = length;
		this.addAttribute("size", length);
	}

	private void setchecked(String checked) {
		this.checked= checked;
		this.addAttribute("checked", checked);
	}

	private void setSize(String size) {
		this.size = size;
		this.addAttribute("size", size);
	}

	private void setAlt(String alt) {
		this.alt = alt;
		this.addAttribute("alt", alt);
	}

	private void setValue(String value) {
		this.value = value;
		this.addAttribute("value", value);
	}

	private void setType(String type) {
		this.type = type;
		this.addAttribute("type", type);
	}

	private void setName(String name) {
		this.name = name;
		this.addAttribute("name", name);
	}
}
