package freenet.clients.http.uielements;

public enum HTMLID {
	BOOKMARKS("bookmarks"),
	BOOKMARKEDIT("bookmarkedit"),
	CONTENT("content"),
	COMPLETEDDOWNLOADTODISK("completedDownloadToDisk"),
	COMPLETEDDOWNLOADTOTEMP("completedDownloadToTemp"),
	COMPLETEDDIRUPLOAD("completedDirUpload"),
	COMPLETEDUPLOAD("completedUpload"),
	FAILEDDIRUPLOAD("failedDirUpload"),
	FAILEDDOWNLOAD("failedDownload"),
	FAILEDUPLOAD("failedUpload"),
	PAGE("page"),
	PERSISTENCEFIELDS("persistenceFields"),
	MESSAGESUMMARYBOX("messages-summary-box"),
	NAVBAR("navbar"),
	NAVLIST("navlist"),
	N2NBOX("n2nbox"),
	SEARCH("search-freenet"),
	SELECTEDSUBNAVBAR("selected-subnavbar"),
	SELECTEDSUBNAVBARLIST("selected-subnavbar-list"),
	STATUSBAR("statusbar"),
	STATUSBARALERTS("statusbar-alerts"),
	STATUSBARCONTAINER("statusbar-container"),
	STATUSBARLANGUAGE("statusbar-language"),
	STATUSBARSECLEVELS("statusbar-seclevels"),
	STATUSBARSWITCHMODE("statusbar-switchmode"),
	TOPBAR("topbar"),
	UNCOMPLETEDDIRUPLOAD("uncompletedDirUpload"),
	UNCOMPLETEDDOWNLOAD("uncompletedDownload"),
	UNCOMPLETEDUPLOAD("uncompletedUpload");

	public final String name;

	private HTMLID(String ID) {
		this.name = ID;
	}
}
