package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP Message sent upon completion of a probe which contains information on the
 * closest location reached which was greater than the target.
 */
public class ProbeCompleteMessage extends FCPMessage {
	public static final String NAME = "ProbeComplete";

	private final SimpleFieldSet fs;

	public ProbeCompleteMessage(String identifier, String reason, double target, double best, double nearest, long id, short counter, short uniqueCounter, short linearCounter) {
		fs = new SimpleFieldSet(true);

		//Identifier client specified in request for matching up with responses.
		fs.putOverwrite(FCPMessage.IDENTIFIER, identifier);

		//(?)
		fs.putOverwrite(DMT.REASON, reason);

		//Probe target location.
		fs.put(DMT.TARGET_LOCATION, target);

		//(? How does this differ from nearest?)
		fs.put(DMT.BEST_LOCATION, best);

		//Nearest greater location relative to target.
		fs.put(DMT.NEAREST_LOCATION, nearest);

		//UID of node which started probe.
		fs.put(DMT.UID, id);

		//(?)
		fs.put(DMT.COUNTER, counter);

		//(?)
		fs.put(DMT.UNIQUE_COUNTER, uniqueCounter);

		//(?)
		fs.put(DMT.LINEAR_COUNTER, linearCounter);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		return fs;
	}

	@Override
	public void run(FCPConnectionHandler handler, Node node) throws MessageInvalidException {
		throw new MessageInvalidException(ProtocolErrorMessage.INVALID_MESSAGE, getName()+" is a reply from the node; the client should not send it.", null, false);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}
}
