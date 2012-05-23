package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which includes outgoing bandwidth limit returned by the endpoint.
 */
public class ProbeBandwidth extends FCPMessage {
	public static String NAME = "ProbeBandwidth";
	private SimpleFieldSet fs;

	/**
	 *
	 * @param fcpIdentifier FCP-level identifier for pairing requests and responses
	 * @param outputBandwidth reported endpoint output bandwidth limit in KiB per second.
	 */
	public ProbeBandwidth(String fcpIdentifier, long outputBandwidth) {
		fs = new SimpleFieldSet(true);
		fs.putOverwrite(FCPMessage.IDENTIFIER, fcpIdentifier);
		fs.put(DMT.OUTPUT_BANDWIDTH_UPPER_LIMIT, outputBandwidth);
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
