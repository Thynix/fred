package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.MHProbe;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP message sent from the node to the client which indicates that an error has occurred.
 * These are propagated so that resources can be freed on error more quickly than they would with just a timeout.
 */
public class ProbeError extends FCPMessage {
	public static String NAME = "ProbeError";
	private final SimpleFieldSet fs;

	/**
	 * An error was received.
	 * @param fcpIdentifier Identifier: FCP-level identifier for pairing requests and responses.
	 * @param error type: The error code.
	 * @param description description: If the error is UNKNOWN, specifies remote error text. Not defined otherwise.
	 * @see MHProbe.Listener onError()
	 * @see MHProbe.ProbeError
	 */
	public ProbeError(String fcpIdentifier, MHProbe.ProbeError error, String description) {
		fs = new SimpleFieldSet(true);
		fs.putOverwrite(FCPMessage.IDENTIFIER, fcpIdentifier);
		fs.putOverwrite(DMT.TYPE, error.name());
		fs.putOverwrite(DMT.DESCRIPTION, description);
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
