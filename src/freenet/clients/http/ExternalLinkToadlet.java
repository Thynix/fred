package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.PageMaker.RenderParameters;
import freenet.clients.http.constants.Identifier;
import freenet.clients.http.constants.InfoboxType;
import freenet.clients.http.constants.InputType;
import freenet.clients.http.uielements.Infobox;
import freenet.clients.http.uielements.Page;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.URI;

/**
 * The External Link Toadlet 
 */
public class ExternalLinkToadlet extends Toadlet {

	private static final int MAX_URL_LENGTH = 1024 * 1024;
	public static final String PATH = "/external-link/";
	private static final String magicHTTPEscapeString = "_CHECKED_HTTP_";

	private final Node node;
	private final NodeClientCore core;

	ExternalLinkToadlet(HighLevelSimpleClient client, NodeClientCore core, Node node) {
		super(client);
		this.node = node;
		this.core = core;
	}

	@Override
	public String path() {
		return PATH;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		String password = request.getPartAsStringFailsafe("formPassword", 32);
		if ((password == null) || !password.equals(core.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", PATH);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		String url = request.getPartAsStringFailsafe(magicHTTPEscapeString, MAX_URL_LENGTH);
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		//If the user clicked cancel, or the URL is not defined, return to the main page.
		//TODO: This will mean the beginning of the first time wizard if it's still in progress.
		//TODO: Is it worth it to fix that?
		if (request.getPartAsStringFailsafe("Go", 32).isEmpty() || url.isEmpty()) {
			url = WelcomeToadlet.PATH;
		}
		headers.put("Location", url);
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		//Unexpected: a URL should have been specified.
		if (request.getParam(magicHTTPEscapeString).isEmpty()) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", WelcomeToadlet.PATH);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}
		//Confirm whether the user really means to access an HTTP link.
		//Only render status and navigation bars if the user has completed the wizard.
		boolean renderBars = node.clientCore.getToadletContainer().fproxyHasCompletedWizard();
		Page externalLinkPage = ctx.getPageMaker().getPage(l10n("confirmExternalLinkTitle"), ctx,
			new RenderParameters().renderNavigationLinks(renderBars).renderStatus(renderBars));
		Infobox externalLinkWarning = externalLinkPage.content
			.addInfobox(InfoboxType.WARNING, Identifier.CONFIRMEXTERNALLINK,
				l10n("confirmExternalLinkSubTitle"));
		HTMLNode externalLinkForm = ctx.addFormChild(externalLinkWarning.body, PATH,
			"confirmExternalLinkForm");
		final String target = request.getParam(magicHTTPEscapeString);
		externalLinkForm.addText(l10n("confirmExternalLinkWithURL", "url", target));
		externalLinkForm.addLineBreak();
		externalLinkForm.addInput(InputType.HIDDEN, magicHTTPEscapeString, target);
		externalLinkForm.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
		externalLinkForm.addInput(InputType.SUBMIT, "Go", l10n("goToExternalLink"));
		this.writeHTMLReply(ctx, 200, "OK", externalLinkPage.generate());
	}

	/**
	 * Prepends a given URI with the path and parameter names to get this external link confirmation page.
	 * @param uri URI to prompt for confirmation.
	 * @return String appropriate for a link.
	 */
	public static String escape(String uri) {
		return ExternalLinkToadlet.PATH+"?" + magicHTTPEscapeString + '=' + uri;
	}

	private static String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("WelcomeToadlet." + key, new String[]{pattern}, new String[]{value});
	}

	private static String l10n(String key) {
		return NodeL10n.getBase().getString("WelcomeToadlet." + key);
	}
}
