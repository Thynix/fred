package freenet.clients.http;

import freenet.support.HTMLNode;

@Deprecated
public class InfoboxNode {

	public final HTMLNode outer;
	public final HTMLNode content;

	InfoboxNode(HTMLNode box, HTMLNode content) {
		this.outer = box;
		this.content = content;
	}

}
