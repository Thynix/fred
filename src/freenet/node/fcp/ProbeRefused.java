package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which indicates that the endpoint has opted not to respond to the
 * request type. This is opposed to just forwarding it again or letting it time out, which would bias the endpoints
 */
public class ProbeRefused extends FCPMessage {
	public static String NAME = "ProbeRefused";
	private SimpleFieldSet fs;

	public ProbeRefused(String fcpIdentifier) {
		fs = new SimpleFieldSet(true);
		fs.putOverwrite(FCPMessage.IDENTIFIER, fcpIdentifier);
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
