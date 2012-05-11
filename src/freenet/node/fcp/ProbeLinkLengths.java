package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which includes link lengths reported by the endpoint.
 */
//TODO: FCPMessage
public class ProbeLinkLengths extends FCPMessage {
	public static String NAME = "ProbeDisconnected";
	private SimpleFieldSet fs;

	public ProbeLinkLengths(long uid, Double[] linkLengths) {
		fs.put(DMT.UID, uid);
		//TODO: Arg, types. Can put double[] but not Double[]. Add this.
		double[] convert = new double[linkLengths.length];
		for (int i = 0; i < linkLengths.length; i++) convert[i] = linkLengths[i];
		fs.put(DMT.LINK_LENGTHS, convert);
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
