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
 * TODO: Should this be a listener for things not sent locally?
 * Instantiated for each outgoing probe; incoming probes are dealt with by one instance thereof.
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
		pendingProbes = new HashSet<Long>();
	}

	//TODO: A terrible hack, along with accepted, to limit the number of pending probes.
	final public static HashSet<Long> pendingProbes;

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
		 * @param sessionUptime session uptime
		 * @param uptime48hour percentage uptime in the last 48 hours
		 */
		void onUptime(long sessionUptime, double uptime48hour);

		/**
		 * Build result.
		 * @param build endpoint's reported build / main version.
		 */
		void onBuild(int build);

		/**
		 * Output bandwidth limit result.
		 * @param outputBandwidth output bandwidth limit
		 */
		void onOutputBandwidth(long outputBandwidth);

		/**
		 * Store size result.
		 * @param storeSize store size
		 */
		void onStoreSize(long storeSize);

		/**
		 * Link length result.
		 * @param linkLengths endpoint's reported link lengths. These may be of limited precision or have
		 *                    random noise added.
		 */
		void onLinkLengths(double[] linkLengths);
		//TODO: HTL, key response,
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
		IDENTIFIER,
		LINK_LENGTHS,
		UPTIME,
		BUILD,
		BANDWIDTH,
		STORE_SIZE,
		HTL

		//TODO: Uncomment for numerical codes.
		/*private int code;
		private static final Map<Integer, ProbeType> lookup = new HashMap<Integer, ProbeType>();

		static {
			for(ProbeType s : EnumSet.allOf(ProbeType.class)) lookup.put(s.code(), s);
		}

		private ProbeType(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}

		public static ProbeType get(int code) {
			return lookup.get(code);
		}*/
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

	//TODO: This is a terrible hack isn't it?
	public static final int MAX_ACCEPTED = 5;
	public static volatile int accepted = 0;

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

	//TODO: Ideal is: recieving request passes on message and waits on response or timeout.
	//TODO:          also sending one waits on response or timeout...

	/**
	 * Same as its three-argument namesake, but responds to results by passing them on to source.
	 * @param message probe request, (possibly made by DMT.createMHProbeRequest) containing HTL (TODO: and
	 *                optionally key to fetch)
	 * @param source node from which the probe request was received. Used to relay back results.
	 */
	public void request(Message message, PeerNode source) {
		if(logDEBUG) Logger.debug(MHProbe.class, "Starting to relay probe with uid " + message.getLong(DMT.UID) + " from " + source.userToString());
		request(message, source, new ResultRelay(source, message.getLong(DMT.UID), this));
	}

	/**
	 * Processes an incoming probe request.
	 * If the probe has a positive HTL, routes with MH correction and probabilistically decrements HTL.
	 * TODO: How to handle multiple response types in FCP?
	 * TODO: Sending node selects this. If disallowed - what? Return error?
	 * TODO: Probabilistic HTL decrement.
	 * If the probe comes to have an HTL of zero: (an incoming HTL of zero is taken to be one.)
	 *     TODO: key and tracer requests. Separate datastore?!
	 *     returns as node settings allow at random exactly one of:
	 *         -unique identifier (specific to probe requests and unrelated to UID) (TODO)
	 *         TODO: config options to disable
	 *          and uptime information: session, 48-hour, (TODO) 7-day - uptime estimator
	 *         -output bandwidth rounded to nearest KiB
	 *         -store size rounded to nearest GiB
	 *         -htl success rates for remote requests (TODO: hourly stats record)
	 *         -link lengths (TODO)
	 *
	 *     -If a key is provided, it may also be: (TODO)
	 *         -a normal fetch
	 *         -a tracer request
	 * @param message probe request, containing HTL (TODO: and optionally key to fetch)
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
		if (!pendingProbes.contains(uid) && accepted >= MAX_ACCEPTED) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Already accepted maximum number of probes; rejecting incoming.");
			return;
		} else if (!pendingProbes.contains(uid)) {
			if (logDEBUG) Logger.debug(MHProbe.class, "Accepting probe with uid " + uid + ".");
			pendingProbes.add(uid);
			accepted++;
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
		 * endpoint distribution.
		 */
		if (htl > 0) {
			//Degree of the local node.
			int degree = degree();
			/*
			 * Loop until HTL runs out, in which case return a result, (by falling out of the loop) or until
			 * a peer suitable to hand the message off to is found, in which case the message is sent and
			 * the method returns. HTL is decremented beforehand so the request can be accepted locally,
			 * though it is decremented probabilistically.
			 */
			htl = probabilisticDecrement(htl);
			for (; htl > 0; htl = probabilisticDecrement(htl)) {
				PeerNode candidate;
				//Can't return a probe request if not connected to anyone.
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
							//TODO: Is it reasonable to re-send in this case?
							callback.onDisconnect(candidate);
						}
					}
				}
			}
		}

		/*
		 * HTL is zero: return a result.
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
				//TODO: 7-day percentage
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
				AsyncMessageCallback cb = new AsyncMessageCallback() {
					@Override
					public void sent() {
						if (logDEBUG) Logger.debug(MHProbe.class, "Response was sent.");
					}

					@Override
					public void acknowledged() {
						if (logDEBUG) Logger.debug(MHProbe.class, "Response was acknowledged.");
					}

					@Override
					public void disconnected() {
						if (logDEBUG) Logger.debug(MHProbe.class, "Response target disconnected.");
					}

					@Override
					public void fatalError() {
						if (logDEBUG) Logger.debug(MHProbe.class, "Fatal error sending response.");
					}
				};
				source.sendAsync(result, cb, this);
			} catch (NotConnectedException e) {
				if (logDEBUG) Logger.debug(MHProbe.class, "Previous step in chain is no longer connected.");
			}
		}
	}

	private short probabilisticDecrement(short htl) {
		//TODO: A mathematical function - say one that's 0.2 at 1 and goes up to 0.9 at MAX_HTL could be more
		//flexible and give smoother behavior, but would also be more complicated?
		if (htl == 1 && node.random.nextDouble() < 0.2) return 0;
		else if (node.random.nextDouble() < 0.9) return (short)(htl - 1);
		return htl;
	}

	/**
	 * @return number of peers the local node is connected to.
	 */
	private int degree() {
		return node.peers.connectedPeers.length;
	}

	/**
	 * Filter listener which determines the type of result and calls the appropriate probe listener method.
	 */
	private class ResultListener implements AsyncMessageFilterCallback {

		private final Listener listener;
		private final Long uid;

		public ResultListener(Listener listener, Long uid) {
			this.listener = listener;
			this.uid = uid;
		}

		/**
		 * Parses provided message and calls appropriate MHProbe.Listener method for the type of result
		 * present,
		 * @param message result message
		 */
		@Override
		public void onMatched(Message message) {
			assert(accepted > 0);
			if(logDEBUG) Logger.debug(MHProbe.class, "Matched " + message.getSpec().getName());
			accepted--;
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
				//TODO: Is it better to just cast an object?
				listener.onLinkLengths(message.getDoubleArray(DMT.LINK_LENGTHS));
			} else if (message.getSpec().equals(DMT.MHProbeBuild)) {
				listener.onBuild(message.getInt(DMT.BUILD));
			} else {
				//TODO: The rest of the result types.
				if (logDEBUG) Logger.debug(MHProbe.class, "Unknown probe result set " + message.getSpec().getName());
			}
		}

		@Override
		public void onTimeout() {
			assert(accepted > 0);
			accepted--;
			pendingProbes.remove(uid);
			if (logDEBUG) Logger.debug(MHProbe.class, "Got timeout");
			listener.onTimeout();
		}

		@Override
		public boolean shouldTimeout() {
			return false;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			assert(accepted > 0);
			accepted--;
			pendingProbes.remove(uid);
			listener.onDisconnected();
		}

		@Override
		public void onRestarted(PeerContext context) {}
	}

	/**
	 * Filter listener which relays messages onto the source given during construction. USed when receiving probes
	 * not sent locally.
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
		 * @param message probe response
		 */
		@Override
		public void onMatched(Message message) {
			assert(accepted > 0);
			accepted--;
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
			assert(accepted > 0);
			accepted--;
			pendingProbes.remove(uid);
			if(logDEBUG) Logger.debug(MHProbe.class, "Relay timed out.");
		}

		//TODO: What does this mean? Its existence implies multiple levels of being timed-out.
		@Override
		public boolean shouldTimeout() {
			return false;
		}

		@Override
		public void onDisconnect(PeerContext context) {
			assert(accepted > 0);
			accepted--;
			pendingProbes.remove(uid);
			if(logDEBUG) Logger.debug(MHProbe.class, "Relay source disconnected.");
		}

		@Override
		public void onRestarted(PeerContext context) {}
	}
}
