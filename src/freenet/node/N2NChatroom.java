package freenet.node;

import freenet.crypt.Yarrow;
import freenet.support.Logger;
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
	private String log;
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
		//Ex: Wednesday, June 1, 2011 (followed by newline)
		dayChangeFormat = new SimpleDateFormat("EEEE, MMMM dd, yyyy\n");
		//Ex: [ 04:48 PM ]
		timestampFormat = new SimpleDateFormat("[ hh:mm a z ]");
		//Start out the chat by setting the day.
		log = dayChangeFormat.format(lastMessageReceived.getTime());
	}

	/**
	 * Attempts to add a participant to the chatroom so that their messages can be received by it.
	 * @param participant The participant to add to the chatroom.
	 * @return True if participant was added; false if the participant was already participating.
	 */
	//TODO: Handle adding nodes to chat that are not directly connected.
	public boolean addParticipant(DarknetPeerNode participant) {
		int hash = participant.getIdentityHash();
		if (participants.containsKey(hash)) {
			return false;
		}
		participants.put(hash, participant.getName());
		//TODO: Add L10n for "{name} joined."
		log += participant.getName()+" joined.\n";
		return true;
	}

	//TODO: Log persistance.
	public String getLog() {
		return log;
	}

	public long getGlobalIdentifier() {
		return globalIdentifier;
	}

	/**
	 * Attempts to add a message to the chatroom log.
	 * @param composedBy The identity hash of the composer of the message.
	 * @param message The message to add.
	 * @return True if the message was added; false if the composer was not a member of this chatroom.
	 */
	//TODO: Mention who this message was routed by if not directly connected.
	public boolean addMessage(int composedBy, /*Date composedTime,*/ String message) {

		//Check that the composer of the message is present in this chatroom.
		if (!participants.containsKey(composedBy)) {
			//TODO: Add L10n for this error message.
			Logger.warning(this, "Received \""+message+"\" composed by identity hash "+composedBy+", who was not present!");
			return false;
		}

		//Check if the day changed, and if so, list the current date.
		Calendar now = Calendar.getInstance();
		now.setTime(new Date());
		if (now.get(Calendar.DAY_OF_YEAR) != lastMessageReceived.get(Calendar.DAY_OF_YEAR) ||
		        now.get(Calendar.YEAR) != lastMessageReceived.get(Calendar.YEAR)) {
			log += dayChangeFormat.format(now.getTime());
		}

		//Ex: [ 04:38 PM ] Billybob: Blah blah blah.
		log += timestampFormat.format(now.getTime())+' '+participants.get(composedBy)+": "+message+'\n';

		return true;
	}
}
