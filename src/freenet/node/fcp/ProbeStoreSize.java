package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which includes store size returned by the endpoint.
 */
public class ProbeStoreSize extends FCPMessage {
	public static String NAME = "ProbeStoreSize";
	private SimpleFieldSet fs;

	/**
	 *
	 * @param fcpIdentifier FCP-level identifier for pairing requests and responses
	 * @param storeSize reported endpoint store size in bytes.
	 */
	public ProbeStoreSize(String fcpIdentifier, long storeSize) {
		fs = new SimpleFieldSet(true);
		fs.putOverwrite(FCPMessage.IDENTIFIER, fcpIdentifier);
		fs.put(DMT.STORE_SIZE, storeSize);
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
