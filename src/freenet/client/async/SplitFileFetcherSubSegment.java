package freenet.client.async;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.keys.CHKBlock;
import freenet.keys.CHKVerifyException;
import freenet.keys.ClientCHK;
import freenet.keys.ClientCHKBlock;
import freenet.keys.ClientKey;
import freenet.keys.ClientKeyBlock;
import freenet.keys.Key;
import freenet.keys.KeyBlock;
import freenet.keys.KeyDecodeException;
import freenet.keys.TooBigException;
import freenet.node.BulkCallFailureItem;
import freenet.node.KeysFetchingLocally;
import freenet.node.LowLevelGetException;
import freenet.node.RequestClient;
import freenet.node.RequestScheduler;
import freenet.node.SendableGet;
import freenet.node.SupportsBulkCallFailure;
import freenet.support.Logger;
import freenet.support.api.Bucket;

/**
 * A sub-segment of a segment of a splitfile being fetched.
 * Collects together all requests within that segment at a given retry level.
 * Registered on the ClientRequestScheduler instead of SimpleSingleFileFetcher's.
 * When CRS asks it to run a request, returns one, and only unregisters if no more requests in this category.
 * 
 * LOCKING: Synchronize on the parent segment. Nothing else makes sense w.r.t. nested locking.
 * Note that SendableRequest will occasionally lock on (this). That lock is always taken last.
 */
public class SplitFileFetcherSubSegment extends SendableGet implements SupportsBulkCallFailure {

	final int retryCount;
	final SplitFileFetcherSegment segment;
	final ClientRequester parent;
	/**
	 * The block numbers (as Integer's) of the blocks we are currently trying to fetch.
	 * Does not include blocks on the cooldown queue, this is simply used to make 
	 * chooseKey() and allKeys() work / work fast. The retries tables in the Segment are
	 * canonical.
	 */
	final Vector<Integer> blockNums;
	final FetchContext ctx;
	private static boolean logMINOR;
	private boolean cancelled;
	
	SplitFileFetcherSubSegment(SplitFileFetcherSegment segment, ClientRequester parent, int retryCount) {
		super(parent);
		this.segment = segment;
		this.retryCount = retryCount;
		this.parent = segment.parent;
		if(parent == null) throw new NullPointerException();
		ctx = segment.blockFetchContext;
		blockNums = new Vector<Integer>();
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
	}
	
	@Override
	public boolean dontCache() {
		return !ctx.cacheLocalRequests;
	}

	@Override
	public FetchContext getContext() {
		return ctx;
	}

	@Override
	public Object chooseKey(KeysFetchingLocally keys, ObjectContainer container, ClientContext context) {
		if(cancelled) return null;
		return getRandomBlockNum(keys, context, container);
	}
	
	@Override
	public ClientKey getKey(Object token, ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
		}
		synchronized(segment) {
			if(cancelled) {
				if(logMINOR)
					Logger.minor(this, "Segment is finishing when getting key "+token+" on "+this);
				return null;
			}
			ClientKey key = segment.getBlockKey(((Integer)token).intValue(), container);
			if(key == null) {
				if(segment.isFinished(container)) {
					Logger.error(this, "Segment finished but didn't tell us! "+this);
				} else if(segment.isFinishing(container)) {
					Logger.error(this, "Segment finishing but didn't tell us! "+this);
				} else {
					Logger.error(this, "Segment not finishing yet still returns null for getKey()!: "+token+" for "+this, new Exception("debug"));
				}
			}
			return key;
		}
	}
	
	/**
	 * Fetch the array from the segment because we need to include *ALL* keys, especially
	 * those on cooldown queues. This is important when unregistering.
	 */
	@Override
	public Object[] allKeys(ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
		}
		return segment.getKeyNumbersAtRetryLevel(retryCount);
	}
	
	/**
	 * Just those keys which are eligible to be started now.
	 */
	@Override
	public Object[] sendableKeys(ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(blockNums, 1);
		}
		cleanBlockNums(container);
		return blockNums.toArray();
	}
	
	private void cleanBlockNums(ObjectContainer container) {
		synchronized(segment) {
			int initSize = blockNums.size();
			Integer prev = null;
			for(int i=0;i<blockNums.size();i++) {
				Integer x = (Integer) blockNums.get(i);
				if(x == prev || x.equals(prev)) {
					blockNums.remove(i);
					i--;
					if(persistent) container.delete(x);
				} else prev = x;
			}
			if(blockNums.size() < initSize) {
				Logger.error(this, "Cleaned block number list duplicates: was "+initSize+" now "+blockNums.size());
			}
		}
	}

	private Object getRandomBlockNum(KeysFetchingLocally keys, ClientContext context, ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(blockNums, 1);
			container.activate(segment, 1);
		}
		logMINOR = Logger.shouldLog(Logger.MINOR, this);
		synchronized(segment) {
			if(blockNums.isEmpty()) {
				if(logMINOR)
					Logger.minor(this, "No blocks to remove");
				return null;
			}
			for(int i=0;i<10;i++) {
				Integer ret;
				int x;
				if(blockNums.size() == 0) return null;
				x = context.random.nextInt(blockNums.size());
				ret = blockNums.get(x);
				int num = ret;
				Key key = segment.getBlockNodeKey(ret, container);
				if(key == null) {
					if(segment.isFinishing(container) || segment.isFinished(container)) return null;
					if(segment.haveBlock(num, container))
						Logger.error(this, "Already have block "+ret+" but was in blockNums on "+this);
					else
						Logger.error(this, "Key is null for block "+ret+" for "+this);
					continue;
				}
				if(keys.hasKey(key)) {
					continue;
				}
				if(logMINOR)
					Logger.minor(this, "Removing block "+x+" of "+(blockNums.size()+1)+ " : "+ret+ " on "+this);
				return ret;
			}
			return null;
		}
	}

	@Override
	public boolean hasValidKeys(KeysFetchingLocally keys, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(blockNums, 1);
			container.activate(segment, 1);
		}
		boolean hasSet = false;
		boolean retval = false;
		synchronized(segment) {
			for(int i=0;i<10;i++) {
				Integer ret;
				int x;
				if(blockNums.isEmpty()) {
					break;
				}
				x = context.random.nextInt(blockNums.size());
				ret = blockNums.get(x);
				int block = ret;
				Key key = segment.getBlockNodeKey(block, container);
				if(key == null) {
					if(segment.isFinishing(container) || segment.isFinished(container)) return false;
					if(segment.haveBlock(block, container))
						Logger.error(this, "Already have block "+ret+" but was in blockNums on "+this+" in hasValidKeys");
					else
						Logger.error(this, "Key is null for block "+ret+" for "+this+" in hasValidKeys");
					blockNums.remove(x);
					if(persistent) {
						container.delete(ret);
						if(!hasSet) {
							hasSet = true;
							container.store(blockNums);
						}
					}
					continue;
				}
				if(keys.hasKey(key)) {
					continue;
				}
				retval = true;
				break;
			}
		}
		if(persistent) {
			container.deactivate(blockNums, 5);
			container.deactivate(segment, 1);
		}
		return retval;
	}
	
	@Override
	public boolean ignoreStore() {
		return ctx.ignoreStore;
	}

	// SendableGet has a hashCode() and inherits equals(), which is consistent with the hashCode().
	
	public void onFailure(BulkCallFailureItem[] items, ObjectContainer container, ClientContext context) {
		FetchException[] fetchExceptions = new FetchException[items.length];
		int countFatal = 0;
		if(persistent) {
			container.activate(blockNums, 2);
		}
		for(int i=0;i<items.length;i++) {
			fetchExceptions[i] = translateException(items[i].e);
			if(fetchExceptions[i].isFatal()) countFatal++;
			removeBlockNum(((Integer)items[i].token).intValue(), container, true);
		}
		if(persistent) {
			container.store(blockNums);
			container.deactivate(blockNums, 2);
			container.activate(segment, 1);
			container.activate(parent, 1);
			container.activate(segment.errors, 1);
		}
		if(parent.isCancelled()) {
			if(Logger.shouldLog(Logger.MINOR, this)) 
				Logger.minor(this, "Failing: cancelled");
			// Fail the segment.
			segment.fail(new FetchException(FetchException.CANCELLED), container, context, false);
			// FIXME do we need to free the keyNum's??? Or will that happen later anyway?
			return;
		}
		for(int i=0;i<fetchExceptions.length;i++)
			segment.errors.inc(fetchExceptions[i].getMode());
		int nonFatalExceptions = items.length - countFatal;
		int[] blockNumbers = new int[nonFatalExceptions];
		if(countFatal > 0) {
			FetchException[] newFetchExceptions = new FetchException[items.length - countFatal];
			// Call the fatal callbacks directly.
			int x = 0;
			for(int i=0;i<items.length;i++) {
				int blockNum = (Integer)items[i].token;
				if(fetchExceptions[i].isFatal()) {
					segment.onFatalFailure(fetchExceptions[i], blockNum, this, container, context);
				} else {
					blockNumbers[x] = blockNum;
					newFetchExceptions[x] = fetchExceptions[i];
					x++;
				}
			}
			fetchExceptions = newFetchExceptions;
		} else {
			for(int i=0;i<blockNumbers.length;i++)
				blockNumbers[i] = (Integer)items[i].token;
		}
		segment.onNonFatalFailure(fetchExceptions, blockNumbers, this, container, context);

		if(persistent) {
			container.deactivate(segment, 1);
			container.deactivate(parent, 1);
			container.deactivate(segment.errors, 1);
		}
	}
	
	// FIXME refactor this out to a common method; see SimpleSingleFileFetcher
	private FetchException translateException(LowLevelGetException e) {
		switch(e.code) {
		case LowLevelGetException.DATA_NOT_FOUND:
		case LowLevelGetException.DATA_NOT_FOUND_IN_STORE:
			return new FetchException(FetchException.DATA_NOT_FOUND);
		case LowLevelGetException.RECENTLY_FAILED:
			return new FetchException(FetchException.RECENTLY_FAILED);
		case LowLevelGetException.DECODE_FAILED:
			return new FetchException(FetchException.BLOCK_DECODE_ERROR);
		case LowLevelGetException.INTERNAL_ERROR:
			return new FetchException(FetchException.INTERNAL_ERROR);
		case LowLevelGetException.REJECTED_OVERLOAD:
			return new FetchException(FetchException.REJECTED_OVERLOAD);
		case LowLevelGetException.ROUTE_NOT_FOUND:
			return new FetchException(FetchException.ROUTE_NOT_FOUND);
		case LowLevelGetException.TRANSFER_FAILED:
			return new FetchException(FetchException.TRANSFER_FAILED);
		case LowLevelGetException.VERIFY_FAILED:
			return new FetchException(FetchException.BLOCK_DECODE_ERROR);
		case LowLevelGetException.CANCELLED:
			return new FetchException(FetchException.CANCELLED);
		default:
			Logger.error(this, "Unknown LowLevelGetException code: "+e.code);
			return new FetchException(FetchException.INTERNAL_ERROR, "Unknown error code: "+e.code);
		}
	}

	// Translate it, then call the real onFailure
	public void onFailure(LowLevelGetException e, Object token, ObjectContainer container, ClientContext context) {
		if(logMINOR)
			Logger.minor(this, "onFailure("+e+" , "+token+" on "+this);
		onFailure(translateException(e), token, container, context);
	}

	// Real onFailure
	protected void onFailure(FetchException e, Object token, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(segment, 1);
			container.activate(parent, 1);
			container.activate(segment.errors, 1);
		}
		boolean forceFatal = false;
		if(parent.isCancelled()) {
			if(Logger.shouldLog(Logger.MINOR, this)) 
				Logger.minor(this, "Failing: cancelled");
			e = new FetchException(FetchException.CANCELLED);
			forceFatal = true;
		}
		segment.errors.inc(e.getMode());
		if(e.isFatal() && token == null) {
			segment.fail(e, container, context, false);
		} else if(e.isFatal() || forceFatal) {
			segment.onFatalFailure(e, (Integer)token, this, container, context);
		} else {
			segment.onNonFatalFailure(e, (Integer)token, this, container, context);
		}
		removeBlockNum((Integer)token, container, false);
		if(persistent) {
			container.deactivate(segment, 1);
			container.deactivate(parent, 1);
			container.deactivate(segment.errors, 1);
		}
	}
	
	@Override
	public void onSuccess(ClientKeyBlock block, boolean fromStore, Object token, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		Bucket data = extract(block, token, container, context);
		if(fromStore) {
			// Normally when this method is called the block number has already
			// been removed. However if fromStore=true, it won't have been, so
			// we have to do it. (Check the call trace for why)
			boolean removed = false;
			synchronized(segment) {
				for(int i=0;i<blockNums.size();i++) {
					Integer x = blockNums.get(i);
					// Compare by value as sometimes we will do new Integer(num) in requeueing after cooldown code.
					if(x.equals(token)) {
						blockNums.remove(i);
						if(persistent) container.delete(x);
						if(logMINOR) Logger.minor(this, "Removed block "+i+" : "+x);
						i--;
						removed = true;
					}
				}
			}
			if(persistent && removed)
				container.store(blockNums);
		}
		if(!block.isMetadata()) {
			onSuccess(data, fromStore, (Integer)token, (Integer)token, block, container, context);
		} else {
			onFailure(new FetchException(FetchException.INVALID_METADATA, "Metadata where expected data"), token, container, context);
			data.free();
		}
		if(persistent) {
			container.deactivate(segment, 1);
			container.deactivate(blockNums, 1);
		}
	}
	
	protected void onSuccess(Bucket data, boolean fromStore, Integer token, int blockNo, ClientKeyBlock block, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
			container.activate(parent, 1);
		}
		if(parent.isCancelled()) {
			data.free();
			onFailure(new FetchException(FetchException.CANCELLED), token, container, context);
			return;
		}
		segment.onSuccess(data, blockNo, block, container, context, this);
	}

	/** Convert a ClientKeyBlock to a Bucket. If an error occurs, report it via onFailure
	 * and return null.
	 */
	protected Bucket extract(ClientKeyBlock block, Object token, ObjectContainer container, ClientContext context) {
		Bucket data;
		try {
			data = block.decode(context.getBucketFactory(persistent), (int)(Math.min(ctx.maxOutputLength, Integer.MAX_VALUE)), false);
		} catch (KeyDecodeException e1) {
			if(Logger.shouldLog(Logger.MINOR, this))
				Logger.minor(this, "Decode failure: "+e1, e1);
			onFailure(new FetchException(FetchException.BLOCK_DECODE_ERROR, e1.getMessage()), token, container, context);
			return null;
		} catch (TooBigException e) {
			onFailure(new FetchException(FetchException.TOO_BIG, e.getMessage()), token, container, context);
			return null;
		} catch (IOException e) {
			Logger.error(this, "Could not capture data - disk full?: "+e, e);
			onFailure(new FetchException(FetchException.BUCKET_ERROR, e), token, container, context);
			return null;
		}
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, data == null ? "Could not decode: null" : ("Decoded "+data.size()+" bytes"));
		return data;
	}

	@Override
	public RequestClient getClient() {
		return parent.getClient();
	}

	@Override
	public ClientRequester getClientRequest() {
		return parent;
	}

	@Override
	public short getPriorityClass(ObjectContainer container) {
		if(persistent) container.activate(parent, 1);
		return parent.priorityClass;
	}

	@Override
	public int getRetryCount() {
		return retryCount;
	}

	@Override
	public boolean isCancelled(ObjectContainer container) {
		if(persistent) {
			container.activate(parent, 1);
		}
		synchronized(segment) {
			return parent.cancelled;
		}
	}
	
	public boolean isEmpty(ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(blockNums, 1);
		}
		synchronized(segment) {
			return cancelled || blockNums.isEmpty();
		}
	}

	@Override
	public boolean isSSK() {
		// Not allowed in splitfiles
		return false;
	}
	
	public void addAll(int blocks, boolean dontSchedule, ObjectContainer container, ClientContext context, boolean dontComplainOnDupes) {
		int[] list = new int[blocks];
		for(int i=0;i<blocks;i++) list[i] = i;
		addAll(list, dontSchedule, container, context, dontComplainOnDupes);
	}

	public void addAll(int[] blocks, boolean dontSchedule, ObjectContainer container, ClientContext context, boolean dontComplainOnDupes) {
		if(persistent) {
//			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		boolean logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if(logMINOR) Logger.minor(this, "Adding "+blocks+" blocks to "+this+" dontSchedule="+dontSchedule);
		boolean schedule = true;
		synchronized(segment) {
			if(cancelled)
				throw new IllegalStateException("Adding blocks to already cancelled "+this);
			for(int x=0;x<blocks.length;x++) {
				int i = blocks[x];
				Integer ii = new Integer(i);
				if(blockNums.contains(ii)) {
					if(!dontComplainOnDupes)
						Logger.error(this, "Block numbers already contain block "+i);
					else if(logMINOR)
						Logger.minor(this, "Block numbers already contain block "+i);
				} else {
					blockNums.add(ii);
				}
				if(dontSchedule) schedule = false;
				/**
				 * Race condition:
				 * 
				 * Starter thread sees there is only one block on us, so removes us.
				 * Another thread adds a block. We don't schedule as we now have two blocks.
				 * Starter thread removes us.
				 * Other blocks may be added later, but we are never rescheduled.
				 * 
				 * Fixing this by only removing the SendableRequest after we've removed the 
				 * block is nontrivial with the current code.
				 * So what we do here is simply check whether we are registered, instead of 
				 * checking whether blockNums.size() > 1 as we used to.
				 */
				if(schedule && getParentGrabArray() != null) {
					if(logMINOR) Logger.minor(this, "Already registered, not scheduling: "+blockNums.size()+" : "+blockNums);
					schedule = false;
				}

			}
		}
		if(persistent)
			container.store(blockNums);
		if(schedule) {
			// Only need to register once for all the blocks.
			try {
				context.getChkFetchScheduler().register(null, new SendableGet[] { this }, persistent, true, container, null, true);
			} catch (KeyListenerConstructionException e) {
				Logger.error(this, "Impossible: "+e+" on "+this, e);
			}
		}

	}
	
	/**
	 * @return True if the caller should schedule.
	 */
	public boolean add(int blockNo, boolean dontSchedule, ObjectContainer container, ClientContext context, boolean dontComplainOnDupes) {
		if(persistent) {
//			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		boolean logMINOR = Logger.shouldLog(Logger.MINOR, this);
		if(logMINOR) Logger.minor(this, "Adding block "+blockNo+" to "+this+" dontSchedule="+dontSchedule);
		if(blockNo < 0) throw new IllegalArgumentException();
		Integer i = new Integer(blockNo);
		
		boolean schedule = true;
		synchronized(segment) {
			if(cancelled)
				throw new IllegalStateException("Adding block "+blockNo+" to already cancelled "+this);
			if(blockNums.contains(i)) {
				if(!dontComplainOnDupes)
					Logger.error(this, "Block numbers already contain block "+blockNo);
				else if(logMINOR)
					Logger.minor(this, "Block numbers already contain block "+blockNo);
			} else {
				blockNums.add(i);
			}
			/**
			 * Race condition:
			 * 
			 * Starter thread sees there is only one block on us, so removes us.
			 * Another thread adds a block. We don't schedule as we now have two blocks.
			 * Starter thread removes us.
			 * Other blocks may be added later, but we are never rescheduled.
			 * 
			 * Fixing this by only removing the SendableRequest after we've removed the 
			 * block is nontrivial with the current code.
			 * So what we do here is simply check whether we are registered, instead of 
			 * checking whether blockNums.size() > 1 as we used to.
			 */
			if(schedule && getParentGrabArray() != null) {
				if(logMINOR) Logger.minor(this, "Already registered, not scheduling: "+blockNums.size()+" : "+blockNums);
				schedule = false;
			}
		}
		if(persistent)
			container.store(blockNums);
		if(schedule) {
			if(dontSchedule) return true;
			try {
				context.getChkFetchScheduler().register(null, new SendableGet[] { this }, persistent, true, container, null, true);
			} catch (KeyListenerConstructionException e) {
				Logger.error(this, "Impossible: "+e+" on "+this, e);
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return super.toString()+":"+retryCount+"/"+segment+'('+(blockNums == null ? "null" : String.valueOf(blockNums.size()))+"),tempid="+objectHash(); 
	}

	public void possiblyRemoveFromParent(ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		if(logMINOR)
			Logger.minor(this, "Possibly removing from parent: "+this);
		synchronized(segment) {
			if(!blockNums.isEmpty()) {
				if(persistent) container.deactivate(blockNums, 1);
				return;
			}
			if(logMINOR)
				Logger.minor(this, "Definitely removing from parent: "+this);
			if(!segment.maybeRemoveSeg(this, container)) {
				if(persistent) container.deactivate(blockNums, 1);
				return;
			}
		}
		kill(container, context, true);
	}

	public void onGotKey(Key key, KeyBlock block, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		if(logMINOR) Logger.minor(this, "onGotKey("+key+")");
		// Find and remove block if it is on this subsegment. However it may have been
		// removed already.
		int blockNo;
		synchronized(segment) {
			for(int i=0;i<blockNums.size();i++) {
				Integer token = blockNums.get(i);
				int num = (Integer)token;
				Key k = segment.getBlockNodeKey(num, container);
				if(k != null && k.equals(key)) {
					blockNums.remove(i);
					if(persistent) container.delete(token);
					break;
				}
			}
			blockNo = segment.getBlockNumber(key, container);
		}
		if(blockNo == -1) {
			Logger.minor(this, "No block found for key "+key+" on "+this);
			return;
		}
		Integer token = new Integer(blockNo);
		ClientCHK ckey = (ClientCHK) segment.getBlockKey(blockNo, container);
		ClientCHKBlock cb;
		try {
			cb = new ClientCHKBlock((CHKBlock)block, ckey);
		} catch (CHKVerifyException e) {
			onFailure(new FetchException(FetchException.BLOCK_DECODE_ERROR, e), token, container, context);
			return;
		}
		Bucket data = extract(cb, token,  container, context);
		if(data == null) return;
		
		if(!cb.isMetadata()) {
			onSuccess(data, false, (Integer)token, ((Integer)token).intValue(), cb, container, context);
		} else {
			onFailure(new FetchException(FetchException.INVALID_METADATA, "Metadata where expected data"), token, container, context);
		}
		
	}

	/**
	 * Terminate a subsegment. Called by the segment, which will have already removed the
	 * subsegment from the list. Will delete the object from the database if persistent.
	 */
	public void kill(ObjectContainer container, ClientContext context, boolean dontDeactivateSeg) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		if(logMINOR)
			Logger.minor(this, "Killing "+this);
		// Do unregister() first so can get and unregister each key and avoid a memory leak
		unregister(container, context);
		Integer[] oldNums;
		synchronized(segment) {
			oldNums = blockNums.toArray(new Integer[blockNums.size()]);
			blockNums.clear();
			cancelled = true;
		}
		if(persistent) {
			for(Integer i : oldNums) container.delete(i);
			container.delete(blockNums);
			container.delete(this);
			if(!dontDeactivateSeg)
				container.deactivate(segment, 1);
			// We do not need to call SendableGet as it has no internal data structures that need deleting.
		}
	}

	@Override
	public long getCooldownWakeup(Object token, ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
		}
		long ret = segment.getCooldownWakeup(((Integer)token).intValue());
		return ret;
	}

	@Override
	public void requeueAfterCooldown(Key key, long time, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(segment, 1);
		}
		if(Logger.shouldLog(Logger.MINOR, this))
			Logger.minor(this, "Requeueing after cooldown "+key+" for "+this);
		if(!segment.requeueAfterCooldown(key, time, container, context, this)) {
			Logger.error(this, "Key was not wanted after cooldown: "+key+" for "+this+" in requeueAfterCooldown");
		}
		if(persistent) {
			container.deactivate(segment, 1);
			if(container.ext().isActive(segment))
				Logger.error(this, "SEGMENT STILL ACTIVE: "+segment);
			else
				if(logMINOR) Logger.minor(this, "Deactivated segment "+segment);
		}
	}

	@Override
	public long getCooldownWakeupByKey(Key key, ObjectContainer container) {
		/* Only deactivate if was deactivated in the first place. 
		 * See the removePendingKey() stack trace: Segment is the listener (getter) ! */
		boolean activated = false;
		if(persistent) {
			activated = container.ext().isActive(segment);
			if(!activated)
				container.activate(segment, 1);
		}
		long ret = segment.getCooldownWakeupByKey(key, container);
		if(persistent) {
			if(!activated)
				container.deactivate(segment, 1);
		}
		return ret;
	}

	@Override
	public void resetCooldownTimes(ObjectContainer container) {
		if(persistent) {
			container.activate(this, 1);
			container.activate(segment, 1);
		}
		synchronized(segment) {
			segment.resetCooldownTimes((Integer[])blockNums.toArray(new Integer[blockNums.size()]));
		}
	}

	public void reschedule(ObjectContainer container, ClientContext context) {
		try {
			getScheduler(context).register(null, new SendableGet[] { this }, persistent, true, container, segment.blockFetchContext.blocks, true);
		} catch (KeyListenerConstructionException e) {
			Logger.error(this, "Impossible: "+e+" on "+this, e);
		}
	}

	public boolean removeBlockNum(int blockNum, ObjectContainer container, boolean callerActivatesAndSets) {
		if(logMINOR) Logger.minor(this, "Removing block "+blockNum+" from "+this);
		if(persistent && !callerActivatesAndSets)
			container.activate(blockNums, 2);
		boolean found = false;
		synchronized(segment) {
			for(int i=0;i<blockNums.size();i++) {
				Integer token = blockNums.get(i);
				int num = token;
				if(num == blockNum) {
					blockNums.remove(i);
					if(persistent) container.delete(token);
					if(logMINOR) Logger.minor(this, "Removed block "+blockNum+" from "+this);
					found = true;
					break;
				}
			}
		}
		if(persistent && !callerActivatesAndSets) {
			container.store(blockNums);
			container.deactivate(blockNums, 2);
		}
		return found;
	}

	public void removeBlockNums(int[] blockNos, ObjectContainer container) {
		if(persistent)
			container.activate(blockNums, 2);
		boolean store = false;
		for(int i=0;i<blockNos.length;i++)
			store |= removeBlockNum(blockNos[i], container, true);
		if(persistent) {
			if(store) container.store(blockNums);
			container.deactivate(blockNums, 2);
		}
	}

	@Override
	public List<PersistentChosenBlock> makeBlocks(PersistentChosenRequest request, RequestScheduler sched, ObjectContainer container, ClientContext context) {
		if(persistent) {
			container.activate(segment, 1);
			container.activate(blockNums, 1);
		}
		Integer[] blockNumbers;
		synchronized(this) {
			blockNumbers = blockNums.toArray(new Integer[blockNums.size()]);
		}
		ArrayList<PersistentChosenBlock> blocks = new ArrayList<PersistentChosenBlock>();
		Arrays.sort(blockNumbers);
		int prevBlockNumber = -1;
		for(int i=0;i<blockNumbers.length;i++) {
			int blockNumber = blockNumbers[i];
			if(blockNumber == prevBlockNumber) {
				Logger.error(this, "Duplicate block number in makeBlocks() in "+this+": two copies of "+blockNumber);
				continue;
			}
			prevBlockNumber = blockNumber;
			ClientKey key = segment.getBlockKey(blockNumber, container);
			if(key == null) {
				if(logMINOR)
					Logger.minor(this, "Block "+blockNumber+" is null, maybe race condition");
				continue;
			}
			key = key.cloneKey();
			Key k = key.getNodeKey();
			PersistentChosenBlock block = new PersistentChosenBlock(false, request, blockNumber, k, key, sched);
			if(logMINOR) Logger.minor(this, "Created block "+block+" for block number "+blockNumber+" on "+this);
			blocks.add(block);
		}
		blocks.trimToSize();
		if(persistent) {
			container.deactivate(segment, 1);
			container.deactivate(blockNums, 1);
		}
		return blocks;
	}

	@Override
	public Key[] listKeys(ObjectContainer container) {
		boolean activated = false;
		if(persistent) {
			activated = container.ext().isActive(segment);
			if(!activated)
				container.activate(segment, 1);
		}
		Key[] keys = segment.listKeys(container);
		if(persistent && !activated)
			container.deactivate(segment, 1);
		return keys;
	}

	public int objectHash() {
		return super.hashCode();
	}
}
