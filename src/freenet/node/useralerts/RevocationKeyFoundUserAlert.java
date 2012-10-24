/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.useralerts;

import freenet.clients.http.uielements.Text;
import freenet.l10n.NodeL10n;
import freenet.support.HTMLNode;

public class RevocationKeyFoundUserAlert extends AbstractUserAlert {
	public RevocationKeyFoundUserAlert(String msg, boolean disabledNotBlown){
		super(false,
				getTitle(disabledNotBlown),
				getText(disabledNotBlown, msg),
				getText(disabledNotBlown, msg), 
				new Text(getText(disabledNotBlown, msg)),
				UserAlert.CRITICAL_ERROR, true, null, false, null);
	}
	
	private static String getText(boolean disabledNotBlown, String msg) {
		if(disabledNotBlown)
			return NodeL10n.getBase().getString("RevocationKeyFoundUserAlert.textDisabled", "message", msg);
		else
			return NodeL10n.getBase().getString("RevocationKeyFoundUserAlert.text", "message", msg);
	}

	private static String getTitle(boolean disabledNotBlown) {
		if(disabledNotBlown)
			return NodeL10n.getBase().getString("RevocationKeyFoundUserAlert.titleDisabled");
		else
			return NodeL10n.getBase().getString("RevocationKeyFoundUserAlert.title");
	}

	@Override
	public void isValid(boolean b){
		// We ignore it : it's ALWAYS valid !
	}
	
}
