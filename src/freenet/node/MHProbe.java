package freenet.node;

import freenet.io.comm.AsyncMessageFilterCallback;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.DisconnectedException;
import freenet.io.comm.Message;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.io.comm.PeerContext;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles starting, routing, and responding to Metropolis-Hastings corrected probes.
 * Instantiated for each outgoing probe; incoming probes are dealt with by one instance thereof.
 *
 * Possible future additions to these probes' results include:
 * - 7-day uptime percentage
 * - Checking whether a key is present in the datastore, either just at the endpoint or at each probe along the way.
 * - Success rates for remote requests by HTL.
 *     - not just for the present moment but over some amount of time.
 */
public class MHProbe implements ByteCounter {

	private static volatile boolean logMINOR;
	private static volatile boolean logDEBUG;

	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(Logger.LogLevel.MINOR, this);
				logDEBUG = Logger.shouldLog(Logger.LogLevel.DEBUG, this);
			}
		});
		pendingProbes = Collections.synchronizedSet(new HashSet<Long>());
	}

	/* TODO: A terrible hack to limit the number of pending probes. Is there a better way? It has to be accessible
	 * from callbacks.
	 */
	public static final int MAX_ACCEPTED = 5;
	final public static Set<Long> pendingProbes;

	/**
	 * Listener for the different types of probe results.
	 */
	public interface Listener {
		/**
		 * Probe response has timed out.
		 */
		void onTimeout();

		/**
		 * Peer from which response is expected has disconnected.
		 */
		void onDisconnected();

		/**
		 * Identifier result.
		 * @param identifier identifier given by endpoint
		 */
		void onIdentifier(long identifier);

		/**
		 * Uptime result.
		 * @param sessionUptime endpoint's reported session uptime in milliseconds.
		 * @param uptime48hour endpoint's reported percentage uptime in the last 48 hours.
		 */
		void onUptime(long sessionUptime, double uptime48hour);

		/**
		 * Build result.
		 * @param build endpoint's reported build / main version.
		 */
		void onBuild(int build);

		/**
		 * Output bandwidth limit result.
		 * @param outputBandwidth endpoint's reported output bandwidth limit in bytes per second.
		 */
		void onOutputBandwidth(long outputBandwidth);

		/**
		 * Store size result.
		 * @param storeSize endpoint's reported store size in bytes.
		 */
		void onStoreSize(long storeSize);

		/**
		 * Link length result.
		 * @param linkLengths endpoint's reported link lengths.
		 */
		void onLinkLengths(double[] linkLengths);
	}

	/**
	 * Applies random noise proportional to the input value.
	 * @param input Value to apply noise to.
	 * @return Value +/- up to 1% of itself.
	 */
	private double randomNoise(double input) {
		double part = input * (node.random.nextDouble() * 0.01);
		return node.random.nextBoolean() ? input + part : input - part;
	}

	/**
	 * Applies random noise proportional to the input value.
	 * @param input Value to apply noise to.
	 * @return Value +/- up to 1% of itself.
	 */
	private long randomNoise(long input) {
		long part = Math.round(input * (node.random.nextDouble() * 0.01));
		return node.random.nextBoolean() ? input + part : input - part;
	}

	public enum ProbeType {
		BANDWIDTH,
		BUILD,
		HTL,
		IDENTIFIER,
		LINK_LENGTHS,
		STORE_SIZE,
		UPTIME
	}

	/**
	 * Counts as probe request transfer.
	 * @param bytes Bytes received.
	 */
	@Override
	public void sentBytes(int bytes) {
		node.nodeStats.probeRequestCtr.sentBytes(bytes);
	}

	/**
	 * Counts as probe request transfer.
	 * @param bytes Bytes received.
	 */
	@Override
	public void receivedBytes(int bytes) {
		node.nodeStats.probeRequestCtr.receivedBytes(bytes);
	}

	/**
	 * No payload in probes.
	 * @param bytes Ignored.
	 */
	@Override
	public void sentPayload(int bytes) {}

	public static final short MAX_HTL = 50;
	/**
	 * In ms, per HTL.
	 */
	public static final int TIMEOUT_PER_HTL = 5000;

	private final Node node;

	public MHProbe(Node node) {
		this.node = node;
	}

	/**
	 * Sends an outgoing probe request.
	 * @param htl htl for this outgoing probe: should be [1, MAX_HTL]
	 * @param listener Something which implements MHProbe.Listener and will be called with results.
	 * @see MHProbe.Listener
	 */
	public void start(final short htl, final long uid, final ProbeType type, final Listener listener) {
		Message request = DMT.createMHProbeRequest(htl, uid, type);
		request(request, null, new ResultListener(listener, uid));
	}

	//TODO: Localization
	private final static String sourceDisconnect = "Previous step in probe chain no longer connected.";

	/**
	 * Same as its three-argument namesake, but responds to results by passing them on to source.
	 * @param message probe request, (possibly made by DMT.createMHProbeRequest) containing HTL
	 * @param source node from which the probe request was received. Used to relay back results.
	 */
	public void request(Message message, PeerNode source) {
		request(message, source, new ResultRelay(source, message.getLong(DMT.UID), this));
	}

	/**
	 * Processes an incoming probe request.
	 * If the probe has a positive HTL, routes with MH correction and probabilistically decrements HTL.
	 * If the probe comes to have an HTL of zero: (an incoming HTL of zero is taken to be one.)
	 *     returns as node settings allow at random exactly one of:
	 *         TODO: config options to disable
	 *         -unique identifier (not UID)
	 *         -uptime: session and 48-hour percentage,
	 *         -output bandwidth
	 *         -store size
	 *         -link lengths
	 *         -build number
	 * @param message probe request, containing HTL
	 * @param source node from which the probe request was received. Used to relay back results. If null, it is
	 *               considered to have been sent from the local node.
	 * @param callback callback for probe response
	 */
	public void request(final Message message, final PeerNode source, final AsyncMessageFilterCallback callback) {
		final Long uid = message.getLong(DMT.UID);
		ProbeType type;
		try {
			type = ProbeType.valueOf(message.getString(DMT.TYPE));
			if (logDEBUG) Logger.debug(MHProbe.class, "Probe type is " + type.name() + ".");
		} catch (IllegalArgumentException e) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Invalid probe type.", e);
			return;
		}
		if (!pendingProbes.contains(uid) && pendingProbes.size() >= MAX_ACCEPTED) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Already accepted maximum number of probes; rejecting incoming.");
			return;
		} else if (!pendingProbes.contains(uid)) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Accepting probe with uid " + uid + ".");
			pendingProbes.add(uid);
		}
		short htl = message.getShort(DMT.HTL);
		if (htl < 0) {
			if (logDEBUG) Logger.debug(MHProbe.class, "HTL cannot be negative; rejecting probe.");
			return;
		} else if (htl == 0) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Interpreting HTL of 0 as 1.");
			htl = 1;
		} else if (htl > MAX_HTL) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Capping HTL of " + htl + " at " + MAX_HTL + ".");
			htl = MAX_HTL;
		}

		/*
		 * Route to a peer, using Metropolis-Hastings correction and ignoring backoff to get a more uniform
		 * endpoint distribution. HTL is decremented before checking peers so that it's possible to respond
		 * locally.
		 */
		if (htl > 0) {
			//Degree of the local node.
			int degree = degree();
			htl = probabilisticDecrement(htl);
			//Loop until HTL runs out, in which case return a result, or the probe is relayed on to a peer.
			for (; htl > 0; htl = probabilisticDecrement(htl)) {
				PeerNode candidate;
				//Can't handle a probe request if not connected to any peers.
				if (degree == 0) {
					if (logDEBUG) Logger.minor(MHProbe.class, "Aborting received probe request; no connections.");
					return;
				}
				try {
					candidate = node.peers.myPeers[node.random.nextInt(degree)];
				} catch (IndexOutOfBoundsException e) {
					if (logDEBUG) Logger.debug(MHProbe.class, "Peer count changed during candidate search.", e);
					degree = degree();
					continue;
				}
				//acceptProbability is the MH correction.
				double acceptProbability = (double)degree / candidate.getDegree();
				if (logDEBUG) Logger.debug(MHProbe.class, "acceptProbability is "+acceptProbability);
				if (node.random.nextDouble() < acceptProbability) {
					if (logDEBUG) Logger.debug(MHProbe.class, "Accepted candidate.");
					if (candidate.isConnected()) {
						final int timeout = htl * TIMEOUT_PER_HTL;
						//Filter for response to this probe with requested result type.
						final MessageFilter filter = MessageFilter.create().setSource(candidate).setField(DMT.UID, uid).setTimeout(timeout);
						switch (type) {
							case IDENTIFIER: filter.setType(DMT.MHProbeIdentifier); break;
							case LINK_LENGTHS: filter.setType(DMT.MHProbeLinkLengths); break;
							case UPTIME: filter.setType(DMT.MHProbeUptime); break;
							case BUILD: filter.setType(DMT.MHProbeBuild); break;
							case BANDWIDTH: filter.setType(DMT.MHProbeBandwidth); break;
							case STORE_SIZE: filter.setType(DMT.MHProbeStoreSize); break;
						}
						message.set(DMT.HTL, htl);
						try {
							node.usm.addAsyncFilter(filter, callback, this);
							if (logDEBUG) Logger.debug(MHProbe.class, "Sending.");
							candidate.sendAsync(message, null, this);
							return;
						} catch (NotConnectedException e) {
							if (logDEBUG) Logger.debug(MHProbe.class, "Peer became disconnected between check and send attempt.", e);
						} catch (DisconnectedException e) {
							//TODO: This is confusing - it's async yet it would throw an exception while waiting?
							if (logDEBUG) Logger.debug(MHProbe.class, "Peer became disconnected while waiting for a response.", e);
							callback.onDisconnect(candidate);
						}
					}
				}
			}
		}

		/*
		 * HTL has been decremented to zero: return a result.
		 */
		if (htl == 0) {
			Message result;
			//Probe message/exchange identifier
			final long identifier = message.getLong(DMT.UID);

			switch (type) {
			case IDENTIFIER:
				result = DMT.createMHProbeIdentifier(identifier, node.swapIdentifier);
				break;
			case LINK_LENGTHS:
				double[] linkLengths = new double[degree()];
				int i = 0;
				for (PeerNode peer : node.peers.connectedPeers) {
					linkLengths[i++] = randomNoise(Math.min(Math.abs(peer.getLocation() - node.peers.node.getLocation()),
						1.0 - Math.abs(peer.getLocation() - node.peers.node.getLocation())));
				}
				result = DMT.createMHProbeLinkLengths(identifier, linkLengths);
				break;
			case UPTIME:
				//getUptime() is session; uptime.getUptime() is 48-hour percentage.
				result = DMT.createMHProbeUptime(uid, randomNoise(node.getUptime()), randomNoise(node.uptime.getUptime()));
				break;
			case BUILD:
				result = DMT.createMHProbeBuild(uid, node.nodeUpdater.getMainVersion());
				break;
			case BANDWIDTH:
				result = DMT.createMHProbeBandwidth(uid, randomNoise(node.config.get("node").getInt("outputBandwidthLimit")));
				break;
			case STORE_SIZE:
				result = DMT.createMHProbeStoreSize(uid, randomNoise(node.config.get("node").getLong("storeSize")));
				break;
			default:
				if (logDEBUG) Logger.debug(MHProbe.class, "Unimplemented probe result type \"" + type + "\".");
				return;
			}
			//Returning result to probe sent locally.
			if (source == null) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Returning locally sent probe.");
				callback.onMatched(result);
				return;
			}
			try {
				if (logDEBUG) Logger.debug(MHProbe.class, "Sending response to probe.");
				source.sendAsync(result, null, this);
			} catch (NotConnectedException e) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Previous step in chain is no longer connected.");
			}
		}
	}

	/**
	 * Decrements 20% of the time at HTL 1; otherwise always. This is to protect the responding node, whereas the
	 * anonymity of the node which initiated the request is not a concern.
	 * @param htl current HTL
	 * @return new HTL
	 */
	private short probabilisticDecrement(short htl) {
		assert(htl > 0);
		if (htl == 1 && node.random.nextDouble() < 0.2) return 0;
		return (short)(htl - 1);
	}

	/**
	 * @return number of peers the local node is connected to.
	 */
	private int degree() {
		return node.peers.connectedPeers.length;
	}

	/**
	 * Filter listener which determines the type of result and calls the appropriate probe listener method.
	 * This is used for returning probe results via FCP.
	 */
	private class ResultListener implements AsyncMessageFilterCallback {

		private final Listener listener;
		private final Long uid;

		/**
		 * @param listener to call appropriate methods for events such as matched messages or timeout.
		 * @param uid uid of probe to listen for - clears it from the accepted list upon an event.
		 */
		public ResultListener(Listener listener, Long uid) {
			this.listener = listener;
			this.uid = uid;
		}

		/**
		 * Parses provided message and calls appropriate MHProbe.Listener method for the type of result.
		 * @param message Probe result.
		 */
		@Override
		public void onMatched(Message message) {
			if(logDEBUG) Logger.debug(MHProbe.class, "Matched " + message.getSpec().getName());
			pendingProbes.remove(uid);
			if (message.getSpec().equals(DMT.MHProbeIdentifier)) {
				listener.onIdentifier(message.getLong(DMT.IDENTIFIER));
			} else if (message.getSpec().equals(DMT.MHProbeUptime)) {
				listener.onUptime(message.getLong(DMT.UPTIME_SESSION), message.getDouble(DMT.UPTIME_PERCENT_48H));
			} else if (message.getSpec().equals(DMT.MHProbeBandwidth)) {
				listener.onOutputBandwidth(message.getLong(DMT.OUTPUT_BANDWIDTH_UPPER_LIMIT));
			} else if (message.getSpec().equals(DMT.MHProbeStoreSize)) {
				listener.onStoreSize(message.getLong(DMT.STORE_SIZE));
			} else if (message.getSpec().equals(DMT.MHProbeLinkLengths)) {
				//TODO: Is it better to just cast an object than have Message support for double[]?
				listener.onLinkLengths(message.getDoubleArray(DMT.LINK_LENGTHS));
			} else if (message.getSpec().equals(DMT.MHProbeBuild)) {
				listener.onBuild(message.getInt(DMT.BUILD));
			} else {
				if (logDEBUG) Logger.debug(MHProbe.class, "Unknown probe result set " + message.getSpec().getName());
			}
		}

		@Override
		public void onTimeout() {
			pendingProbes.remove(uid);
			listener.onTimeout();
		}

		@Override
		public boolean shouldTimeout() {
			return false;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			pendingProbes.remove(uid);
			listener.onDisconnected();
		}

		@Override
		public void onRestarted(PeerContext context) {}
	}

	/**
	 * Filter listener which relays messages (intended to be responses to the probe) to the node (intended to be
	 * that from which the probe request was received) given during construction. Used for received probe requests.
	 */
	private class ResultRelay implements AsyncMessageFilterCallback {

		private final PeerNode source;
		private final Long uid;
		private final MHProbe mhProbe;

		/**
		 *
		 * @param source peer from which the request was received and to which send the response.
		 */
		public ResultRelay(PeerNode source, Long uid, MHProbe mhProbe) {
			if (source == null) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Cannot return probe result to null peer.");
			}
			this.source = source;
			this.uid = uid;
			this.mhProbe = mhProbe;
		}


		/**
		 * Sends an incoming probe response to the originator.
		 * @param message probe response.
		 */
		@Override
		public void onMatched(Message message) {
			pendingProbes.remove(uid);
			if (source == null) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect);
				return;
			}

			//TODO: If result is a tracer request, can add local results to it.
			if (logDEBUG) Logger.debug(MHProbe.class, "Relayed message matched; relaying back to " + source.userToString());
			try {
				source.sendAsync(message, null, mhProbe);
			} catch (NotConnectedException e) {
				if (logMINOR) Logger.minor(MHProbe.class, sourceDisconnect);
			}
		}

		@Override
		public void onTimeout() {
			pendingProbes.remove(uid);
			if(logDEBUG) Logger.debug(MHProbe.class, "Relay timed out.");
		}

		/* TODO: What does this mean? Its existence implies multiple levels of being timed-out. The filter seems
		 * to instantly time out if this returns true - it's expected to perform some logic?
		 */
		@Override
		public boolean shouldTimeout() {
			return false;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			pendingProbes.remove(uid);
			if (logDEBUG) Logger.debug(MHProbe.class, "Relay source disconnected.");
		}

		@Override
		public void onRestarted(PeerContext context) {}
	}
}
