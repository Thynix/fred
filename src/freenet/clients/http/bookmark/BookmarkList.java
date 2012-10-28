package freenet.clients.http.bookmark;

import freenet.clients.http.uielements.*;
import freenet.l10n.NodeL10n;
import freenet.support.URLEncoder;

import java.util.List;

public class BookmarkList extends OutputList {

	final String edit = NodeL10n.getBase().getString("BookmarkEditorToadlet.edit");
	final String delete = NodeL10n.getBase().getString("BookmarkEditorToadlet.delete");
	final String cut = NodeL10n.getBase().getString("BookmarkEditorToadlet.cut");
	final String moveUp = NodeL10n.getBase().getString("BookmarkEditorToadlet.moveUp");
	final String moveDown = NodeL10n.getBase().getString("BookmarkEditorToadlet.moveDown");
	final String paste = NodeL10n.getBase().getString("BookmarkEditorToadlet.paste");
	final String addBookmark = NodeL10n.getBase().getString("BookmarkEditorToadlet.addBookmark");
	final String addCategory = NodeL10n.getBase().getString("BookmarkEditorToadlet.addCategory");

	private OutputList addRootNode(OutputList list, boolean paste) {
		Item root = list.addItem(Category.CAT);
		root.addClass(Category.ROOT);
		root.addText("/");
		root.addInlineBox(new EditLinks("/", true, false, paste, false, false));
		return root.addList();
	}
	private void addCategoryToList(BookmarkCategory cat, OutputList list, boolean showActiveLinks, String path,
	                               String cutPath, boolean showEditLinks, boolean hasFriends, boolean root) {
		if (! root) {
			List<BookmarkItem> items = cat.getItems();
			if (items.size() > 0) {
				Table table = list.addItem().addTable(Category.BOOKMARKLIST);
				for (int i = 0; i < items.size(); i++) {
					String itemPath = path + items.get(i).getName() + '/';
					BookmarkItem item = items.get(i);
					Row bookmarkItemRow = table.addRow(Category.BOOKMARKLIST);
					if (item.hasAnActivelink() && showActiveLinks) {
						String initialKey = item.getKey();
						String key = '/' + initialKey + (initialKey.endsWith("/") ? "" :
							"/") +
							"activelink.png";
						bookmarkItemRow.addCell(Category.BOOKMARKLIST)
							.addLink('/' + item.getKey())
							.addChild("img",
								new String[]{"src", "height", "width", "alt",
									"title"},
								new String[]{key, "36", "108", "activelink",
									item.getDescription()});
					} else {
						bookmarkItemRow.addCell(Category.BOOKMARKLIST).addText(" ");
					}
					Cell linkCell = bookmarkItemRow.addCell(Category.BOOKMARKLIST);
					linkCell.addLink('/' + item.getKey(), item.getDescription(),
						Category.BOOKMARKTITLE, item.getVisibleName());
					String explain = item.getShortDescription();
					if (explain != null && explain.length() > 0) {
						linkCell.addText(" (");
						linkCell.addText(explain);
						linkCell.addText(")");
					}
					if (showEditLinks) {
						linkCell.addInlineBox(
							new EditLinks(getPathEncoded(path, items.get(i)), false,
								showCut(cutPath), showPaste(cutPath,
								getPathEncoded(itemPath, items.get(i))), (i != 0),
								((i + 1) < items.size())));
					}
					if (hasFriends) {
						linkCell.addLink("?action=share&bookmark=" +
							URLEncoder.encode(path + item.getName(), false),
							NodeL10n.getBase().getString("BookmarkEditorToadlet.share"));
					}
				}
			}
			List<BookmarkCategory> cats = cat.getSubCategories();
			for (int i = 0; i < cats.size(); i++) {
				String catPath = path + cats.get(i).getName() + '/';
				Item subCategory = new Item(Category.CAT);
				subCategory.addText(cats.get(i).getVisibleName());
				if (showEditLinks) {
					subCategory.addInlineBox(new EditLinks(getPathEncoded(path, cats.get(i)),
						false,
						showCut(cutPath),
						showPaste(cutPath, getPathEncoded(catPath, cats.get(i))), (i > 0),
						((i + 1) < cats.size())));
				}
				list.addItem(subCategory);
				addCategoryToList(cats.get(i), list.addItem().addList(), showActiveLinks,
					path + cats.get(i).getName() + '/', cutPath, showEditLinks, hasFriends,
					false);
			}
		} else {
			addCategoryToList(BookmarkManager.MAIN_CATEGORY,
				addRootNode(list, (cutPath != null)), showActiveLinks, "/", cutPath, showEditLinks,
				hasFriends, false);
		}
	}
	private boolean showCut(String cutPath) {
		return cutPath == null;
	}
	private boolean showPaste(String cutPath, String catPathEncoded) {
		if (cutPath == null) {
			return false;
		} else if (catPathEncoded.startsWith(cutPath) || (catPathEncoded.equals(parentPath(cutPath)))) {
			return false;
		} else {
			return true;
		}
	}
	private String getPathEncoded(String path, BookmarkCategory item) {
		String catPath = path + item.getName() + '/';
		return URLEncoder.encode(catPath, false);
	}
	private String getPathEncoded(String path, BookmarkItem item) {
		String catPath = path + item.getName() + '/';
		return URLEncoder.encode(catPath, false);
	}
	private String parentPath(String path) {
		if (path.equals("/")) {
			return "/";
		}
		return path.substring(0, path.substring(0, path.length() - 1).lastIndexOf("/")) + "/";
	}
	private class EditLinks extends InlineBox {
		public EditLinks(String catPathEncoded, boolean root, boolean includeCut, boolean includePaste,
		                 boolean includeUp,
		                 boolean includeDown) {
			super(Category.ACTIONS);
			if (! root) {
				this.addLink("?action=edit&bookmark=" + catPathEncoded)
					.addChild("img", new String[]{"src", "alt", "title"},
						new String[]{"/static/icon/edit.png", edit, edit});
				this.addLink("?action=del&bookmark=" + catPathEncoded)
					.addChild("img", new String[]{"src", "alt", "title"},
						new String[]{"/static/icon/delete.png", delete, delete});
			}
			this.addLink("?action=addItem&bookmark=" + catPathEncoded)
				.addChild("img", new String[]{"src", "alt", "title"},
					new String[]{"/static/icon/bookmark-new.png", addBookmark, addBookmark});
			this.addLink("?action=addCat&bookmark=" + catPathEncoded)
				.addChild("img", new String[]{"src", "alt", "title"},
					new String[]{"/static/icon/folder-new.png", addCategory, addCategory});
			if (! root) {
				if (includeCut) {
					this.addLink("?action=cut&bookmark=" + catPathEncoded)
						.addChild("img", new String[]{"src", "alt", "title"},
							new String[]{"/static/icon/cut.png", cut, cut});
				}
				if (includeUp) {
					this.addLink("?action=up&bookmark=" + catPathEncoded)
						.addChild("img", new String[]{"src", "alt", "title"},
							new String[]{"/static/icon/go-up.png", moveUp, moveUp});
				}
				if (includeDown) {
					this.addLink("?action=down&bookmark=" + catPathEncoded)
						.addChild("img", new String[]{"src", "alt", "title"},
							new String[]{"/static/icon/go-down.png", moveDown, moveDown});
				}
			}
			if (includePaste) {
				this.addLink("?action=paste&bookmark=" + catPathEncoded)
					.addChild("img", new String[]{"src", "alt", "title"},
						new String[]{"/static/icon/paste.png", paste, paste});
			}
		}
	}
	public BookmarkList(boolean restricted, boolean showActivelinks) {
		this(restricted, showActivelinks, false, false, null);
	}
	public BookmarkList(String cutPath, boolean hasFriends) {
		this(false, false, true, hasFriends, cutPath);
	}
	private BookmarkList(boolean restricted, boolean showActivelinks, boolean showEditLinks, boolean hasFriends,
	                     String cutPath) {
		super(Identifier.BOOKMARKS);
		if (! restricted) {
			addCategoryToList(BookmarkManager.MAIN_CATEGORY, this, showActivelinks, "/", cutPath,
				showEditLinks, hasFriends, showEditLinks);
		} else {
			addCategoryToList(BookmarkManager.DEFAULT_CATEGORY, this, showActivelinks, "/", cutPath,
				showEditLinks, hasFriends, showEditLinks);
		}
	}
}
