package freenet.clients.http.uielements;

/**
 * Creates a form element
 */
public class Input extends OutputNode {

	private Type type;
	private String name;
	private String value;
	private String alt;
	private int size;
	private short maxlength;
	private boolean checked;
	private boolean disabled;

	public enum Type {
		BUTTON("button"),
		CHECKBOX("checkbox"),
		COLOR("color"),
		DATE("date"),
		DATETIME("datetime"),
		DATETIMELOCAL("datetime-local"),
		EMAIL("email"),
		FILE("file"),
		HIDDEN("hidden"),
		IMAGE("image"),
		MONTH("month"),
		NUMBER("number"),
		PASSWORD("password"),
		RADIO("radio"),
		RANGE("range"),
		RESET("reset"),
		SEARCH("search"),
		SUBMIT("submit"),
		TEL("tel"),
		TEXT("text"),
		TIME("time"),
		URL("url"),
		WEEK("week");

		public final String name;

		private Type(String name) {
			this.name = name;
		}
	}

	public Input(Type type, String name, String value, Category category, String alt, Boolean disabled) {
		this(type, name, value, category, alt);
		this.setAlt(alt);
	}

	public Input(Type type, String name, String value, int size, short maxlength, Identifier id) {
		this(type, name, value, size, maxlength);
		this.setID(id);
	}

	public Input(Type type, String name, String value, int size, short maxlength) {
		this(type, name, value);
		this.setSize(size);
		this.setMaxLength(maxlength);
	}

	public Input(Type type, String name, String value, Category category, String alt) {
		this(type, name, value, category);
		this.setAlt(alt);
	}

	public Input(Type type, String name, String value, Identifier id, int size) {
		this(type, name, value, id);
		this.setSize(size);
	}

	public Input(Type type, String name, boolean checked, Identifier id) {
		this(type, name, checked);
		this.setID(id);
	}

	public Input(Type type, String name, int size, Identifier id) {
		this(type, name, size);
		this.setID(id);
	}

	public Input(Type type, String name, String value, boolean checked) {
		this(type, name, value);
		this.setChecked(checked);
	}

	public Input(Type type, String name, String value, Category category) {
		this(type, name, value);
		this.addClass(category);
	}

	public Input(Type type, String name, String value, Identifier id) {
		this(type, name, value);
		this.setID(id);
	}

	public Input(Type type, String name, Identifier id) {
		this(type, name);
		this.setID(id);
	}

	public Input(Type type, String name, boolean checked) {
		this(type, name);
		this.setChecked(checked);
	}

	public Input(Type type, String name, String value) {
		this(type, name);
		this.setValue(value);
	}

	public Input(Type type, String name, int size) {
		this(type, name);
		this.setSize(size);
	}

	public Input(Type type, String value, Category category) {
		this(type, value);
		this.addClass(category);
	}

	public Input(Type type, String value) {
		this(type);
		this.setValue(value);
	}

	public Input(String name, Type type) {
		this(type);
		this.setName(name);
	}

	public Input(String name) {
		this();
		this.setName(name);
	}

	public Input(Type type) {
		this();
		this.setType(type);
	}

	private Input() {
		super("input");
	}

	private void setMaxLength(short length) {
		this.maxlength = length;
		this.addAttribute("maxlength", Short.toString(length));
	}

	private void setChecked(boolean checked) {
		if (checked) {
			this.checked = true;
			this.addAttribute("checked", "checked");
		}
	}

	private void setDisabled(boolean disabled) {
		if (disabled) {
			this.checked = true;
			this.addAttribute("disabled", "disabled");
		}
	}

	private void setSize(int size) {
		this.size = size;
		this.addAttribute("size", Integer.toString(size));
	}

	private void setAlt(String alt) {
		this.alt = alt;
		this.addAttribute("alt", alt);
	}

	private void setValue(String value) {
		this.value = value;
		this.addAttribute("value", value);
	}

	private void setType(Type type) {
		this.type = type;
		this.addAttribute("type", type.name);
	}

	private void setName(String name) {
		this.name = name;
		this.addAttribute("name", name);
	}
}
