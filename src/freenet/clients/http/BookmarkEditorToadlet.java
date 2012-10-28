package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.bookmark.*;
import freenet.clients.http.uielements.*;
import freenet.keys.FreenetURI;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.NodeClientCore;
import freenet.support.*;
import freenet.support.Logger.LogLevel;
import freenet.support.api.HTTPRequest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import static freenet.clients.http.QueueToadlet.MAX_KEY_LENGTH;

/**
 * BookmarkEditor Toadlet 
 * 
 * Accessible from http://.../bookmarkEditor/
 */
public class BookmarkEditorToadlet extends Toadlet {
	private static final int MAX_ACTION_LENGTH = 20;
	/** Max. bookmark name length */
	private static final int MAX_NAME_LENGTH = 500;
	/** Max. bookmark path length (e.g. <code>Freenet related software and documentation/Freenet Message System</code> ) */
	private static final int MAX_BOOKMARK_PATH_LENGTH = 10 * MAX_NAME_LENGTH;
	private static final int MAX_EXPLANATION_LENGTH = 1024;
	
	private final NodeClientCore core;
	private final BookmarkManager bookmarkManager;
	private String cutedPath;

        private static volatile boolean logDEBUG;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logDEBUG = Logger.shouldLog(LogLevel.DEBUG, this);
			}
		});
	}

	BookmarkEditorToadlet(HighLevelSimpleClient client, NodeClientCore core, BookmarkManager bookmarks) {
		super(client);
		this.core = core;
		this.bookmarkManager = bookmarks;
		this.cutedPath = null;
	}

	private void sendBookmarkFeeds(HTTPRequest req, BookmarkItem item, String publicDescription) {
		for(DarknetPeerNode peer : core.node.getDarknetConnections())
			if(req.isPartSet("node_" + peer.hashCode()))
				peer.sendBookmarkFeed(item.getURI(), item.getName(), publicDescription, item.hasAnActivelink());
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		if (! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"),
				NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		PageMaker pageMaker = ctx.getPageMaker();
		String editorTitle = NodeL10n.getBase().getString("BookmarkEditorToadlet.title");
		String error = NodeL10n.getBase().getString("BookmarkEditorToadlet.error");
		Page bookmarkEditor = pageMaker.getPage(editorTitle, ctx);
		String originalBookmark = req.getParam("bookmark");
		if (req.getParam("action").length() > 0 && originalBookmark.length() > 0) {
			String action = req.getParam("action");
			String bookmarkPath;
			try {
				bookmarkPath = URLDecoder.decode(originalBookmark, false);
			} catch (URLEncodedFormatException e) {
				bookmarkEditor.content
					.addInfobox(Infobox.Type.ERROR, Category.BOOKMARKURLDECODEERROR,
						error).body
					.addText(NodeL10n.getBase().getString("BookmarkEditorToadlet" +
						".urlDecodeError"));
				writeHTMLReply(ctx, 200, "OK", bookmarkEditor.generate());
				return;
			}
			Bookmark bookmark;
			if (bookmarkPath.endsWith("/")) {
				bookmark = bookmarkManager.getCategoryByPath(bookmarkPath);
			} else {
				bookmark = bookmarkManager.getItemByPath(bookmarkPath);
			}
			if (bookmark == null) {
				bookmarkEditor.content
					.addInfobox(Infobox.Type.ERROR, Category.BOOKMARKDOESNOTEXIST,
						error).body
					.addText(NodeL10n.getBase()
						.getString("BookmarkEditorToadlet.bookmarkDoesNotExist",
							new String[]{"bookmark"}, new String[]{bookmarkPath}));
				this.writeHTMLReply(ctx, 200, "OK", bookmarkEditor.generate());
				return;
			} else if ("del".equals(action)) {
				String[] bm = new String[]{"bookmark"};
				String[] path = new String[]{bookmarkPath};
				String queryTitle = NodeL10n.getBase().getString("BookmarkEditorToadlet." +
					((bookmark instanceof BookmarkItem) ? "deleteBookmark" : "deleteCategory"));
				String query = NodeL10n.getBase().getString("BookmarkEditorToadlet." +
					((bookmark instanceof BookmarkItem) ? "deleteBookmarkConfirm" :
						"deleteCategoryConfirm"), bm, path);
				Infobox confirmDeleteBookmark = bookmarkEditor.content.addInfobox(
					Infobox.Type.QUERY, Category.BOOKMARKDELETE, queryTitle);
				confirmDeleteBookmark.body.addBlockText(query);
				HTMLNode confirmForm =
					ctx.addFormChild(confirmDeleteBookmark.body, "", "confirmDeleteForm");
				confirmForm.addInput(Input.Type.HIDDEN, "bookmark", bookmarkPath);
				confirmForm.addInput(Input.Type.SUBMIT, "cancel",
						NodeL10n.getBase().getString("Toadlet.cancel"));
				confirmForm.addInput(Input.Type.SUBMIT, "confirmdelete",
						NodeL10n.getBase().getString("BookmarkEditorToadlet.confirmDelete"));
			} else if ("cut".equals(action)) {
				cutedPath = bookmarkPath;
			} else if ("paste".equals(action) && cutedPath != null) {
				bookmarkManager.moveBookmark(cutedPath, bookmarkPath);
				bookmarkManager.storeBookmarks();
				cutedPath = null;
			} else if ("edit".equals(action) || "addItem".equals(action) || "addCat".equals(action) ||
				"share".equals(action)) {
				boolean isNew = "addItem".equals(action) || "addCat".equals(action);
				String header;
				if ("edit".equals(action)) {
					header = NodeL10n.getBase().getString("BookmarkEditorToadlet.edit" +
						((bookmark instanceof BookmarkItem) ? "Bookmark" : "Category") +
						"Title");
				} else if ("addItem".equals(action)) {
					header = NodeL10n.getBase().getString("BookmarkEditorToadlet.addNewBookmark");
				} else if ("share".equals(action)) {
					header = NodeL10n.getBase().getString("BookmarkEditorToadlet.share");
				} else {
					header = NodeL10n.getBase().getString("BookmarkEditorToadlet.addNewCategory");
				}
				//bookmarkEditor.content.addInfobox(Infobox.Type.INFORMATION, "Debug").addText(req.getParam("action"));
				Infobox bookmarkAction = bookmarkEditor.content.addInfobox(Infobox.Type
					.QUERY,
					Category.BOOKMARKACTION, header);
				HTMLNode form = ctx.addFormChild(bookmarkAction.body, "", "editBookmarkForm");
				form.addChild("label", "for", "name",
					(NodeL10n.getBase().getString("BookmarkEditorToadlet.nameLabel") + ' '));
				form.addInput(Input.Type.TEXT, "name", ! isNew ? bookmark.getVisibleName() : "", Identifier.NAME, 20);
				form.addLineBreak();
				if (("edit".equals(action) && bookmark instanceof BookmarkItem) ||
					"addItem".equals(action) || "share".equals(action)) {
					BookmarkItem item = isNew ? null : (BookmarkItem) bookmark;
					String key = ! isNew ? item.getKey() : "";
					form.addChild("label", "for", "key",
						(NodeL10n.getBase().getString("BookmarkEditorToadlet.keyLabel") + ' '));
					form.addInput(Input.Type.TEXT, "key", key, Identifier.KEY, 50);
					form.addLineBreak();
					if ("edit".equals(action) || "addItem".equals(action)) {
						form.addChild("label", "for", "descB", (NodeL10n.getBase()
							.getString("BookmarkEditorToadlet.descLabel") + ' '));
						form.addLineBreak();
						form.addChild("textarea", new String[]{"id", "name", "row", "cols"},
							new String[]{"descB", "descB", "3", "70"},
							(isNew ? "" : item.getDescription()));
						form.addLineBreak();
						form.addChild("label", "for", "descB", (NodeL10n.getBase()
							.getString("BookmarkEditorToadlet.explainLabel") + ' '));
						form.addLineBreak();
						form.addChild("textarea", new String[]{"id", "name", "row", "cols"},
							new String[]{"explain", "explain", "3", "70"},
							(isNew ? "" : item.getShortDescription()));
						form.addLineBreak();
					}
					form.addChild("label", "for", "hasAnActivelink", (NodeL10n.getBase()
						.getString("BookmarkEditorToadlet.hasAnActivelinkLabel") + ' '));
					if (! isNew && item.hasAnActivelink()) {
						form.addInput(Input.Type.CHECKBOX, "hasAnActivelink", item.hasAnActivelink(), Identifier.HASACTIVELINK);
					} else {
						form.addInput(Input.Type.CHECKBOX, "hasAnActivelink", Identifier.HASACTIVELINK);
					}
					if (core.node.getDarknetConnections().length > 0 &&
						("addItem".equals(action) || "share".equals(action))) {
						form.addLineBreak();
						form.addLineBreak();
						Table peerTable = new Table(Category.DARKNETCONNECTIONS);
						form.addChild(peerTable);
						peerTable.addRow().addHeader(2, NodeL10n.getBase()
							.getString("QueueToadlet.recommendToFriends"));
						for (DarknetPeerNode peer : core.node.getDarknetConnections()) {
							Row peerRow =
								peerTable.addRow(Category.DARKNETCONNECTIONSNORMAL);
							peerRow.addCell(Category.PEERMARKER)
								.addInput("node_" + peer.hashCode(), Input.Type.CHECKBOX);
							peerRow.addCell(Category.PEERNAME).addText(peer.getName());
						}
						form.addChild("label", "for", "descB", (NodeL10n.getBase()
							.getString("BookmarkEditorToadlet.publicDescLabel") + ' '));
						form.addLineBreak();
						form.addChild("textarea", new String[]{"id", "name", "row", "cols"},
							new String[]{"descB", "publicDescB", "3", "70"},
							(isNew ? "" : item.getDescription()));
						form.addLineBreak();
					}
				}
				form.addInput(Input.Type.HIDDEN, "bookmark", bookmarkPath);
				form.addInput(Input.Type.HIDDEN, "action", req.getParam("action"));
				form.addInput(Input.Type.SUBMIT,
					"share".equals(action) ?
						NodeL10n.getBase().getString("BookmarkEditorToadlet.share") :
						NodeL10n.getBase().getString("BookmarkEditorToadlet.save"));
			} else if ("up".equals(action)) {
				bookmarkManager.moveBookmarkUp(bookmarkPath, true);
			} else if ("down".equals(action)) {
				bookmarkManager.moveBookmarkDown(bookmarkPath, true);
			}
		}
		if (cutedPath != null) {
			Infobox confirmClipboard = bookmarkEditor.content.addInfobox(Infobox.Type.NORMAL,
				NodeL10n.getBase().getString("BookmarkEditorToadlet.pasteTitle"));
			confirmClipboard.body
				.addText(NodeL10n.getBase().getString("BookmarkEditorToadlet.pasteOrCancel"));
			HTMLNode cancelForm =
				ctx.addFormChild(confirmClipboard.body, "/bookmarkEditor/", "cancelCutForm");
			cancelForm.addInput(Input.Type.SUBMIT, "cancelCut",
					NodeL10n.getBase().getString("BookmarkEditorToadlet.cancelCut"));
			cancelForm.addInput(Input.Type.HIDDEN, "action", "cancelCut");
		}
		bookmarkEditor.content.addInfobox(Infobox.Type.NORMAL, Category.BOOKMARKTITLE,
			NodeL10n.getBase().getString("BookmarkEditorToadlet.myBookmarksTitle")).body
			.addList(new BookmarkList(cutedPath, core.node.getDarknetConnections().length > 0));
		HTMLNode addDefaultBookmarksForm = ctx.addFormChild(bookmarkEditor.content, "", "AddDefaultBookmarks");
		addDefaultBookmarksForm.addInput(Input.Type.SUBMIT, "AddDefaultBookmarks",
				NodeL10n.getBase().getString("BookmarkEditorToadlet.addDefaultBookmarks"));
		if (logDEBUG) {
			Logger.debug(this, "Returning:\n" + bookmarkEditor.generate());
		}
		this.writeHTMLReply(ctx, 200, "OK", bookmarkEditor.generate());
	}

	public void handleMethodPOST(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		PageMaker pageMaker = ctx.getPageMaker();
		Page response = pageMaker.getPage(NodeL10n.getBase().getString("BookmarkEditorToadlet.title"), ctx);
		String passwd = req.getPartAsStringFailsafe("formPassword", 32);
		boolean noPassword = (passwd == null) || ! passwd.equals(core.formPassword);
		if (noPassword) {
			writePermanentRedirect(ctx, "Invalid", "");
			return;
		}
		if (req.isPartSet("AddDefaultBookmarks")) {
			bookmarkManager.reAddDefaultBookmarks();
			this.writeTemporaryRedirect(ctx, "Ok", "/");
			return;
		}
		String bookmarkPath = req.getPartAsStringFailsafe("bookmark", MAX_BOOKMARK_PATH_LENGTH);
		try {
			Bookmark bookmark;
			if (bookmarkPath.endsWith("/")) {
				bookmark = bookmarkManager.getCategoryByPath(bookmarkPath);
			} else {
				bookmark = bookmarkManager.getItemByPath(bookmarkPath);
			}
			if (bookmark == null && ! req.isPartSet("cancelCut")) {
				response.content.addInfobox(Infobox.Type.ERROR, Category.BOOKMARKERROR,
					NodeL10n.getBase().getString("BookmarkEditorToadlet.error"))
					.addText(NodeL10n.getBase()
						.getString("BookmarkEditorToadlet.bookmarkDoesNotExist",
							new String[]{"bookmark"}, new String[]{bookmarkPath}));
				this.writeHTMLReply(ctx, 200, "OK", response.generate());
				return;
			}
			String action = req.getPartAsStringFailsafe("action", MAX_ACTION_LENGTH);
			if (req.isPartSet("confirmdelete")) {
				bookmarkManager.removeBookmark(bookmarkPath);
				bookmarkManager.storeBookmarks();
				response.content.addInfobox(Infobox.Type.SUCCESS,
					Category.BOOKMARKSUCCESSFULDELETE,
					NodeL10n.getBase().getString("BookmarkEditorToadlet.deleteSucceededTitle"))
					.addBlockText(
						NodeL10n.getBase().getString("BookmarkEditorToadlet" +
							".deleteSucceeded"));
			} else if (req.isPartSet("cancelCut")) {
				cutedPath = null;
			} else if ("edit".equals(action) || "addItem".equals(action) || "addCat".equals(action)) {
				String name = "unnamed";
				if (req.isPartSet("name")) {
					name = req.getPartAsStringFailsafe("name", MAX_NAME_LENGTH);
				}
				if ("edit".equals(action)) {
					bookmarkManager.renameBookmark(bookmarkPath, name);
					boolean hasAnActivelink = req.isPartSet("hasAnActivelink");
					if (bookmark instanceof BookmarkItem) {
						BookmarkItem item = (BookmarkItem) bookmark;
						item.update(new FreenetURI(
							req.getPartAsStringFailsafe("key", MAX_KEY_LENGTH)),
							hasAnActivelink,
							req.getPartAsStringFailsafe("descB", MAX_KEY_LENGTH),
							req.getPartAsStringFailsafe("explain",
								MAX_EXPLANATION_LENGTH));
						sendBookmarkFeeds(req, item,
							req.getPartAsStringFailsafe("publicDescB", MAX_KEY_LENGTH));
					}
					bookmarkManager.storeBookmarks();
					response.content.addInfobox(Infobox.Type.SUCCESS,
						Category.BOOKMARKERROR,
						NodeL10n.getBase().getString("BookmarkEditorToadlet" +
							".changesSavedTitle"))
						.
							addBlockText(NodeL10n.getBase()
								.getString("BookmarkEditorToadlet.changesSaved"));
				} else if ("addItem".equals(action) || "addCat".equals(action)) {
					Bookmark newBookmark = null;
					if ("addItem".equals(action)) {
						FreenetURI key = new FreenetURI(
							req.getPartAsStringFailsafe("key", MAX_KEY_LENGTH));
						/* TODO:
						 * <nextgens> I suggest you implement a HTTPRequest.getBoolean
						 * (String name) using Fields.stringtobool
						 * <nextgens> HTTPRequest.getBoolean(String name,
						 * boolean default) even
						 * 
						 * - values as "on", "true", "yes" should be accepted.
						 */
						boolean hasAnActivelink = req.isPartSet("hasAnActivelink");
						if (name.contains("/")) {
							response.content.addInfobox(Infobox.Type.ERROR,
								Category.BOOKMARKERROR, NodeL10n.getBase()
								.getString("BookmarkEditorToadlet.invalidNameTitle")).
								addText(NodeL10n.getBase().getString(
									"BookmarkEditorToadlet.invalidName"));
						} else {
							newBookmark = new BookmarkItem(key, name,
								req.getPartAsStringFailsafe("descB", MAX_KEY_LENGTH),
								req.getPartAsStringFailsafe("explain",
									MAX_EXPLANATION_LENGTH), hasAnActivelink,
								core.alerts);
						}
					} else if (name.contains("/")) {
						response.content
							.addInfobox(Infobox.Type.ERROR, Category.BOOKMARKERROR,
								NodeL10n.getBase().getString(
									"BookmarkEditorToadlet.invalidNameTitle")).
							addText(NodeL10n.getBase()
								.getString("BookmarkEditorToadlet.invalidName"));
					} else {
						newBookmark = new BookmarkCategory(name);
					}
					if (newBookmark != null) {
						bookmarkManager.addBookmark(bookmarkPath, newBookmark);
						bookmarkManager.storeBookmarks();
						if (newBookmark instanceof BookmarkItem) {
							sendBookmarkFeeds(req, (BookmarkItem) newBookmark,
								req.getPartAsStringFailsafe("publicDescB",
									MAX_KEY_LENGTH));
						}
						response.content
							.addInfobox(Infobox.Type.SUCCESS,
								Category.BOOKMARKADDNEW,
								NodeL10n.getBase().getString(
									"BookmarkEditorToadlet" +
										".addedNewBookmarkTitle")).
							addBlockText(NodeL10n.getBase()
								.getString("BookmarkEditorToadlet.addedNewBookmark"));
					}
				}
			} else if ("share".equals(action)) {
				sendBookmarkFeeds(req, (BookmarkItem) bookmark,
					req.getPartAsStringFailsafe("publicDescB", MAX_KEY_LENGTH));
			}
		} catch (MalformedURLException mue) {
			response.content.addInfobox(Infobox.Type.ERROR, Category.BOOKMARKERROR,
				NodeL10n.getBase().getString("BookmarkEditorToadlet.invalidKeyTitle")).
				addText(NodeL10n.getBase().getString("BookmarkEditorToadlet.invalidKey"));
		}
		response.content.addInfobox(Infobox.Type.NORMAL, Category.BOOKMARKS,
			NodeL10n.getBase().getString("BookmarkEditorToadlet.myBookmarksTitle"))
				.addList(new BookmarkList(cutedPath, core.node.getDarknetConnections().length > 0));
		HTMLNode addDefaultBookmarksForm = ctx.addFormChild(response.content, "", "AddDefaultBookmarks");
		addDefaultBookmarksForm.addInput(Input.Type.SUBMIT, "AddDefaultBookmarks",
				NodeL10n.getBase().getString("BookmarkEditorToadlet.addDefaultBookmarks"));
		this.writeHTMLReply(ctx, 200, "OK", response.generate());
	}

	@Override
	public String path() {
		return "/bookmarkEditor/";
	}
}
