package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.FSParseException;
import freenet.node.Node;
import freenet.node.ProbeCallback;
import freenet.support.SimpleFieldSet;

/**
 * FCP Message which is received from a client and requests a network probe to
 * a target location.
 */
public class ProbeMessage extends FCPMessage {
	public static final String NAME = "Probe";

	private final SimpleFieldSet fs;
	private final String identifier;

	public ProbeMessage(SimpleFieldSet fs) throws MessageInvalidException {
		this.fs = fs;
		identifier = fs.get(FCPMessage.IDENTIFIER);
		//TODO: Why remove this? Following example set by ListPeerMessage.
		fs.removeValue(FCPMessage.IDENTIFIER);
	}

	@Override
	public SimpleFieldSet getFieldSet() {
		return new SimpleFieldSet(true);
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void run(final FCPConnectionHandler handler, Node node) throws MessageInvalidException {
		//Check request validity.
		double target;
		if(!handler.hasFullAccess()) {
			throw new MessageInvalidException(ProtocolErrorMessage.ACCESS_DENIED, "Probe requires full access.", identifier, false);
		}
		try {
			target = fs.getDouble(DMT.TARGET_LOCATION);
		} catch (FSParseException e) {
			throw new MessageInvalidException(ProtocolErrorMessage.ERROR_PARSING_NUMBER, "Unable to parse location as a double.", identifier, false);
		}
		//Valid locations are (0, 1]: 0.0 up to and excluding 1.0.
		if (target < 0.0d || target >= 1.0d) {
			throw new MessageInvalidException(ProtocolErrorMessage.INVALID_FIELD, "Requested target outside valid range of (0, 1].", identifier, false);
		}

		//Send results as they arrive.
		//TODO: Might want to delay sending completion so that more traces can arrive.
		ProbeCallback cb = new ProbeCallback() {
			@Override
			public void onCompleted(String reason, double target, double best, double nearest, long id, short counter, short uniqueCounter, short linearCounter) {
				handler.outputHandler.queue(new ProbeCompleteMessage(identifier, reason, target, best, nearest, id, counter, uniqueCounter, linearCounter));
			}

			@Override
			public void onTrace(long uid, double target, double nearest, double best, short htl, short counter, double location, long nodeUID, double[] peerLocs, long[] peerUIDs, double[] locsNotVisited, short forkCount, short linearCounter, String reason, long prevUID) {
				handler.outputHandler.queue(new ProbeTraceMessage(identifier, uid, target, nearest, best, htl, counter, location, nodeUID, peerLocs, peerUIDs, locsNotVisited, forkCount, linearCounter, reason, prevUID));
			}

			@Override
			public void onRejectOverload() {
				//TODO: What to do here? No information other than that a rejection occurred.
			}
		};

		node.dispatcher.startProbe(target, cb);
	}

}
