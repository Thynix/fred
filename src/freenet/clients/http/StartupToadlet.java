package freenet.clients.http;

import freenet.clients.http.PageMaker.RenderParameters;
import freenet.clients.http.uielements.Infobox;
import freenet.clients.http.uielements.Page;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

/**
 * Toadlet for "Freenet is starting up" page.
 */
public class StartupToadlet extends Toadlet {

	private StaticToadlet staticToadlet;
	private volatile boolean isPRNGReady = false;

	public StartupToadlet(StaticToadlet staticToadlet) {
		super(null);
		this.staticToadlet = staticToadlet;
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException, RedirectException {
		// If we don't disconnect we will have pipelining issues
		ctx.forceDisconnect();
		String path = uri.getPath();
		if (path.startsWith(StaticToadlet.ROOT_URL) && staticToadlet != null) {
			staticToadlet.handleMethodGET(uri, req, ctx);
		} else {
			String desc = NodeL10n.getBase().getString("StartupToadlet.title");
			Page page = ctx.getPageMaker().getPage(desc, ctx,
				new RenderParameters().renderStatus(false).renderNavigationLinks(false)
					.renderModeSwitch(false));
			page.root.head.addMeta("refresh", "20; url=");
			HTMLNode contentNode = page.content;
			if (! isPRNGReady) {
				Infobox entropyError = new Infobox(Infobox.Type.ERROR,
					NodeL10n.getBase().getString("StartupToadlet.entropyErrorTitle"));
				contentNode.addInfobox(entropyError);
				entropyError.body.addText(
					NodeL10n.getBase().getString("StartupToadlet.entropyErrorContent"));
			}
			Infobox startingUp = new Infobox(Infobox.Type.ERROR, desc);
			contentNode.addInfobox(startingUp);
			startingUp.body.addText(NodeL10n.getBase().getString("StartupToadlet.isStartingUp"));
			WelcomeToadlet.maybeDisplayWrapperLogfile(ctx, contentNode);
			//TODO: send a Retry-After header ?
			writeHTMLReply(ctx, 503, desc, page.generate());
		}
	}

	public void setIsPRNGReady() {
		isPRNGReady = true;
	}

	@Override
	public String path() {
		return "/";
	}
}
