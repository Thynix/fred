package freenet.clients.http.updateableelements;

import freenet.client.FetchContext;
import freenet.clients.http.*;
import freenet.clients.http.constants.Category;
import freenet.clients.http.uielements.Box;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.support.Base64;
import freenet.support.HTMLNode;

import java.text.NumberFormat;

/** A pushed element that renders the progress bar when loading a page. */
public class ProgressBarElement extends BaseUpdateableElement {

	/** The tracker that the Fetcher can be acquired */
	private final FProxyFetchTracker		tracker;
	/** The URI of the download this progress bar shows */
	private final FreenetURI				key;
	/** The maxSize */
	private final long					maxSize;
	/** The FetchListener that gets notified when the download progresses */
	private final NotifierFetchListener	fetchListener;
	private final FetchContext		fctx;

	public ProgressBarElement(FProxyFetchTracker tracker, FreenetURI key, FetchContext fctx, long maxSize, ToadletContext ctx, boolean pushed) {
		// This is a <div>
		super("div", "class", "progressbar", ctx);
		this.tracker = tracker;
		this.key = key;
		this.fctx = fctx;
		this.maxSize = maxSize;
		init(pushed);
		if(!pushed) {
			fetchListener = null;
			return;
		}
		// Creates and registers the FetchListener
		fetchListener = new NotifierFetchListener(((SimpleToadletServer) ctx.getContainer()).pushDataManager, this);
		tracker.getFetchInProgress(key, maxSize, fctx).addListener(fetchListener);
	}

	@Override
	public void updateState(boolean initial) {
		children.clear();

		FProxyFetchInProgress progress = tracker.getFetchInProgress(key, maxSize, fctx);
		FProxyFetchWaiter waiter = progress == null ? null : progress.getWaiter();
		FProxyFetchResult fr = waiter == null ? null : waiter.getResult();
		if (fr == null) {
			addBox(Category.NONE, "No fetcher found");
		} else {
			if (fr.isFinished() || fr.hasData() || fr.failed != null) {
				// If finished then we just send a FINISHED text. It will reload the page
				setContent(UpdaterConstants.FINISHED);
			} else {
				int total = fr.requiredBlocks;
				int fetchedPercent = (int) (fr.fetchedBlocks / (double) total * 100);
				int failedPercent = (int) (fr.failedBlocks / (double) total * 100);
				int fatallyFailedPercent = (int) (fr.fatallyFailedBlocks / (double) total * 100);
				HTMLNode progressBar = addBox(Category.PROGRESSBAR);
				Box done = new Box(Category.PROGRESSBARDONE);
				done.addAttribute("style", "width: " + fetchedPercent + "%;");
				progressBar.addChild(done);

				if (fr.failedBlocks > 0) {
					Box failed = new Box(Category.PROGRESSBARFAILED);
					failed.addAttribute("style", "width: " + failedPercent + "%;");
					progressBar.addChild(failed);
				}
				if (fr.fatallyFailedBlocks > 0) {
					Box failed2 = new Box(Category.PROGRESSBARFAILED2);
					failed2.addAttribute("style", "width: " + fatallyFailedPercent + "%;");
					progressBar.addChild(failed2);
				}
				
				NumberFormat nf = NumberFormat.getInstance();
				nf.setMaximumFractionDigits(1);
				String prefix = '('+Integer.toString(fr.fetchedBlocks) + "/ " + Integer.toString(total)+"): ";
				if (fr.finalizedBlocks) {
					Box finalized = new Box(Category.PROGRESSBARFINAL, nf.format((int) ((fr.fetchedBlocks / (double) total) * 1000) / 10.0) + '%');
					finalized.addAttribute("title", prefix + NodeL10n.getBase().getString("QueueToadlet.progressbarAccurate"));
					progressBar.addChild(finalized);
				} else {
					String text = nf.format((int) ((fr.fetchedBlocks / (double) total) * 1000) / 10.0)+ '%';
					text = "" + fr.fetchedBlocks + " ("+text+"??)";
					Box notfinalized = new Box(Category.PROGRESSBARNOTFINAL, text);
					notfinalized.addAttribute("title", prefix + NodeL10n.getBase().getString("QueueToadlet.progressbarNotAccurate"));
					progressBar.addChild(notfinalized);
				}
			}
		}
		if (waiter != null) {
			progress.close(waiter);
		}
		if (fr != null) {
			progress.close(fr);
		}
	}

	@Override
	public String getUpdaterId(String requestId) {
		return getId(key);
	}

	public static String getId(FreenetURI uri) {
		return Base64.encodeStandard(("progressbar[URI:" + uri.toString() + "]").getBytes());
	}

	@Override
	public void dispose() {
		// Deregisters the FetchListener
		FProxyFetchInProgress progress = tracker.getFetchInProgress(key, maxSize, fctx);
		if (progress != null) {
			progress.removeListener(fetchListener);
		}
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.PROGRESSBAR_UPDATER;
	}

	@Override
	public String toString() {
		return "ProgressBarElement[key:" + key + ",maxSize:" + maxSize + ",updaterId:" + getUpdaterId(null) + "]";
	}

}
