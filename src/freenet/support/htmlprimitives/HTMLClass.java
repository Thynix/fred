package freenet.support.htmlprimitives;

public enum HTMLClass {

	ALERTSLINE("alerts-line"),
	BOOKMARKSBOX("bookmarks-box"),
	CONFIGPREFIX("configprefix"),
	CONTAINSALERT("contains-alert"),
	CONTAINSINFORMATION("contains-information"),
	CONTAINSWARNING("contains-warning"),
	DARKNETDIV("darknetDiv"),
	HIDDEN("hidden"),
	HISTOGRAMLABEL("histogramLabel"),
	HISTOGRAMDISCONNECTED("histogramDisconnected"),
	HISTOGRAMCONNECTED("histogramConnected"),
	INFOBOX("infobox"),
	INFOBOXALERT("infobox-alert"),
	INFOBOXCONTENT("infobox-content"),
	INFOBOXERROR("infobox-error"),
	INFOBOXHEADER("infobox-header"),
	INFOBOXINFORMATION("infobox-information"),
	INFOBOXMINOR("infobox-minor"),
	INFOBOXNORMAL("infobox-normal"),
	INFOBOXQUERY("infobox-query"),
	INFOBOXSUCCESS("infobox-success"),
	INFOBOXWARNING("infobox-warning"),
	N2NTMMESSAGETEXT("n2ntm-message-text"),
	NONE("none"),
	NULL(""),
	NOWRAP("NOWRAP"),
	OPENNETDIV("opennetDiv"),
	PEERCIRCLE("peercircle"),
	PEERSAVERAGE("avg-peers"),
	PEERSFEW("few-peers"),
	PEERSFULL("full-peers"),
	PEERSVERYFEW("very-few-peers"),
	PLUGIN("plugin"),
	PLUGINGROUP("plugin-group"),
	PLUGINGROUPTITLE("plugin-group-title"),
	PROGRESSBAR("progressbar"),
	PROGRESSBARDONE("progressbar-done"),
	PROGRESSBARFAILED("progressbar-failed"),
	PROGRESSBARFAILED2("progressbar-failed2"),
	PROGRESSBARFINAL("progress_fraction_finalized"),
	PROGRESSBARMIN("progressbar-min"),
	PROGRESSBARNOTFINAL("progress_fraction_not_finalized"),
	PROGRESSBARPEERS("progressbar-peers"),
	REQUESTDELETE("request-delete"),
	REQUESTDELETEFINISHEDDOWNLOADS("request-delete-finished-downloads"),
	REQUESTPRIORITY("request-priority"),
	REQUESTRECOMMEND("request-recommend"),
	REQUESTTABLEFORM("request-table-form"),
	SEPERATOR("separator"),
	TOGGLEABLE("toggleable"),
	TRANSLATION("translation"),
	WARNING("warning");

	public final String name;

	private HTMLClass(String CLASSNAME) {
		this.name = CLASSNAME;
	}
}
