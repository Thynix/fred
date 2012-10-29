package freenet.clients.http.constants;


public enum Path {
	ADDFRIEND("FProxyToadlet.categoryFriends", "/addfriend/", "FProxyToadlet.addFriendTitle", "FProxyToadlet.addFriend", true, true),
	ALERTS("FProxyToadlet.categoryStatus", "/alerts/", "FProxyToadlet.alertsTitle", "FProxyToadlet.alerts", true, true),
	CHAT("FProxyToadlet.categoryChat", "/chat/", "FProxyToadlet.chatForumsTitle", "FProxyToadlet.chatForums", true, true),
	CONNECTION("FProxyToadlet.categoryStatus", "/connectivity/", "ConnectivityToadlet.connectivityTitle", "ConnectivityToadlet.connectivity", true, true),
	DIAGNOSTIC("FProxyToadlet.categoryStatus", "/diagnostic/", "FProxyToadlet.diagnosticTitle", "FProxyToadlet.diagnostic", true, true),
	DOWNLOAD("FProxyToadlet.categoryQueue", "/downloads/", "FProxyToadlet.downloadsTitle", "FProxyToadlet.downloads", true, false),
	FRIENDS("FProxyToadlet.categoryFriends", "/friends/", "FProxyToadlet.friendsTitle", "FProxyToadlet.friends", true, true),
	INSERT("FProxyToadlet.categoryBrowsing", "/insertsite/", "FProxyToadlet.insertFreesiteTitle", "FProxyToadlet.insertFreesite", true, false),
	MAIN("FProxyToadlet.categoryBrowsing", "/", "FProxyToadlet.welcomeTitle", "FProxyToadlet.welcome", false, false),
	PLUGINS("FProxyToadlet.categoryConfig", "/plugins/", "FProxyToadlet.pluginsTitle", "FProxyToadlet.plugins", true, true),
	SECLEVELS("FProxyToadlet.categoryConfig", "/seclevels/", "FProxyToadlet.seclevelsTitle", "FProxyToadlet.seclevels", true, true),
	STATS("FProxyToadlet.categoryStatus", "/stats/", "FProxyToadlet.statsTitle", "FProxyToadlet.stats", true, true),
	STRANGERS("FProxyToadlet.categoryStatus", "/strangers/", "FProxyToadlet.opennetTitle", "FProxyToadlet.opennet", true, true),
	UPLOAD("FProxyToadlet.categoryQueue", "/uploads/", "FProxyToadlet.uploadsTitle", "FProxyToadlet.uploads", true, false);

	public final String menu;
	public final String url;
	public final String name;
	public final String title;
	public final boolean priority;
	public final boolean fullOnly;

	private Path(String menu, String url, String name, String title, boolean priority, boolean fullOnly) {
		this.menu = menu;
		this.url = url;
		this.name = name;
		this.title = title;
		this.priority = priority;
		this.fullOnly = fullOnly;
	}
}

