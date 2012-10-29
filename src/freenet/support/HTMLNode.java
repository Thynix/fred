package freenet.support;

import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;

import java.util.*;
import java.util.regex.Pattern;

public class HTMLNode implements XMLCharacterClasses {
	
	private static final Pattern namePattern = Pattern.compile("^[" + NAME + "]*$");
	private static final Pattern simpleNamePattern = Pattern.compile("^[A-Za-z][A-Za-z0-9]*$");
	/** Text to be inserted between tags, or possibly raw HTML. Only non-null if name is "#" (= text) or "%" (= raw
	 * HTML). Otherwise the constructor will allocate a separate child node to contain it. */
	private String content;
	private final Map<String, String> attributes = new HashMap<String, String>();
	//Lists of html elements which receive special handling
	private static final ArrayList<String> EmptyTag = new ArrayList<String>(10);
	private static final ArrayList<String> OpenTags = new ArrayList<String>(12);
	private static final ArrayList<String> CloseTags = new ArrayList<String>(12);
	static {
		/* HTML elements which are allowed to be empty */
		EmptyTag.add("area");
		EmptyTag.add("base");
		EmptyTag.add("br");
		EmptyTag.add("col");
		EmptyTag.add("hr");
		EmptyTag.add("img");
		EmptyTag.add("input");
		EmptyTag.add("link");
		EmptyTag.add("meta");
		EmptyTag.add("param");
		/* HTML elements for which we should add a newline following the open tag. */
		OpenTags.add("body");
		OpenTags.add("div");
		OpenTags.add("form");
		OpenTags.add("head");
		OpenTags.add("html");
		OpenTags.add("input");
		OpenTags.add("ol");
		OpenTags.add("script");
		OpenTags.add("table");
		OpenTags.add("td");
		OpenTags.add("tr");
		OpenTags.add("ul");
		/* HTML elements for which we should add a newline following the close tag. */
		CloseTags.add("h1");
		CloseTags.add("h2");
		CloseTags.add("h3");
		CloseTags.add("h4");
		CloseTags.add("h5");
		CloseTags.add("h6");
		CloseTags.add("li");
		CloseTags.add("link");
		CloseTags.add("meta");
		CloseTags.add("noscript");
		CloseTags.add("option");
		CloseTags.add("title");
	}

	protected boolean readOnly;
	protected final String name;
	protected final List<HTMLNode> children = new ArrayList<HTMLNode>();

	public static HTMLNode STRONG = new HTMLNode("strong").setReadOnly();

	public static final String CLASS = "class";

	/** Tests an HTML element name to determine if it is one of the elements permitted to be empty in the XHTML
	 * spec ( http://www.w3.org/TR/xhtml1/ )
	 * @param name The name of the html element
	 * @return True if the element is allowed to be empty
	 */
	private Boolean isEmptyElement(String name) {
		return EmptyTag.contains(name);
	}

	/** Tests an HTML element to determine if we should add a newline after the opening tag for readability
	 * @param name The name of the html element
	 * @return True if we should add a newline after the opening tag
	 */
	private Boolean newlineOpen(String name) {
		return OpenTags.contains(name);
	}

	/** Tests an HTML element to determine if we should add a newline after the closing tag for readability. All
	 * tags with newlines after the opening tag also get newlines afterthe closing tag.
	 * @param name The name of the html element
	 * @return True if we should add a newline after the opening tag
	 */
	private Boolean newlineClose(String name) {
		return (newlineOpen(name) || CloseTags.contains(name));
	}

	/** Returns a properly formatted closing angle bracket to complete an open tag of a named html element
	 * @param name the name of the element
	 * @return the proper string of characters to complete the open tag
	 */
	private String OpenSuffix(String name) {
		if (isEmptyElement(name)) {
			return " />";
		} else {
			return ">";
		}
	}

	/** Returns a closing tag for a named html element
	 * @param name the name of the element
	 * @return the complete closing tag for the element
	 */
	private String CloseTag(String name) {
		if (isEmptyElement(name)) {
			return "";
		} else {
			return "</" + name + ">";
		}
	}

	/** Returns a string containing a specified number of tab characters
	 * @param indentDepth the number of tab characters
	 * @return Returns a string suitable for indenting tags
	 */
	private String indentString(int indentDepth) {
		StringBuffer indentLine = new StringBuffer();

		for (int indentIndex = 0, indentCount = indentDepth+1; indentIndex < indentCount; indentIndex++) {
			indentLine.append('\t');
		}
		return indentLine.toString();
	}

	public HTMLNode setReadOnly() {
		readOnly = true;
		return this;
	}

	@Override
	public HTMLNode clone() {
		return new HTMLNode(this, true);
	}

	public HTMLNode(String name) {
		this(name, null);
	}

	public HTMLNode(String name, String content) {
		this(name, (String[]) null, (String[]) null, content);
	}

	public HTMLNode(String name, String attributeName, String attributeValue) {
		this(name, attributeName, attributeValue, null);
	}

	public HTMLNode(String name, String attributeName, String attributeValue, String content) {
		this(name, new String[] { attributeName }, new String[] { attributeValue }, content);
	}

	public HTMLNode(String name, String[] attributeNames, String[] attributeValues) {
		this(name, attributeNames, attributeValues, null);
	}

	public HTMLNode(String name, String[] attributeNames, String[] attributeValues, String content) {
		if ((name == null) || (!"#".equals(name) && !"%".equals(name) && !checkNamePattern(name))) {
			throw new IllegalArgumentException("element name is not legal");
		}
		addAttributes(attributeNames, attributeValues);
		this.name = name.toLowerCase(Locale.ENGLISH);
		if (content != null && !("#").equals(name)&& !("%").equals(name)) {
			addText(content);
			this.content = null;
		} else {
			this.content = content;
		}
	}

	protected HTMLNode(HTMLNode node, boolean clearReadOnly) {
		attributes.putAll(node.attributes);
		children.addAll(node.children);
		content = node.content;
		name = node.name;
		if(clearReadOnly) {
			readOnly = false;
		} else
			readOnly = node.readOnly;
	}

	protected boolean checkNamePattern(String str) {
		// Workaround buggy java regexes, also probably slightly faster.
		if(str.length() < 1) return false;
		char c;
		c = str.charAt(0);
		if((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
			boolean simpleMatch = true;
			for(int i=1;i<str.length();i++) {
				c = str.charAt(i);
				if(!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
					simpleMatch = false;
					break;
				}
			}
			if(simpleMatch) return true;
		}
		// Regex-based match. Probably more expensive, and problems (infinite recursion in Pattern$6.isSatisfiedBy) have been seen in practice.
		// Oddly these problems were seen where the answer is almost certainly in the first matcher, because the tag name was "html"...
		return simpleNamePattern.matcher(str).matches() || 
			namePattern.matcher(str).matches();
	}

	public void addAttributes(String[] attributeNames, String[] attributeValues) {
		if ((attributeNames != null) && (attributeValues != null)) {
			if (attributeNames.length != attributeValues.length) {
				throw new IllegalArgumentException("attribute names and values differ in length");
			}
			for (int attributeIndex = 0, attributeCount = attributeNames.length; attributeIndex < attributeCount; attributeIndex++) {
				if ((attributeNames[attributeIndex] == null) || !checkNamePattern(attributeNames[attributeIndex])) {
					throw new IllegalArgumentException("attributeName is not legal");
				}
				addAttribute(attributeNames[attributeIndex], attributeValues[attributeIndex]);
			}
		}
	}

	/**
	 * @return a single named attribute
	 */
	public String getAttribute(String attributeName) {
		return attributes.get(attributeName);
	}

	public boolean hasAttribute(final String attributeName) {
		return attributes.containsKey(attributeName);
	}

	/**
	 * @return all attributes
	 */
	public Map<String, String> getAttributes() {
		return Collections.unmodifiableMap(attributes);
	}

	public List<HTMLNode> getChildren(){
		return children;
	}

	/**
	 * @return the content
	 */
	public String getContent() {
		return content;
	}

	/**
	 * Returns the name of the first "real" tag found in the hierarchy below
	 * this node.
	 *
	 * @return The name of the first "real" tag, or <code>null</code> if no
	 *         "real" tag could be found
	 */
	public String getFirstTag() {
		if (!"#".equals(name)) {
			return name;
		}
		for (int childIndex = 0, childCount = children.size(); childIndex < childCount; childIndex++) {
			HTMLNode childNode = children.get(childIndex);
			String tag = childNode.getFirstTag();
			if (tag != null) {
				return tag;
			}
		}
		return null;
	}

	public void addAttribute(String attributeName, String attributeValue) {
		if(readOnly)
			throw new IllegalArgumentException("Read only");
		if (attributeName == null)
			throw new IllegalArgumentException("Cannot add an attribute with a null name");
		if (attributeValue == null)
			throw new IllegalArgumentException("Cannot add an attribute with a null value");
		attributes.put(attributeName, attributeValue);
	}

	public HTMLNode addChild(HTMLNode childNode) {
		if(readOnly)
			throw new IllegalArgumentException("Read only");
		if (childNode == null) throw new NullPointerException();
		//since an efficient algorithm to check the loop presence
		//is not present, at least it checks if we are trying to
		//addChild the node itself as a child
		if (childNode == this)
			throw new IllegalArgumentException("A HTMLNode cannot be child of himself");
		if (children.contains(childNode))
			throw new IllegalArgumentException("Cannot add twice the same HTMLNode as child");
		children.add(childNode);
		return childNode;
	}

	public HTMLNode addChild(String nodeName) {
		return addChild(nodeName, null);
	}

	public HTMLNode addChild(String nodeName, String content) {
		return addChild(nodeName, (String[]) null, (String[]) null, content);
	}

	public HTMLNode addChild(String nodeName, String attributeName, String attributeValue) {
		return addChild(nodeName, attributeName, attributeValue, null);
	}

	public HTMLNode addChild(String nodeName, String attributeName, String attributeValue, String content) {
		return addChild(nodeName, new String[] { attributeName }, new String[] { attributeValue }, content);
	}

	public HTMLNode addChild(String nodeName, String[] attributeNames, String[] attributeValues) {
		return addChild(nodeName, attributeNames, attributeValues, null);
	}

	public HTMLNode addChild(String nodeName, String[] attributeNames, String[] attributeValues, String content) {
		return addChild(new HTMLNode(nodeName, attributeNames, attributeValues, content));
	}

	public void addChildren(HTMLNode[] childNodes) {
		if(readOnly)
			throw new IllegalArgumentException("Read only");
		for (int i = 0, c = childNodes.length; i < c; i++) {
			addChild(childNodes[i]);
		}
	}

	/**
	 * Add a html class to the list of class attributes.<br/>
	 * A duplicate class attribute will not be added.<br/>
	 * As classes are space-separated, if the class name has spaces multiple classes will be added.
	 *
	 * @param className class to add.
	 */
	public void addClass(final Category className) {
		/*
		 * Each class is bookended with a space to avoid mistaking parts of existing classes as the new class.
		 */
		final String bookended = ' ' + className.name + ' ';
		if (this.hasAttribute(CLASS)) {
			final String classes = attributes.get(CLASS);
			if (classes.contains(bookended)) return;
			attributes.put(CLASS, bookended.concat(classes));
		} else {
			attributes.put(CLASS, bookended);
		}
	}
	@Deprecated
	public void addClass(final String className) {
		final String bookended = ' ' + className + ' ';
		if (this.hasAttribute(CLASS)) {
			final String classes = attributes.get(CLASS);
			if (classes.contains(bookended)) return;
			attributes.put(CLASS, bookended.concat(classes));
		} else {
			attributes.put(CLASS, bookended);
		}
	}

	/**
	 * Add an onBlur attribute to a tag.
	 * @param script
	 */
	public void onBlur(String script) {
		attributes.put("onBlur", script);
	}

	/**
	 * Add an onChange attribute to a tag.
	 * @param script
	 */
	public void onChange(String script) {
		attributes.put("onChange", script);
	}


	/**
	 * Set the html "id" attribute
	 * @param tagID ID attribute to be set
	 */
	public void setID(final Identifier tagID) {
		attributes.put("id", tagID.name);
	}

	public String generate() {
		StringBuilder tagBuffer = new StringBuilder();
		return generate(tagBuffer).toString();
	}

	public StringBuilder generate(StringBuilder tagBuffer) {
		return generate(tagBuffer,0);
	}

	public StringBuilder generate(StringBuilder tagBuffer, int indentDepth ) {
		if("#".equals(name)) {
			if(content != null) {
				HTMLEncoder.encodeToBuffer(content, tagBuffer);
				return tagBuffer;
			}
			
			for(int childIndex = 0, childCount = children.size(); childIndex < childCount; childIndex++) {
				HTMLNode childNode = children.get(childIndex);
				childNode.generate(tagBuffer);
			}
			return tagBuffer;
		}
		// Perhaps this should be something else, but since I don't know if '#' was not just arbitrary chosen, I'll just pick '%'
		// This allows non-encoded text to be appended to the tag buffer
		if ("%".equals(name)) {
			tagBuffer.append(content);
			return tagBuffer;
		}
		/* start the open tag */
		tagBuffer.append('<').append(name);
		/* add attributes*/
		Set<Map.Entry<String, String>> attributeSet = attributes.entrySet();
		for (Map.Entry<String, String> attributeEntry : attributeSet) {
			String attributeName = attributeEntry.getKey();
			String attributeValue = attributeEntry.getValue();
			tagBuffer.append(' ');
			HTMLEncoder.encodeToBuffer(attributeName, tagBuffer);
			tagBuffer.append("=\"");
			HTMLEncoder.encodeToBuffer(attributeValue, tagBuffer);
			tagBuffer.append('"');
		}
		/* complete the open tag*/
		tagBuffer.append(OpenSuffix(name));
		/*insert the contents*/
		if (children.size() == 0) {
			if(content==null) {
			} else {
				HTMLEncoder.encodeToBuffer(content, tagBuffer);
			}
		} else {
			if (newlineOpen(name)) {
				tagBuffer.append('\n');
				tagBuffer.append(indentString(indentDepth+1));
			}
			for (int childIndex = 0, childCount = children.size(); childIndex < childCount; childIndex++) {
				HTMLNode childNode = children.get(childIndex);
				childNode.generate(tagBuffer,indentDepth+1);
			}
		}
		/* add a closing tag */
		if (newlineOpen(name)) {
			tagBuffer.append('\n');
			tagBuffer.append(indentString(indentDepth));
		}
		tagBuffer.append(CloseTag(name));
		if (newlineClose(name)) {
			tagBuffer.append('\n');
			tagBuffer.append(indentString(indentDepth));
		}
		return tagBuffer;
	}

	public String generateChildren(){
		if(content!=null){
			return content;
		}
		StringBuilder tagBuffer=new StringBuilder();
		for(int childIndex = 0, childCount = children.size(); childIndex < childCount; childIndex++) {
			HTMLNode childNode = children.get(childIndex);
			childNode.generate(tagBuffer);
		}
		return tagBuffer.toString();
	}

	public void setContent(String newContent){
		if(readOnly)
			throw new IllegalArgumentException("Read only");
		content=newContent;
	}

	/**
	 * Special HTML node for the DOCTYPE declaration. This node differs from a
	 * normal HTML node in that it's child (and it should only have exactly one
	 * child, the "html" node) is rendered <em>after</em> this node.
	 *
	 * @author David 'Bombe' Roden &lt;bombe@freenetproject.org&gt;
	 * @version $Id$
	 */
	public static class HTMLDoctype extends HTMLNode {

		private final String systemUri;

		/**
		 *
		 */
		public HTMLDoctype(String doctype, String systemUri) {
			super(doctype);
			this.systemUri = systemUri;
		}

		public StringBuilder generate(StringBuilder tagBuffer) {
			tagBuffer.append("<!DOCTYPE ").append(name).append(" PUBLIC \"").append(systemUri).append("\">\n");
			//TODO A meaningful exception should be raised
			// when trying to call the method for a HTMLDoctype
			// with number of child != 1
			return children.get(0).generate(tagBuffer);
		}

	}

	@Deprecated
	public static HTMLNode link(String path) {
		return new HTMLNode("a", "href", path);
	}

	@Deprecated
	public static HTMLNode linkInNewWindow(String path) {
		return new HTMLNode("a", new String[] { "href", "target" }, new String[] { path, "_blank" });
	}

	@Deprecated
	public static HTMLNode text(String text) {
		return new HTMLNode("#", text);
	}

	@Deprecated
	public static HTMLNode text(int count) {
		return new HTMLNode("#", Integer.toString(count));
	}

	@Deprecated
	public static HTMLNode text(long count) {
		return new HTMLNode("#", Long.toString(count));
	}

	@Deprecated
	public static HTMLNode text(short count) {
		return new HTMLNode("#", Short.toString(count));
	}

	//methods for creating Box children
	public Box addBox() {
		Box newBox = new Box();
		addChild(newBox);
		return newBox;
	}
	public Box addBox(Box newBox) {
		addChild(newBox);
		return newBox;
	}
	public Box addBox(Identifier id) {
		Box newBox = new Box(id);
		addChild(newBox);
		return newBox;
	}
	public Box addBox(Identifier id, String content) {
		Box newBox = new Box(id, content);
		addChild(newBox);
		return newBox;
	}
	public Box addBox(Category category) {
		Box newBox = new Box(category);
		addChild(newBox);
		return newBox;
	}
	public Box addBox(Category category, String content) {
		Box newBox = new Box(category, content);
		addChild(newBox);
		return newBox;
	}

	//methods for creating blockText children
	public BlockText addBlockText() {
		BlockText newBlockText = new BlockText();
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(String content) {
		BlockText newBlockText = new BlockText(content);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(Identifier id) {
		BlockText newBlockText = new BlockText(id);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(Identifier id, String content) {
		BlockText newBlockText = new BlockText(id, content);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(Category category) {
		BlockText newBlockText = new BlockText(category);
		addChild(newBlockText);
		return newBlockText;
	}
	public BlockText addBlockText(Category category, String content) {
		BlockText newBlockText = new BlockText(category, content);
		addChild(newBlockText);
		return newBlockText;
	}

	//methods for creating Form children
	public Form addForm(String action, String method) {
		Form form = new Form(action, method);
		addChild(form);
		return form;
	}
	public Form addForm(String action, String method, Target target) {
		Form form = new Form(action, method, target);
		addChild(form);
		return form;
	}
	public Form addForm(String action, String method, String enctype, String charset, Identifier id) {
		Form form = new Form(action, method, enctype, charset, id);
		addChild(form);
		return form;
	}
	@Deprecated
	public Form addForm(String action, String method, String enctype, String charset, String id) {
		Form form = new Form(action, method, enctype, charset, id);
		addChild(form);
		return form;
	}
	public Form addForm(String action, String method, String enctype, String charset, Identifier id, String name) {
		Form form = new Form(action, method, enctype, charset, id, name);
		addChild(form);
		return form;
	}
	@Deprecated
	public Form addForm(String action, String method, String enctype, String charset, String id, String name) {
		Form form = new Form(action, method, enctype, charset, id, name);
		addChild(form);
		return form;
	}

	//methods for creating Image children
	public Image addImage(String src, String alt) {
		Image newImage = new Image(src, alt);
		addChild(newImage);
		return newImage;
	}
	public Image addImage(String src, String alt, String title) {
		Image newImage = new Image(src, alt, title);
		addChild(newImage);
		return newImage;
	}
	public Image addImage(String src, String alt, String title, int height, int width) {
		Image newImage = new Image(src, alt, title, height, width);
		addChild(newImage);
		return newImage;
	}

	//methods for creating InlineBox children
	public InlineBox addInlineBox() {
		InlineBox newInlineBox = new InlineBox();
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(InlineBox newInlineBox) {
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String content) {
		InlineBox newInlineBox = new InlineBox(content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(Identifier id) {
		InlineBox newInlineBox = new InlineBox(id);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(Identifier id, String content) {
		InlineBox newInlineBox = new InlineBox(id, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String title, Category category) {
		InlineBox newInlineBox = new InlineBox(title, category);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(Category category) {
		InlineBox newInlineBox = new InlineBox(category);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(Category category, String content) {
		InlineBox newInlineBox = new InlineBox(category, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(Category category, String title, String content) {
		InlineBox newInlineBox = new InlineBox(category, title, content);
		addChild(newInlineBox);
		return newInlineBox;
	}
	public InlineBox addInlineBox(String style, Category category, String content) {
		InlineBox newInlineBox = new InlineBox(style, category, content);
		addChild(newInlineBox);
		return newInlineBox;
	}

	//methods for creating Infobox children
	public Infobox addInfobox(Infobox newInfobox) {
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(String title) {
		Infobox newInfobox = new Infobox(title);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, String title) {
		Infobox newInfobox = new Infobox(type, title);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Identifier id, String title) {
		Infobox newInfobox = new Infobox(type, id, title);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Identifier id, String title, OutputNode content) {
		Infobox newInfobox = new Infobox(type, id, title, content);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Identifier id, String title, String content) {
		Infobox newInfobox = new Infobox(type, id, title, content);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(Identifier id, String title) {
		Infobox newInfobox = new Infobox(id, title);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Category category, String title) {
		Infobox newInfobox = new Infobox(type, category, title);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Category category, String title, OutputNode content) {
		Infobox newInfobox = new Infobox(type, category, title, content);
		addChild(newInfobox);
		return newInfobox;
	}
	public Infobox addInfobox(InfoboxType type, Category category, String title, String content) {
		Infobox newInfobox = new Infobox(type, category, title, content);
		addChild(newInfobox);
		return newInfobox;
	}

	//methods for creating input elements
	public Input addInput(Input input){
		this.addChild(input);
		return input;
	}
	public Input addInput(InputType type){
		Input newInput = new Input(type);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(String name){
		Input newInput = new Input(name);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String value){
		Input newInput = new Input(type, value);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(String name, InputType type){
		Input newInput = new Input(name, type);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, int size){
		Input newInput = new Input(type, name, size);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, Category category){
		Input newInput = new Input(type, name, category);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, boolean checked){
		Input newInput = new Input(type, name, checked);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, short maxlength){
		Input newInput = new Input(type, name, maxlength);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, Identifier id){
		Input newInput = new Input(type, name, id);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value){
		Input newInput = new Input(type, name, value);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, Identifier id){
		Input newInput = new Input(type, name, value, id);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, Category category){
		Input newInput = new Input(type, name, value, category);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, boolean checked){
		Input newInput = new Input(type, name, value, checked);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, boolean checked, Identifier id){
		Input newInput = new Input(type, name, checked, id);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, int size, Identifier id){
		Input newInput = new Input(type, name, size, id);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, Identifier id, int size) {
		Input newInput = new Input(type, name, value, id, size);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, Category category, String alt) {
		Input newInput = new Input(type, name, value, category, alt);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, int size, short maxlength) {
		Input newInput = new Input(type, name, value, size, maxlength);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, int size, short maxlength, Identifier id) {
		Input newInput = new Input(type, name, value, size, maxlength, id);
		this.addChild(newInput);
		return newInput;
	}
	public Input addInput(InputType type, String name, String value, Category category, String alt, boolean disabled) {
		Input newInput = new Input(type, name, value, category, alt, disabled);
		this.addChild(newInput);
		return newInput;
	}

	//methods for creating line break elements
	public LineBreak addLineBreak() {
		LineBreak newLineBreak = new LineBreak();
		addChild(newLineBreak);
		return newLineBreak;
	}

	//methods for creating Link children
	public Link addLink() {
		Link newLink = new Link();
		addChild(newLink);
		return newLink;
	}
	public Link addLink(Link newLink) {
		addChild(newLink);
		return newLink;
	}
	public Link addLink(LinkType type, Identifier data) {
		Link newLink = new Link(type, data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(LinkType type, String data) {
		Link newLink = new Link(type, data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(LinkType type, String data, String id) {
		Link newLink = new Link(type, data, id);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(Category category, String title, String content) {
		Link newLink = new Link(category, title, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data) {
		Link newLink = new Link(data);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String content) {
		Link newLink = new Link(data, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, Category category, String content) {
		Link newLink = new Link(data, category, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String title, String content) {
		Link newLink = new Link(data, title, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, Target target,  String content) {
		Link newLink = new Link(data, target, content);
		addChild(newLink);
		return newLink;
	}
	public Link addLink(String data, String title, Category category, String content) {
		Link newLink = new Link(data, title, category, content);
		addChild(newLink);
		return newLink;
	}

	//methods for creating List children
	public OutputList addList() {
		OutputList newList = new OutputList();
		addChild(newList);
		return newList;
	}
	public OutputList addList(OutputList newList) {
		addChild(newList);
		return newList;
	}
	public OutputList addList(Category category) {
		OutputList newList = new OutputList(category);
		addChild(newList);
		return newList;
	}
	public OutputList addList(Identifier id) {
		OutputList newList = new OutputList(id);
		addChild(newList);
		return newList;
	}
	public OutputList addList(ListType type, Category category) {
		OutputList newList = new OutputList(type, category);
		addChild(newList);
		return newList;
	}

	//methods for creating Noscript children
	public Noscript addNoscript() {
		Noscript newNoscript = new Noscript();
		addChild(newNoscript);
		return newNoscript;
	}

	//methods for creating Table children
	public Table addTable() {
		Table newTable = new Table();
		addChild(newTable);
		return newTable;
	}
	public Table addTable(Table newTable) {
		addChild(newTable);
		return newTable;
	}
	public Table addTable(Category category) {
		Table newTable = new Table(category);
		addChild(newTable);
		return newTable;
	}

	//methods for creating Text children
	public Text addText() {
		Text newText = new Text();
		addChild(newText);
		return newText;
	}
	public Text addText(String content) {
		Text newText = new Text(content);
		addChild(newText);
		return newText;
	}
}
