package freenet.clients.http.uielements;

public class Doctype extends OutputNode {

	private final String systemUri;

	public Doctype(String doctype, String systemUri) {
		super(doctype);
		this.systemUri = systemUri;
	}

	public StringBuilder generate(StringBuilder tagBuffer) {
		tagBuffer.append("<!DOCTYPE ").append(name).append(" PUBLIC \"").append(systemUri).append("\">\n");
		return children.get(0).generate(tagBuffer);
	}
}
