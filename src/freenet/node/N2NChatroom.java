package freenet.node;

import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import java.lang.String;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Date;

/**
 * The N2NChatroom class keeps track of what has been said in a chatroom, parses new messages, formats them, and is
 * responsible for system messages such as joins, leaves, and day changes.
 */
public class N2NChatroom {

	private Calendar lastMessageReceived;
	//The key is the identity hash, the value is the name of the node.
	private HashMap<Integer, String> participants;
	private HTMLNode log;
	private String localName;
	private long globalIdentifier;
	private SimpleDateFormat dayChangeFormat;
	private SimpleDateFormat timestampFormat;
	//TODO: When chatting with people who are not directly connected, there will need to be a way to set a
	//TODO: nickname for them, have them request a nickname for themselves, or have the node that invited them
	//TODO: suggest a nickname to those already participating.

	/**
	 * Initializes date formatters and starts the room off with a timestamp of the day.
	 * @param localName Name of the chatroom. Only applies locally.
	 * @param globalIdentifier Global ID; used by all participants to specify what room a message is for.
	 */
	public N2NChatroom(String localName, long globalIdentifier){
		this.localName = localName;
		this.globalIdentifier = globalIdentifier;
		participants = new HashMap<Integer, String>();
		lastMessageReceived = Calendar.getInstance();
		lastMessageReceived.setTime(new Date());
		//TODO: Allow date display formatting configuration.
		//Ex: Wednesday, June 1, 2011 (followed by newline)
		dayChangeFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy");
		//Ex: 04:48 PM
		timestampFormat = new SimpleDateFormat("hh:mm a");
		//TODO: What size should this box be? Will the automatic size be reasonable?
		//TODO: Likely full width with limited height.
		log = new HTMLNode("div", "class", "overflow:scroll");
		//Start out the chat by setting the day.
		log.addChild("p", dayChangeFormat.format(lastMessageReceived.getTime()));
	}

	/**
	 * Adds a participant to this chatroom so that messages they send here can be received.
	 * @param participant The participant to add to the chatroom.
	 * @return True if participant was added; false if the participant was already participating.
	 */
	public boolean addParticipant(DarknetPeerNode participant) {
		return addParticipant(participant.getIdentityHash(), participant.getName());
	}

	/**
	 * Adds a participant to this chatroom so that messages they send here can be received.
	 * @param identityHash Identity hash of the participant to add.
	 * @param name Name of the participant to add.
	 * @return True if participant was added; false if the participant was already participating.
	 */
	public boolean addParticipant(int identityHash, String name) {
		if (participants.containsKey(identityHash)) {
			return false;
		}
		participants.put(identityHash, name);
		log.addChild("p", l10n("joined", "name", name));
		return true;
	}

	/**
	 * Removes a participant from the chatroom.
	 * @param identityHash Identity hash of the participant to remove.
	 * @return True if the participant was removed; false if the participant was not present.
	 */
	//TODO: Verify that the node sending a disconnect for an identity is the same as that sending their messages.
	public boolean removeParticipant(int identityHash) {
		if (!participants.containsKey(identityHash)) {
			Logger.warning(this, l10n("removedNonexistentParticipant",
			        new String[] { "identityHash", "localName", "globalIdentifier" },
			        new String[] { String.valueOf(identityHash), localName, String.valueOf(globalIdentifier) }));
			return false;
		}
		log.addChild(l10n("left", "name", participants.get(identityHash)));
		participants.remove(identityHash);
		return true;
	}

	//TODO: Log persistance.
	public HTMLNode getLog() {
		return log;
	}

	public long getGlobalIdentifier() {
		return globalIdentifier;
	}

	/**
	 * Attempts to add a message to the chatroom log.
	 * @param composedBy The identity hash of the composer of the message.
	 * @param timeComposed The time at which the message was composed.
	 * @param deliveredID The identity hash of the darknet peer that delivered the message.
	 * @param deliveredName The nickname of the darknet peer that delivered the message.
	 * @param message The message to add.
	 * @return True if the message was added; false if the message's composer is not in this chatroom.
	 */
	public boolean addMessage(int composedBy, Date timeComposed, int deliveredID, String deliveredName, String message) {

		//Check that the composer of the message is present in this chatroom.
		if (!participants.containsKey(composedBy)) {
			Logger.warning(this, l10n("nonexistentParticipant",
			        new String[] { "identityHash", "deliveredByName", "deliveredByID", "globalIdentifier", "localName", "message" },
			        new String[] { String.valueOf(composedBy), deliveredName, String.valueOf(deliveredID), String.valueOf(globalIdentifier), localName, message }));
			return false;
		}

		//List the current date if the day changed.
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		if (now.get(Calendar.DAY_OF_YEAR) != lastMessageReceived.get(Calendar.DAY_OF_YEAR) ||
		        now.get(Calendar.YEAR) != lastMessageReceived.get(Calendar.YEAR)) {
			log.addChild("p", dayChangeFormat.format(now.getTime()));
		}

		HTMLNode messageLine = log.addChild("p");

		//Ex: [ 04:38 PM ]
		//Ex: Tooltip of time composed.
		messageLine.addChild("timestamp", "title",
		        l10n("composed", "time", timestampFormat.format(timeComposed.getTime())),
		        "[ "+timestampFormat.format(now.getTime())+" ] ");

		//Ex: Billybob:
		//Ex: Tooltip of either who it wasdeliveredDirectly delivered by or "Delivered directly"
		if (deliveredID != composedBy) {
			messageLine.addChild("delivery", "title", l10n("deliveredBy", "deliveredByName", deliveredName), participants.get(composedBy)+": ");
		} else {
			messageLine.addChild("delivery", "title", l10n("deliveredDirectly"), participants.get(composedBy)+": ");
		}

		//Ex: Blah blah blah.
		messageLine.addChild("#", message);

		return true;
	}

	private String l10n(String key) {
		return NodeL10n.getBase().getString("N2NChatroom."+key);
	}

	private String l10n(String key, String pattern, String value) {
		return l10n(key, new String[] { pattern }, new String[] { value });
	}

	private String l10n(String key, String[] pattern, String[] value) {
		return NodeL10n.getBase().getString("N2NChatroom."+key, pattern, value);
	}
}
