package freenet.clients.http.bookmark;

import freenet.clients.http.uielements.*;

import java.util.List;

public class BookmarkList extends OutputList {
	private	void addCategoryToList(BookmarkCategory cat, OutputList list, boolean showActiveLinks) {
		List<BookmarkItem> items = cat.getItems();
		if (items.size() > 0) {
			Table table = list.addItem().addTable(Category.BOOKMARKLIST);
			for (int i = 0; i < items.size(); i++) {
				BookmarkItem item = items.get(i);
				Row bookmarkItemRow = table.addRow(Category.BOOKMARKLIST);
				if (item.hasAnActivelink() && showActiveLinks) {
					String initialKey = item.getKey();
					String key = '/' + initialKey + (initialKey.endsWith("/") ? "" : "/") +
					             "activelink.png";
					bookmarkItemRow.addCell(Category.BOOKMARKLIST).addLink('/' + item.getKey())
					               .addChild("img",
					                         new String[]{"src", "height", "width", "alt", "title"},
					                         new String[]{key, "36", "108", "activelink",
					                                      item.getDescription()});
				} else {
					bookmarkItemRow.addCell(Category.BOOKMARKLIST).addText(" ");
				}
				Cell linkCell = bookmarkItemRow.addCell(Category.BOOKMARKLIST);
				linkCell.addLink('/' + item.getKey(), item.getDescription(), Category.BOOKMARKTITLE,
				                 item.getVisibleName());
				String explain = item.getShortDescription();
				if (explain != null && explain.length() > 0) {
					linkCell.addText(" (");
					linkCell.addText(explain);
					linkCell.addText(")");
				}
			}
		}
		List<BookmarkCategory> cats = cat.getSubCategories();
		for (int i = 0; i < cats.size(); i++) {
			list.addItem(Category.CAT, cats.get(i).getVisibleName());
			addCategoryToList(cats.get(i), list.addItem().addList(), showActiveLinks);
		}
	}

	public BookmarkList(boolean restricted, boolean showActivelinks) {
		super(Identifier.BOOKMARKS);
		if (! restricted) {
			addCategoryToList(BookmarkManager.MAIN_CATEGORY, this, showActivelinks);
		} else {
			addCategoryToList(BookmarkManager.DEFAULT_CATEGORY, this, showActivelinks);
		}
	}
}

