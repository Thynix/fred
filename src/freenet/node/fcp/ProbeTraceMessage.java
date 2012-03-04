package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP Message which contains a probe trace. Field names as present for a ProbeCallback and named as in comm.io.DMT
 * with the exception of "locations not visited" because it is hard-coded as empty in NodeDispatcher.
 */
public class ProbeTraceMessage extends FCPMessage {
	public static final String NAME = "ProbeTrace";

	private final SimpleFieldSet fs;

	public ProbeTraceMessage(String identifier, long uid, double target, double nearest, double best, short htl, short counter, double location, long nodeUID, double[] peerLocs, long[] peerUIDs, double[] locsNotVisited, short forkCount, short linearCounter, String reason, long prevUID) {
		fs = new SimpleFieldSet(true);

		/*
		 * Cannot use map to reduce code duplication in adding to field set: different types are required, and SFS cannot
		 * accept objects.
		 */

		//Identifier client specified in request for matching up with responses.
		fs.putOverwrite(FCPMessage.IDENTIFIER, identifier);

		//UID of node which started probe.
		fs.put(DMT.UID, uid);

		//Probe target location.
		fs.put(DMT.TARGET_LOCATION, target);

		//Nearest peer location to target. (?)
		fs.put(DMT.NEAREST_LOCATION, nearest);

		//Closest known location to target. (?)
		fs.put(DMT.BEST_LOCATION, best);

		//Hops to live.
		fs.put(DMT.HTL, htl);

		//(?)
		fs.put(DMT.COUNTER, counter);

		//Location of the node described by the trace.
		fs.put(DMT.LOCATION, location);

		//UID of the node described by the trace.
		fs.put(DMT.UID, nodeUID);

		//Reported locations of the peers of the node described by the trace.
		fs.put(DMT.PEER_LOCATIONS, peerLocs);

		//Reported UIDs of the peers of the node described by the trace.
		fs.put(DMT.PEER_UIDS, peerUIDs);

		//Locations not visited is unused: in NodeDispatcher's call to a ProbeCallback's onTrace() it is always empty.

		//(?)
		fs.put(DMT.FORK_COUNT, forkCount);

		//(?)
		fs.put(DMT.LINEAR_COUNTER, linearCounter);

		//(?)
		fs.putOverwrite(DMT.REASON, reason);

		//Previous UID of the node, (? It doesn't appear to be the previous trace in the chain.)
		fs.put(DMT.PREV_UID, prevUID);
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
