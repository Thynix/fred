package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.InputType;
import freenet.l10n.NodeL10n;
import freenet.node.NodeClientCore;
import freenet.support.HTMLNode;

import java.io.File;
import java.util.Hashtable;

public class LocalDirectoryConfigToadlet extends LocalDirectoryToadlet {

	public LocalDirectoryConfigToadlet (NodeClientCore core, HighLevelSimpleClient highLevelSimpleClient,
	        String postTo) {
		super(core, highLevelSimpleClient, postTo);
	}

	@Override
	protected String startingDir() {
		//Start out in user home directory.
		return System.getProperty("user.home");
	}

	@Override
	protected boolean allowedDir(File path) {
		//When configuring, can select any directory.
		return true;
	}
	
	@Override
	protected void createSelectDirectoryButton (HTMLNode formNode, String path, HTMLNode persist) {
		formNode.addInput(InputType.SUBMIT, selectDir,
		                NodeL10n.getBase().getString("ConfigToadlet.selectDirectory"));
		formNode.addInput(InputType.HIDDEN, filenameField(), path);
		formNode.addChild(persist);
	}

	@Override
	protected  Hashtable<String, String> persistenceFields (Hashtable<String, String> set) {
		set.remove("path");
		set.remove("formPassword");
		return set;
	}
}
