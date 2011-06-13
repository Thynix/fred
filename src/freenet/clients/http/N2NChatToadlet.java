package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.node.N2NChatRoom;
import freenet.node.Node;

public class N2NChatToadlet extends Toadlet {

	//TODO: Which chat room to render: ?room={globalIdentifier}

	private Node node;

	public N2NChatToadlet(Node node, HighLevelSimpleClient client) {
		super(client);
		this.node = node;
	}

	public String path() {
		return "/n2nchat/";
	}


}
