package freenet.support.htmlprimitives;

public enum HTMLID {
	CONTENT("content"),
	PAGE("page"),
	PERSISTENCEFIELDS("persistenceFields"),
	MESSAGESUMMARYBOX("messages-summary-box"),
	NAVBAR("navbar"),
	N2NBOX("n2nbox"),
	SELECTEDSUBNAVBAR("selected-subnavbar"),
	STATUSBAR("statusbar"),
	STATUSBARCONTAINER("statusbar-container"),
	STATUSBARLANGUAGE("statusbar-language"),
	STATUSBARSECLEVELS("statusbar-seclevels"),
	STATUSBARSWITCHMODE("statusbar-switchmode"),
	TOPBAR("topbar");

	public final String name;

	private HTMLID(String ID) {
		this.name = ID;
	}
}
