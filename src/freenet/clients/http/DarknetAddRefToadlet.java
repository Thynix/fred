package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.updater.NodeUpdateManager;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.SimpleFieldSet;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileBucket;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;

public class DarknetAddRefToadlet extends Toadlet {

	private final Node node;
	private final NodeClientCore core;
	
	protected DarknetAddRefToadlet(Node n, NodeClientCore core, HighLevelSimpleClient client) {
		super(client);
		this.node = n;
		this.core = core;
	}

	public void handleMethodGET(URI uri, final HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException, RedirectException {
		if (! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"),
				NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		String path = uri.getPath();
		if (path.endsWith("myref.fref")) {
			SimpleFieldSet fs = getNoderef();
			StringWriter sw = new StringWriter();
			fs.writeTo(sw);
			MultiValueTable<String, String> extraHeaders = new MultiValueTable<String, String>();
			// Force download to disk
			extraHeaders.put("Content-Disposition", "attachment; filename=myref.fref");
			this.writeReply(ctx, 200, "application/x-freenet-reference", "OK", extraHeaders,
				sw.toString());
			return;
		}
		if (path.endsWith("myref.txt")) {
			SimpleFieldSet fs = getNoderef();
			StringWriter sw = new StringWriter();
			fs.writeTo(sw);
			this.writeTextReply(ctx, 200, "OK", sw.toString());
			return;
		}
		if (path.endsWith(NodeUpdateManager.WINDOWS_FILENAME)) {
			File installer = node.nodeUpdater.getInstallerWindows();
			if (installer != null) {
				FileBucket bucket = new FileBucket(installer, true, false, false, false, false);
				this.writeReply(ctx, 200, "application/x-msdownload", "OK", bucket);
				return;
			}
		}
		if (path.endsWith(NodeUpdateManager.NON_WINDOWS_FILENAME)) {
			File installer = node.nodeUpdater.getInstallerNonWindows();
			if (installer != null) {
				FileBucket bucket = new FileBucket(installer, true, false, false, false, false);
				this.writeReply(ctx, 200, "application/x-java-archive", "OK", bucket);
				return;
			}
		}
		PageMaker pageMaker = ctx.getPageMaker();
		Page addDarknetRef = pageMaker.getPage(l10n("title"), ctx);
		addDarknetRef.content.addChild(core.alerts.createSummary());
		InfoboxWidget darknetExplainations = addDarknetRef.content
			.addInfobox(InfoboxWidget.Type.INFORMATION, Identifier.DARKNETEXPLAINATIONS,
				l10n("explainBoxTitle"));
		darknetExplainations.body.addText(l10n("explainBox1"));
		darknetExplainations.body.addText(l10n("explainBox2"));
		File installer = node.nodeUpdater.getInstallerWindows();
		String shortFilename = NodeUpdateManager.WINDOWS_FILENAME;
		BlockText p = darknetExplainations.body.addBlockText();
		if (installer != null) {
			NodeL10n.getBase().addL10nSubstitution(p, "DarknetAddRefToadlet.explainInstallerWindows",
				new String[]{"filename", "get-windows"},
				new HTMLNode[]{new Text(installer.getCanonicalPath()),
					new Link(path() + shortFilename)});
		} else {
			NodeL10n.getBase().addL10nSubstitution(p, "DarknetAddRefToadlet" +
				".explainInstallerWindowsNotYet",
				new String[]{"link"},
				new HTMLNode[]{new Link("/" + node.nodeUpdater.getInstallerWindowsURI().toString())});
		}
		installer = node.nodeUpdater.getInstallerNonWindows();
		shortFilename = NodeUpdateManager.NON_WINDOWS_FILENAME;
		darknetExplainations.body.addText(" ");
		p = darknetExplainations.body.addBlockText();
		if (installer != null) {
			NodeL10n.getBase().addL10nSubstitution(p, "DarknetAddRefToadlet.explainInstallerNonWindows",
				new String[]{"filename", "get-nonwindows", "shortfilename"},
				new HTMLNode[]{new Text(installer.getCanonicalPath()),
					new Link(path() + shortFilename),
					new Text(shortFilename)});
		} else {
			NodeL10n.getBase()
				.addL10nSubstitution(p, "DarknetAddRefToadlet.explainInstallerNonWindowsNotYet",
					new String[]{"link", "shortfilename"}, new HTMLNode[]{
					new Link("/" + node.nodeUpdater.getInstallerNonWindowsURI().toString()),
					new Text(shortFilename)});
		}
		ConnectionsToadlet.drawAddPeerBox(addDarknetRef.content, ctx, false, "/friends/");
		ConnectionsToadlet.drawNoderefBox(addDarknetRef.content, getNoderef(),
			pageMaker.advancedMode(request, this.container));
		this.writeHTMLReply(ctx, 200, "OK", addDarknetRef.generate());
	}

	protected SimpleFieldSet getNoderef() {
		return node.exportDarknetPublicFieldSet();
	}
	
	private static String l10n(String string) {
		return NodeL10n.getBase().getString("DarknetAddRefToadlet."+string);
	}

	static final String PATH = "/addfriend/";

	@Override
	public String path() {
		return PATH;
	}
}
