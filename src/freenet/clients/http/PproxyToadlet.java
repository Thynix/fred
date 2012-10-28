package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.pluginmanager.*;
import freenet.pluginmanager.PluginManager.OfficialPluginDescription;
import freenet.pluginmanager.PluginManager.PluginProgress;
import freenet.support.*;
import freenet.support.Logger.LogLevel;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

public class PproxyToadlet extends Toadlet {
	private static final int MAX_PLUGIN_NAME_LENGTH = 1024;
	/** Maximum time to wait for a threaded plugin to exit */
	private static final int MAX_THREADED_UNLOAD_WAIT_TIME = 60*1000;
	private final Node node;
	private final NodeClientCore core;

        private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	public PproxyToadlet(HighLevelSimpleClient client, Node node, NodeClientCore core) {
		super(client);
		this.node = node;
		this.core = core;
	}

	public void handleMethodPOST(URI uri, final HTTPRequest request, ToadletContext ctx)
	throws ToadletContextClosedException, IOException {

		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();

		String pass = request.getPartAsStringFailsafe("formPassword", 32);
		if((pass == null) || !pass.equals(core.formPassword)) {
			headers.put("Location", "/plugins/");
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, l10n("unauthorizedTitle"), l10n("unauthorized"));
			return;
		}

		String path=request.getPath();

		// remove leading / and plugins/ from path
		if(path.startsWith("/")) path = path.substring(1);
		if(path.startsWith("plugins/")) path = path.substring("plugins/".length());

		if(logMINOR) Logger.minor(this, "Pproxy received POST on "+path);

		final PluginManager pm = node.pluginManager;

		if(path.length()>0)
		{
			try
			{
				String plugin = null;
				// split path into plugin class name and 'daa' path for plugin
				int to = path.indexOf("/");
				if(to == -1)
				{
					plugin = path;
				}
				else
				{
					plugin = path.substring(0, to);
				}

				writeHTMLReply(ctx, 200, "OK", pm.handleHTTPPost(plugin, request));
			}
			catch (RedirectPluginHTTPException e) {
				writeTemporaryRedirect(ctx, e.message, e.newLocation);
			}
			catch (NotFoundPluginHTTPException e) {
				sendErrorPage(ctx, NotFoundPluginHTTPException.code, e.message, e.location);
			}
			catch (AccessDeniedPluginHTTPException e) {
				sendErrorPage(ctx, AccessDeniedPluginHTTPException.code, e.message, e.location);
			}
			catch (DownloadPluginHTTPException e) {
				// FIXME: maybe it ought to be defined like sendErrorPage : in toadlets

				MultiValueTable<String, String> head = new MultiValueTable<String, String>();
				head.put("Content-Disposition", "attachment; filename=\"" + e.filename + '"');
				ctx.sendReplyHeaders(DownloadPluginHTTPException.CODE, "Found", head, e.mimeType, e.data.length);
				ctx.writeData(e.data);
			}
			catch(PluginHTTPException e)
			{
				sendErrorPage(ctx, PluginHTTPException.code, e.message, e.location);
			}
			catch(Throwable t)
			{
				writeInternalError(t, ctx);
			}
		}
		else
		{
			PageMaker pageMaker = ctx.getPageMaker();
			
			if (request.isPartSet("submit-official")) {
				final String pluginName = request.getPartAsStringFailsafe("plugin-name", 40);
				final String pluginSource = request.getPartAsStringFailsafe("pluginSource", 10);
				
				node.executor.execute(new Runnable() {
					@Override
					public void run() {
						pm.startPluginOfficial(pluginName, true, true, "https".equals(pluginSource));
					}
				});
				
				headers.put("Location", ".");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}
			if (request.isPartSet("submit-other")) {
				final String pluginName = request.getPartAsStringFailsafe("plugin-url", 200);
				final boolean fileonly = "on".equalsIgnoreCase(request.getPartAsStringFailsafe("fileonly", 20));
				
				node.executor.execute(new Runnable() {
					@Override
					public void run() {
						if (fileonly) 
							pm.startPluginFile(pluginName, true);
						else
							pm.startPluginURL(pluginName, true);
					}
				});

				headers.put("Location", ".");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}
			if (request.isPartSet("submit-freenet")) {
				final String pluginName = request.getPartAsStringFailsafe("plugin-uri", 300);
				
				node.executor.execute(new Runnable() {
					@Override
					public void run() {
						pm.startPluginFreenet(pluginName, true);
					}
				});

				headers.put("Location", ".");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}
			if (request.isPartSet("cancel")){
				headers.put("Location", "/plugins/");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}
			if (request.getPartAsStringFailsafe("unloadconfirm", MAX_PLUGIN_NAME_LENGTH).length() > 0) {
				String pluginThreadName = request.getPartAsStringFailsafe("unloadconfirm", MAX_PLUGIN_NAME_LENGTH);
				String pluginSpecification = getPluginSpecification(pm, pluginThreadName);
				pm.killPlugin(pluginThreadName, MAX_THREADED_UNLOAD_WAIT_TIME, false);
				if (request.isPartSet("purge")) {
					pm.removeCachedCopy(pluginSpecification);
				}
				Page page = pageMaker.getPage(l10n("plugins"), ctx);
				Infobox success = page.content.addInfobox(InfoboxType.SUCCESS, l10n("pluginUnloaded"));
				success.body.addText(l10n("pluginUnloadedWithName", "name", pluginThreadName));
				success.body.addLineBreak();
				success.body.addLink("/plugins/", l10n("returnToPluginPage"));
				writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} if (request.getPartAsStringFailsafe("unload", MAX_PLUGIN_NAME_LENGTH).length() > 0) {
				Page page = pageMaker.getPage(l10n("plugins"), ctx);
				Infobox query = page.content.addInfobox(InfoboxType.QUERY, l10n("unloadPluginTitle"));
				query.body.addText(l10n("unloadPluginWithName", "name", request.getPartAsStringFailsafe("unload", MAX_PLUGIN_NAME_LENGTH)));
				HTMLNode unloadForm = ctx.addFormChild(query.body, "/plugins/",
					"unloadPluginConfirmForm");
				unloadForm.addInput(InputType.HIDDEN, "unloadconfirm", request.getPartAsStringFailsafe("unload", MAX_PLUGIN_NAME_LENGTH));
				HTMLNode tempNode = unloadForm.addBlockText();
				tempNode.addInput("purge", InputType.CHECKBOX);
				tempNode.addText(l10n("unloadPurge"));
				tempNode = unloadForm.addBlockText();
				tempNode.addInput(InputType.SUBMIT, "confirm", l10n("unload"));
				tempNode.addText(" ");
				tempNode.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
				writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} else if (request.getPartAsStringFailsafe("reload", MAX_PLUGIN_NAME_LENGTH).length() > 0) {
				Page page = pageMaker.getPage(l10n("plugins"), ctx);
				Infobox reloadPlugin = page.content.addInfobox(InfoboxType.QUERY, Identifier.PLUGINRELOAD, l10n("reloadPluginTitle"));
				reloadPlugin.body.addBlockText(l10n("reloadExplanation"));
				reloadPlugin.body.addBlockText(l10n("reloadWarning"));
				HTMLNode reloadForm = ctx.addFormChild(reloadPlugin.body, "/plugins/", "reloadPluginConfirmForm");
				reloadForm.addInput(InputType.HIDDEN, "reloadconfirm", request.getPartAsStringFailsafe("reload", MAX_PLUGIN_NAME_LENGTH));
				HTMLNode tempNode = reloadForm.addBlockText();
				tempNode.addInput("purge", InputType.CHECKBOX);
				tempNode.addText(l10n("reloadPurgeWarning"));
				tempNode = reloadForm.addBlockText();
				tempNode.addInput(InputType.SUBMIT, "confirm", l10n("reload"));
				tempNode.addText(" ");
				tempNode.addInput(InputType.SUBMIT, "cancel", NodeL10n.getBase().getString("Toadlet.cancel"));
				
				writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} else if (request.getPartAsStringFailsafe("update", MAX_PLUGIN_NAME_LENGTH).length() > 0) {
				// Deploy the plugin update
				final String pluginFilename = request.getPartAsStringFailsafe("update", MAX_PLUGIN_NAME_LENGTH);

				if (!pm.isPluginLoaded(pluginFilename)) {
					sendErrorPage(ctx, 404, l10n("pluginNotFoundUpdatingTitle"), 
							l10n("pluginNotFoundUpdating", "name", pluginFilename));
				} else {
					node.nodeUpdater.deployPluginWhenReady(pluginFilename);

					headers.put("Location", ".");
					ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				}
				return;
				
			}else if (request.getPartAsStringFailsafe("reloadconfirm", MAX_PLUGIN_NAME_LENGTH).length() > 0) {
				boolean purge = request.isPartSet("purge");
				String pluginThreadName = request.getPartAsStringFailsafe("reloadconfirm", MAX_PLUGIN_NAME_LENGTH);
				final String fn = getPluginSpecification(pm, pluginThreadName);

				if (fn == null) {
					sendErrorPage(ctx, 404, l10n("pluginNotFoundReloadTitle"), 
							l10n("pluginNotFoundReload"));
				} else {
					pm.killPlugin(pluginThreadName, MAX_THREADED_UNLOAD_WAIT_TIME, true);
					if (purge) {
						pm.removeCachedCopy(fn);
					}
					node.executor.execute(new Runnable() {

						@Override
						public void run() {
							// FIXME
							pm.startPluginAuto(fn, true);
						}
						
					});

					headers.put("Location", ".");
					ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				}
				return;
			}else {
				// Ignore
				headers.put("Location", ".");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			}

		}

	}

	/**
	 * Searches all plugins for the plugin with the given thread name and
	 * returns the plugin specification used to load the plugin.
	 * 
	 * @param pluginManager
	 *            The plugin manager
	 * @param pluginThreadName
	 *            The thread name of the plugin
	 * @return The plugin specification of the plugin, or <code>null</code> if
	 *         no plugin was found
	 */
	private String getPluginSpecification(PluginManager pluginManager, String pluginThreadName) {
		Iterator<PluginInfoWrapper> it = pluginManager.getPlugins().iterator();
		while (it.hasNext()) {
			PluginInfoWrapper pi = it.next();
			if (pi.getThreadName().equals(pluginThreadName)) {
				return pi.getFilename();
			}
		}
		return null;
	}

	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("PproxyToadlet."+key, new String[] { pattern }, new String[] { value });
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("PproxyToadlet."+key);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx)
	throws ToadletContextClosedException, IOException {

		//String basepath = "/plugins/";
		String path = request.getPath();

		// remove leading / and plugins/ from path
		if(path.startsWith("/")) path = path.substring(1);
		if(path.startsWith("plugins/")) path = path.substring("plugins/".length());

		PluginManager pm = node.pluginManager;

		if(logMINOR)
			Logger.minor(this, "Pproxy fetching "+path);
		try {
			if (path.equals("")) {
				if (!ctx.isAllowedFullAccess()) {
					super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
					return;
				}
				Iterator<PluginProgress> loadingPlugins = pm.getStartingPlugins().iterator();
				Page page = ctx.getPageMaker().getPage(l10n("plugins"), ctx);
				boolean advancedModeEnabled = ctx.getContainer().isAdvancedModeEnabled();
				if (loadingPlugins.hasNext()) {
					/* okay, add a refresh. */
					page.root.head.addMeta("refresh", "10; url=");
				}
				page.content.addChild(core.alerts.createSummary());
				/* find which plugins have already been loaded. */
				List<OfficialPluginDescription> availablePlugins = pm.findAvailablePlugins();
				Iterator<PluginInfoWrapper> loadedPlugins = pm.getPlugins().iterator();
				while (loadedPlugins.hasNext()) {
					PluginInfoWrapper pluginInfoWrapper = loadedPlugins.next();
					String pluginName = pluginInfoWrapper.getPluginClassName();
					String shortPluginName = pluginName.substring(pluginName.lastIndexOf('.') + 1);
					/* FIXME: Workaround the "Freemail" plugin show duplicate problem
					 * The "Freemail" plugin is show on "Aviliable Plugin" even
					 * if it is loaded. However fixing the plugin itself may break
					 * running it as standalone application. */
					if (shortPluginName.equals("FreemailPlugin")) shortPluginName = "Freemail"; // DOH!
					availablePlugins.remove(pm.isOfficialPlugin(shortPluginName));
				}
				while (loadingPlugins.hasNext()) {
					PluginProgress pluginProgress = loadingPlugins.next();
					String pluginName = pluginProgress.getName();
					availablePlugins.remove(pm.isOfficialPlugin(pluginName));
				}
				/* sort available plugins into groups. */
				SortedMap<String, List<OfficialPluginDescription>> groupedAvailablePlugins = new TreeMap<String, List<OfficialPluginDescription>>();
				for (OfficialPluginDescription pluginDescription : availablePlugins) {
					if (!advancedModeEnabled && (pluginDescription.advanced || pluginDescription.experimental || pluginDescription.deprecated)) {
						continue;
					}
					String translatedGroup = l10n("pluginGroup." + pluginDescription.group);
					if (!groupedAvailablePlugins.containsKey(translatedGroup)) {
						groupedAvailablePlugins.put(translatedGroup, new ArrayList<OfficialPluginDescription>());
					}
					groupedAvailablePlugins.get(translatedGroup).add(pluginDescription);
				}
				for (List<OfficialPluginDescription> pluginDescriptions : groupedAvailablePlugins.values()) {
					Collections.sort(pluginDescriptions, new Comparator<OfficialPluginDescription>() {
						/**
						 * {@inheritDoc}
						 */
						@Override
						public int compare(OfficialPluginDescription o1, OfficialPluginDescription o2) {
							return o1.name.compareTo(o2.name);
						}
					});
				}
				showStartingPlugins(pm, page.content);
				showPluginList(ctx, pm, page.content, advancedModeEnabled);
				showOfficialPluginLoader(ctx, page.content, groupedAvailablePlugins, pm, advancedModeEnabled);
				showUnofficialPluginLoader(ctx, page.content);
				showFreenetPluginLoader(ctx, page.content);
				writeHTMLReply(ctx, 200, "OK", page.generate());
			} else {
				// split path into plugin class name and 'data' path for plugin
				int to = path.indexOf("/");
				String plugin;
				if (to == -1) {
					plugin = path;
				} else {
					plugin = path.substring(0, to);
				}
				// Plugin may need to know where it was accessed from, so it can e.g. produce relative URLs.
				//writeReply(ctx, 200, "text/html", "OK", mkPage("plugin", pm.handleHTTPGet(plugin, data)));
				writeHTMLReply(ctx, 200, "OK", pm.handleHTTPGet(plugin, request));				
			}
			//FetchResult result = fetch(key);
			//writeReply(ctx, 200, result.getMimeType(), "OK", result.asBucket());
		} catch (RedirectPluginHTTPException e) {
			writeTemporaryRedirect(ctx, e.message, e.newLocation);
		} catch (NotFoundPluginHTTPException e) {
			sendErrorPage(ctx, NotFoundPluginHTTPException.code, e.message, e.location);
		} catch (AccessDeniedPluginHTTPException e) {
			sendErrorPage(ctx, AccessDeniedPluginHTTPException.code, e.message, e.location);
		} catch (DownloadPluginHTTPException e) {
			// FIXME: maybe it ought to be defined like sendErrorPage : in toadlets
			MultiValueTable<String, String> head = new MultiValueTable<String, String>();
			head.put("Content-Disposition", "attachment; filename=\"" + e.filename + '"');
			ctx.sendReplyHeaders(DownloadPluginHTTPException.CODE, "Found", head, e.mimeType, e.data.length);
			ctx.writeData(e.data);
		} catch(PluginHTTPException e) {
			sendErrorPage(ctx, PluginHTTPException.code, e.message, e.location);
		} catch (SocketException e) {
			ctx.forceDisconnect();
		} catch (Throwable t) {
			ctx.forceDisconnect();
			Logger.error(this, "Caught: "+t, t);
			writeInternalError(t, ctx);
		}
	}

	/**
	 * Shows a list of all currently loading plugins.
	 * 
	 * @param pluginManager
	 *            The plugin manager
	 * @param contentNode
	 *            The node to add content to
	 */
	private void showStartingPlugins(PluginManager pluginManager, HTMLNode contentNode) {
		Set<PluginProgress> startingPlugins = pluginManager.getStartingPlugins();
		if (!startingPlugins.isEmpty()) {
			Infobox startingPluginsBox = new Infobox(InfoboxType.NORMAL, l10n("startingPluginsTitle"));
			contentNode.addChild(startingPluginsBox);
			Table startingPluginsTable = startingPluginsBox.body.addTable();
			Row startingPluginsHeader = startingPluginsTable.addRow();
			startingPluginsHeader.addHeader(l10n("startingPluginName"));
			startingPluginsHeader.addHeader(l10n("startingPluginStatus"));
			startingPluginsHeader.addHeader(l10n("startingPluginTime"));
			Iterator<PluginProgress> startingPluginsIterator = startingPlugins.iterator();
			while (startingPluginsIterator.hasNext()) {
				PluginProgress pluginProgress = startingPluginsIterator.next();
				Row startingPluginsRow = startingPluginsTable.addRow();
				startingPluginsRow.addCell(pluginProgress.getName());
				startingPluginsRow.addChild(pluginProgress.toLocalisedHTML());
				startingPluginsRow.addCell(Category.ALIGNRIGHT, TimeUtil.formatTime(pluginProgress.getTime()));
			}
		}
	}

	private void showPluginList(ToadletContext ctx, PluginManager pm, HTMLNode contentNode, boolean advancedMode) throws ToadletContextClosedException, IOException {
		Infobox pluginList = new Infobox(InfoboxType.NORMAL, NodeL10n.getBase().getString("PluginToadlet.pluginListTitle"));
		contentNode.addChild(pluginList);
		if (pm.getPlugins().isEmpty()) {
			pluginList.body.addBox(Category.NONE, l10n("noPlugins"));
		} else {
			Table pluginTable = pluginList.body.addTable(Category.PLUGINS);
			Row headerRow = pluginTable.addRow();
			headerRow.addHeader(l10n("pluginFilename"));
			if (advancedMode) {
				headerRow.addHeader(l10n("classNameTitle"));
			}
			headerRow.addHeader(l10n("versionTitle"));
			if (advancedMode) {
				headerRow.addHeader(l10n("internalIDTitle"));
				headerRow.addHeader(l10n("startedAtTitle"));
			}
			headerRow.addHeader();
			headerRow.addHeader();
			headerRow.addHeader();
			Iterator<PluginInfoWrapper> it = pm.getPlugins().iterator();
			while (it.hasNext()) {
				PluginInfoWrapper pi = it.next();
				Row pluginRow = pluginTable.addRow();
				pluginRow.addCell(pi.getFilename());
				if(advancedMode)
					pluginRow.addCell(pi.getPluginClassName());
				long ver = pi.getPluginLongVersion();
				if(ver != -1)
					pluginRow.addCell(pi.getPluginVersion()+" ("+ver+")");
				else
					pluginRow.addCell(pi.getPluginVersion());
				if(advancedMode) {
					pluginRow.addCell(pi.getThreadName());
					pluginRow.addCell(new Date(pi.getStarted()).toString());
				}
				if (pi.isStopping()) {
					pluginRow.addCell(l10n("pluginStopping"));
					/* add two empty cells. */
					pluginRow.addCell();
					pluginRow.addCell();
				} else {
					if (pi.isPproxyPlugin()) {
						Form visitForm = pluginRow.addCell().addForm(pi.getPluginClassName(), "get", Target.BLANK);
						visitForm.addInput(InputType.HIDDEN, "formPassword", core.formPassword);
						visitForm.addInput(InputType.SUBMIT, NodeL10n.getBase().getString("PluginToadlet.visit"));
					} else
						pluginRow.addCell();
					HTMLNode unloadForm = ctx.addFormChild(pluginRow.addCell(), ".", "unloadPluginForm");
					unloadForm.addInput(InputType.HIDDEN, "unload", pi.getThreadName());
					unloadForm.addInput(InputType.SUBMIT, l10n(
						"unload"));
					HTMLNode reloadForm = ctx.addFormChild(pluginRow.addCell(), ".", "reloadPluginForm");
					reloadForm.addInput(InputType.HIDDEN, "reload", pi.getThreadName());
					reloadForm.addInput(InputType.SUBMIT, l10n("reload"));
				}
			}
		}
	}
	
	private void showOfficialPluginLoader(ToadletContext toadletContext, HTMLNode contentNode, Map<String, List<OfficialPluginDescription>> availablePlugins, PluginManager pm, boolean advancedModeEnabled) {
		/* box for "official" plugins. */
		Infobox addOfficialPluginBox = new Infobox(InfoboxType.NORMAL, l10n("loadOfficialPlugin"));
		contentNode.addChild(addOfficialPluginBox);
		HTMLNode addOfficialForm = toadletContext.addFormChild(addOfficialPluginBox.body, ".", "addOfficialPluginForm");
		
		HTMLNode p = addOfficialForm.addBlockText();
		
		p.addText(l10n("loadOfficialPluginText"));
		
		// Over Freenet or over HTTP??
		
		p.addText(" " + l10n("pluginSourceChoice"));
		
		boolean loadFromWeb = pm.loadOfficialPluginsFromWeb();
		
		HTMLNode input = addOfficialForm.addInput(InputType.RADIO, "pluginSource", "freenet");
		if(!loadFromWeb)
			input.addAttribute("checked", "true");
		addOfficialForm.addText(l10n("pluginSourceFreenet"));
		addOfficialForm.addLineBreak();
		input = addOfficialForm.addInput(InputType.RADIO, "pluginSource", "https");
		if(loadFromWeb)
			input.addAttribute("checked", "true");
		addOfficialForm.addText(l10n("pluginSourceHTTPS"));
		addOfficialForm.addText(" ");
		if(node.getOpennet() == null)
			// FIXME CSS-ize this
			addOfficialForm.addInlineBox(Category.BOLD).addChild("font", "color", "red", l10n("pluginSourceHTTPSWarningDarknet"));
		else
			// FIXME CSS-ize this
			addOfficialForm.addInlineBox(Category.BOLD, l10n("pluginSourceHTTPSWarning"));
		
		p = addOfficialForm.addBlockText();
		
		p.addText((l10n("loadOfficialPluginLabel") + ": "));
		for (Entry<String, List<OfficialPluginDescription>> groupPlugins : availablePlugins.entrySet()) {
			HTMLNode pluginGroupNode = addOfficialForm.addBox(Category.PLUGINGROUP);
			pluginGroupNode.addBox(Category.PLUGINGROUPTITLE, l10n("pluginGroupTitle", "pluginGroup", groupPlugins.getKey()));
			for (OfficialPluginDescription pluginDescription : groupPlugins.getValue()) {
				HTMLNode pluginNode = pluginGroupNode.addBox(Category.PLUGIN);
				String pluginName = pluginDescription.name;
				if(!pm.isPluginLoaded(pluginName)) {
					HTMLNode option = pluginNode.addInput(InputType.RADIO, "plugin-name", pluginName);
					option.addInlineBox(Category.ITALIC, pluginName);
					if(pluginDescription.deprecated)
						option.addInlineBox(Category.BOLD, " (" + l10n("loadLabelDeprecated") + ")");
					if(pluginDescription.experimental)
						option.addInlineBox(Category.BOLD, " (" + l10n("loadLabelExperimental") + ")");
					if (advancedModeEnabled && pluginDescription.minimumVersion >= 0) {
						option.addText(" (" + l10n("pluginVersion") + " " + pluginDescription.minimumVersion + ")");
					}
					option.addText(" - " + l10n("pluginDesc." + pluginName));
				}
			}
		}
		addOfficialForm.addBlockText().addInput(InputType.SUBMIT, "submit-official", l10n("Load"));
	}
	
	private void showUnofficialPluginLoader(ToadletContext toadletContext, HTMLNode contentNode) {
		/* box for unofficial plugins. */
		Infobox addOtherPluginBox = new Infobox(InfoboxType.NORMAL, l10n("loadOtherPlugin"));
		contentNode.addChild(addOtherPluginBox);
		HTMLNode addOtherForm = toadletContext.addFormChild(addOtherPluginBox.body, ".", "addOtherPluginForm");
		addOtherForm.addBox(Category.NONE , l10n("loadOtherPluginText"));
		addOtherForm.addText((l10n("loadOtherURLLabel") + ": "));
		addOtherForm.addInput(InputType.TEXT, "plugin-url", 80);
		addOtherForm.addText(" ");
		addOtherForm.addInput(InputType.SUBMIT, "submit-other", l10n("Load"));
		addOtherForm.addLineBreak();
		addOtherForm.addInput(InputType.CHECKBOX, "fileonly", true);
		addOtherForm.addText(" " + l10n("fileonly"));
	}
	
	private void showFreenetPluginLoader(ToadletContext toadletContext, HTMLNode contentNode) {
		/* box for freenet plugins. */
		Infobox addFreenetPluginBox = new Infobox(InfoboxType.NORMAL, l10n("loadFreenetPlugin"));
		contentNode.addChild(addFreenetPluginBox);
		HTMLNode addFreenetForm = toadletContext.addFormChild(addFreenetPluginBox.body, ".", "addFreenetPluginForm");
		addFreenetForm.addBox(Category.NONE, l10n("loadFreenetPluginText"));
		addFreenetForm.addText((l10n("loadFreenetURLLabel") + ": "));
		addFreenetForm.addInput(InputType.TEXT, "plugin-uri", 80);
		addFreenetForm.addText(" ");
		addFreenetForm.addInput(InputType.SUBMIT, "submit-freenet", l10n("Load"));
	}

	@Override
	public String path() {
		return PATH;
	}
	
	public static final String PATH = "/plugins/";

}
