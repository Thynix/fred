package freenet.clients.http.constants;

public enum InfoboxType {
	ALERT(Category.INFOBOXALERT),
	ERROR(Category.INFOBOXERROR),
	FAILEDREQUESTS(Category.FAILEDREQUESTS),
	INFORMATION(Category.INFOBOXINFORMATION),
	LEGEND(Category.LEGEND),
	MINOR(Category.INFOBOXMINOR),
	NAVBAR(Category.NAVBAR),
	NORMAL(Category.INFOBOXNORMAL),
	NONE(Category.NONE),
	PROGRESSING(Category.REQUESTSINPROGRESS),
	QUERY(Category.INFOBOXQUERY),
	REQUESTCOMPLETE(Category.REQUESTCOMPLETED),
	SUCCESS(Category.INFOBOXSUCCESS),
	WARNING(Category.INFOBOXWARNING),
	WTF(Category.WTF);

	public final Category htmlclass;

	private InfoboxType(Category id) {
		this.htmlclass = id;
	}
}
