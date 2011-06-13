package freenet.clients.http.updateableelements;

import freenet.clients.http.ToadletContext;

//TODO: Web pushing for available chat rooms, invites thereto, participants pane, messages pane, and pending sent invites.
//TODO: Perhaps look up how Sone does it?
public class ChatElement extends BaseUpdateableElement {

	public ChatElement(String name, ToadletContext ctx) {
		super(name, ctx);
	}

	@Override
	public void dispose() {
		//TODO: Remove listener.
	}

	@Override
	public String getUpdaterId(String requestID) {
		//TODO: What is the identifer?
		return null;
	}

	@Override
	public String getUpdaterType() {
		return UpdaterConstants.REPLACER_UPDATER;
	}

	@Override
	public void updateState(boolean initial) {
		//TODO: Grab HTMLNode from chatroom.
		//TODO: Will there need to be multiple elements for each thing, or can there be a multipurpose one?
		//TODO: 
	}
}
