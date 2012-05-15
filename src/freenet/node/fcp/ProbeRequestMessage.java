package freenet.node.fcp;

import com.db4o.ObjectContainer;
import freenet.io.comm.DMT;
import freenet.node.FSParseException;
import freenet.node.MHProbe;
import freenet.node.Node;
import freenet.support.SimpleFieldSet;

/**
 * FCP Message which is received from a client and requests a network probe of a specific type.
 * type: Mandatory: denotes the desired response type.
 *                  Valid values are:
 *                  IDENTIFIER - returns swap identifier.
 *                  LINK_LENGTHS - returns (with random noise) link lengths between the endpoint and its peers
 * hopsToLive: Optional; approximately how many hops the probe will take before possibly returning a result.
 *                       Valid values are [1, MHProbe.MAX_HTL]. If omitted MHProbe.MAX_HTL is used.
 */
public class ProbeRequestMessage extends FCPMessage {
	public static String NAME = "ProbeRequest";

	private final SimpleFieldSet fs;
	private final String identifier;

	public ProbeRequestMessage(SimpleFieldSet fs) throws MessageInvalidException {
		this.fs = fs;
		this.identifier = fs.get(FCPMessage.IDENTIFIER);
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
		if(!handler.hasFullAccess()) {
			throw new MessageInvalidException(ProtocolErrorMessage.ACCESS_DENIED, "Probe requires full access.", identifier, false);
		}

		try {
			MHProbe.ProbeType type =  MHProbe.ProbeType.valueOf(fs.get(DMT.TYPE));
			final short htl = fs.getShort(DMT.HTL);
			MHProbe.Listener listener = new MHProbe.Listener() {
				@Override
				public void onTimeout() {
					handler.outputHandler.queue(new ProbeTimeout(identifier));
				}

				@Override
				public void onDisconnected() {
					handler.outputHandler.queue(new ProbeDisconnected(identifier));
				}

				@Override
				public void onIdentifier(long probeIdentifier) {
					handler.outputHandler.queue(new ProbeIdentifier(identifier, probeIdentifier));
				}

				/*@Override
				public void onUptime(long sessionUptime, double uptime48hour) {
					handler.outputHandler.queue();
				}

				@Override
				public void onOutputBandwidth(int outputBandwidth) {
					handler.outputHandler.queue();
				}

				@Override
				public void onStoreSize(int storeSize) {
					handler.outputHandler.queue();
				}*/

				@Override
				public void onLinkLengths(Double[] linkLengths) {
					handler.outputHandler.queue(new ProbeLinkLengths(identifier, linkLengths));
				}
			};
			node.dispatcher.mhProbe.start(htl, node.random.nextLong(), type, listener);
		} catch (IllegalArgumentException e) {
			throw new MessageInvalidException(ProtocolErrorMessage.INVALID_MESSAGE, "Unrecognized parse probe type \"" + fs.get(DMT.TYPE) + "\":" + e, null, false);
		} catch (FSParseException e) {
			throw new MessageInvalidException(ProtocolErrorMessage.INVALID_MESSAGE, "Unrecognized parse probe type \"" + fs.get(DMT.TYPE) + "\":" + e, null, false);
		}
	}
}
