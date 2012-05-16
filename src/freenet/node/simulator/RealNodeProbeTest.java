/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.simulator;

import freenet.crypt.DummyRandomSource;
import freenet.crypt.RandomSource;
import freenet.node.LocationManager;
import freenet.node.MHProbe;
import freenet.node.Node;
import freenet.node.NodeStarter;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.PooledExecutor;
import freenet.support.io.FileUtil;
import freenet.support.math.BootstrappingDecayingRunningAverage;
import freenet.support.math.RunningAverage;
import freenet.support.math.SimpleRunningAverage;

import java.io.File;

/**
 * Create a mesh of nodes and let them sort out their locations.
 * 
 * Then run some node-to-node searches.
 */
public class RealNodeProbeTest extends RealNodeTest {

	static final int NUMBER_OF_NODES = 100;
	static final int DEGREE = 5;
	static final short MAX_HTL = (short) 5;
	static final boolean START_WITH_IDEAL_LOCATIONS = true;
	static final boolean FORCE_NEIGHBOUR_CONNECTIONS = true;
	static final int MAX_PINGS = 2000;
	static final boolean ENABLE_SWAPPING = false;
	static final boolean ENABLE_SWAP_QUEUEING = false;
	static final boolean ENABLE_FOAF = true;
	
	public static int DARKNET_PORT_BASE = RealNodeRequestInsertTest.DARKNET_PORT_END;
	public static final int DARKNET_PORT_END = DARKNET_PORT_BASE + NUMBER_OF_NODES;

	public static void main(String[] args) throws Exception {
		System.out.println("Probe test using real nodes:");
		System.out.println();
		String dir = "realNodeProbeTest";
		File wd = new File(dir);
		if(!FileUtil.removeAll(wd)) {
			System.err.println("Mass delete failed, test may not be accurate.");
			System.exit(EXIT_CANNOT_DELETE_OLD_DATA);
		}
		wd.mkdir();
		NodeStarter.globalTestInit(dir, false, LogLevel.ERROR, "", true);
		// Make the network reproducible so we can easily compare different routing options by specifying a seed.
		DummyRandomSource random = new DummyRandomSource(3142);
		Node[] nodes = new Node[NUMBER_OF_NODES];
		Logger.normal(RealNodeProbeTest.class, "Creating nodes...");
		Executor executor = new PooledExecutor();
		for(int i = 0; i < NUMBER_OF_NODES; i++) {
			System.err.println("Creating node " + i);
			nodes[i] = NodeStarter.createTestNode(DARKNET_PORT_BASE + i, 0, dir, true, MAX_HTL, 0 /* no dropped packets */, random, executor, 500 * NUMBER_OF_NODES, 65536, true, ENABLE_SWAPPING, false, false, false, ENABLE_SWAP_QUEUEING, true, 0, ENABLE_FOAF, false, true, false, null);
			Logger.normal(RealNodeProbeTest.class, "Created node " + i);
		}
		Logger.normal(RealNodeProbeTest.class, "Created " + NUMBER_OF_NODES + " nodes");
		// Now link them up
		makeKleinbergNetwork(nodes, START_WITH_IDEAL_LOCATIONS, DEGREE, FORCE_NEIGHBOUR_CONNECTIONS, random);

		Logger.normal(RealNodeProbeTest.class, "Added random links");

		for(int i = 0; i < NUMBER_OF_NODES; i++) {
			System.err.println("Starting node " + i);
			nodes[i].start(false);
		}

		waitForAllConnected(nodes);

		//TODO: How to start probes? Not clear if there's FCP exposed or what.
		MHProbe.Listener print = new MHProbe.Listener() {
			@Override
			public void onTimeout() {
				System.out.println("Probe timed out.");
			}

			@Override
			public void onDisconnected() {
				System.out.println("Probe disconnected.");
			}

			@Override
			public void onIdentifier(long identifier) {
				System.out.println("Probe got identifier " + identifier);
			}

			@Override
			public void onLinkLengths(double[] linkLengths) {
				System.out.println("Probe got link lengths: { ");
				for (Double length : linkLengths) System.out.print(length + ", ");
				System.out.println("}.");
			}
		};

		nodes[random.nextInt(NUMBER_OF_NODES)].dispatcher.mhProbe.start(MAX_HTL, random.nextLong(), MHProbe.ProbeType.IDENTIFIER, print);
		System.exit(0);
	}
}
