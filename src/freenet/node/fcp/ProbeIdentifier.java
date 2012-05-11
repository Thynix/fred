package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which includes an identifier returned by the endpoint.
 */
//TODO: FCPMessage
public class ProbeIdentifier extends FCPMessage {
	public static String NAME = "ProbeDisconnected";
	private SimpleFieldSet fs;

	public ProbeIdentifier(long uid, long identifier) {
		fs = new SimpleFieldSet(true);
		fs.put(DMT.UID, uid);
		fs.put(DMT.IDENTIFIER, identifier);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		return fs;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void run(FCPConnectionHandler handler, Node node) throws MessageInvalidException {
		throw new MessageInvalidException(ProtocolErrorMessage.INVALID_MESSAGE, getName()+" is a reply from the node; the client should not send it.", null, false);
	}

	@Override
	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}
}
