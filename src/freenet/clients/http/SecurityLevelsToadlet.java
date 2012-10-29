/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.constants.*;
import freenet.clients.http.uielements.*;
import freenet.clients.http.wizardsteps.PageHelper;
import freenet.clients.http.wizardsteps.WizardL10n;
import freenet.l10n.NodeL10n;
import freenet.node.*;
import freenet.node.Node.AlreadySetPasswordException;
import freenet.node.SecurityLevels.NETWORK_THREAT_LEVEL;
import freenet.node.SecurityLevels.PHYSICAL_THREAT_LEVEL;
import freenet.support.HTMLNode;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;
import freenet.support.io.FileUtil;
import freenet.support.io.FileUtil.OperatingSystem;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The security levels page.
 * @author Matthew Toseland <toad@amphibian.dyndns.org> (0xE43DA450)
 *
 */
public class SecurityLevelsToadlet extends Toadlet {

	public static final int MAX_PASSWORD_LENGTH = 1024;
	private final NodeClientCore core;
	private final Node node;

        private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	SecurityLevelsToadlet(HighLevelSimpleClient client, Node node, NodeClientCore core) {
		super(client);
		this.core = core;
		this.node = node;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"), NodeL10n.getBase()
			        .getString("Toadlet.unauthorized"));
			return;
		}

		String formPassword = request.getPartAsStringFailsafe("formPassword", 32);
		if((formPassword == null) || !formPassword.equals(core.formPassword)) {
			MultiValueTable<String,String> headers = new MultiValueTable<String,String>();
			headers.put("Location", Path.SECLEVELS.url);
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}
		if (request.isPartSet("seclevels")) {
			// Handle the security level changes.
			Page page = null;
			OutputList secLevelList = null;
			HTMLNode formNode = null;
			boolean changedAnything = false;
			String configName = "security-levels.networkThreatLevel";
			String confirm = "security-levels.networkThreatLevel.confirm";
			String tryConfirm = "security-levels.networkThreatLevel.tryConfirm";
			String networkThreatLevel = request.getPartAsStringFailsafe(configName, 128);
			NETWORK_THREAT_LEVEL newThreatLevel =
				SecurityLevels.parseNetworkThreatLevel(networkThreatLevel);
			if (newThreatLevel != null) {
				if (newThreatLevel != node.securityLevels.getNetworkThreatLevel()) {
					if (! request.isPartSet(confirm) && ! request.isPartSet(tryConfirm)) {
						HTMLNode warning =
							node.securityLevels.getConfirmWarning(newThreatLevel,
								confirm);
						if (warning != null) {
							page = ctx.getPageMaker().getPage(
								NodeL10n.getBase().getString("ConfigToadlet" +
									".fullTitle"),
								ctx);
							formNode = ctx.addFormChild(page.content, ".",
								"configFormSecLevels");
							secLevelList = new OutputList(Category.CONFIG);
							formNode.addChild(secLevelList);
							HTMLNode seclevelGroup = secLevelList.addItem();
							seclevelGroup.addInput(InputType.HIDDEN, configName,
									networkThreatLevel);
							Infobox networkthreatlevelconfirm =
								new Infobox(InfoboxType.INFORMATION,
									l10nSec("networkThreatLevelConfirmTitle",
										"mode", SecurityLevels
										.localisedName(newThreatLevel)));
							seclevelGroup.addChild(networkthreatlevelconfirm);
							networkthreatlevelconfirm.body.addChild(warning);
							networkthreatlevelconfirm.body.addInput(InputType.HIDDEN, tryConfirm, "on");
						} else {
							// Apply immediately, no confirm needed.
							node.securityLevels.setThreatLevel(newThreatLevel);
							changedAnything = true;
						}
					} else if (request.isPartSet(confirm)) {
						// Apply immediately, user confirmed it.
						node.securityLevels.setThreatLevel(newThreatLevel);
						changedAnything = true;
					}
				}
			}
			configName = "security-levels.physicalThreatLevel";
			confirm = "security-levels.physicalThreatLevel.confirm";
			tryConfirm = "security-levels.physicalThreatLevel.tryConfirm";
			String physicalThreatLevel = request.getPartAsStringFailsafe(configName, 128);
			PHYSICAL_THREAT_LEVEL newPhysicalLevel =
				SecurityLevels.parsePhysicalThreatLevel(physicalThreatLevel);
			PHYSICAL_THREAT_LEVEL oldPhysicalLevel = core.node.securityLevels.getPhysicalThreatLevel();
			if (logMINOR) {
				Logger.minor(this, "New physical threat level: " + newPhysicalLevel + " old = " +
					node.securityLevels.getPhysicalThreatLevel());
			}
			if (newPhysicalLevel != null) {
				if (newPhysicalLevel == oldPhysicalLevel &&
					newPhysicalLevel == PHYSICAL_THREAT_LEVEL.HIGH) {
					String password =
						request.getPartAsStringFailsafe("masterPassword",
							MAX_PASSWORD_LENGTH);
					String oldPassword =
						request.getPartAsStringFailsafe("oldPassword", MAX_PASSWORD_LENGTH);
					if (password != null && oldPassword != null && password.length() > 0 &&
						oldPassword.length() > 0) {
						try {
							core.node.changeMasterPassword(oldPassword, password, false);
						} catch (MasterKeysWrongPasswordException e) {
							sendChangePasswordForm(ctx, true, false,
								newPhysicalLevel.name());
							return;
						} catch (MasterKeysFileSizeException e) {
							sendPasswordFileCorruptedPage(e.isTooBig(), ctx, false, true);
							if (changedAnything) {
								core.storeConfig();
							}
							return;
						} catch (AlreadySetPasswordException e) {
							sendChangePasswordForm(ctx, false, true,
								newPhysicalLevel.name());
							if (changedAnything) {
								core.storeConfig();
							}
							return;
						}
					} else if (password != null || oldPassword != null) {
						sendChangePasswordForm(ctx, false, true, newPhysicalLevel.name());
						if (changedAnything) {
							core.storeConfig();
						}
						return;
					}
				}
				if (newPhysicalLevel != oldPhysicalLevel) {
					// No confirmation for changes to physical threat level.
					if (newPhysicalLevel == PHYSICAL_THREAT_LEVEL.HIGH &&
						node.securityLevels.getPhysicalThreatLevel() != newPhysicalLevel) {
						// Check for password
						String password = request.getPartAsStringFailsafe("masterPassword",
							MAX_PASSWORD_LENGTH);
						if (password != null && password.length() > 0) {
							try {
								if (oldPhysicalLevel == PHYSICAL_THREAT_LEVEL
									.NORMAL ||
									oldPhysicalLevel == PHYSICAL_THREAT_LEVEL
										.LOW) {
									core.node.changeMasterPassword("", password,
										false);
								} else {
									core.node.setMasterPassword(password, false);
								}
							} catch (AlreadySetPasswordException e) {
								sendChangePasswordForm(ctx, false, false,
									newPhysicalLevel.name());
								return;
							} catch (MasterKeysWrongPasswordException e) {
								System.err.println("Wrong password!");
								page = ctx.getPageMaker()
									.getPage(l10nSec("passwordPageTitle"),
										ctx);
								Infobox wrongPassword = new Infobox(
									InfoboxType.ERROR,
									Identifier.WRONGPASSWORD,
									l10nSec("passwordWrongTitle"));
								page.content.addInfobox(wrongPassword);
								SecurityLevelsToadlet.generatePasswordFormPage(true,
									ctx.getContainer(), wrongPassword.body, false,
									false, true, newPhysicalLevel.name(), null);
								addBackToSeclevelsLink(wrongPassword.body);
								writeHTMLReply(ctx, 200, "OK", page.generate());
								if (changedAnything) {
									core.storeConfig();
								}
								return;
							} catch (MasterKeysFileSizeException e) {
								sendPasswordFileCorruptedPage(e.isTooBig(), ctx,
									false,
									true);
								if (changedAnything) {
									core.storeConfig();
								}
								return;
							}
						} else {
							sendPasswordPage(ctx,
								password != null && password.length() == 0,
								newPhysicalLevel.name());
							if (changedAnything) {
								core.storeConfig();
							}
							return;
						}
					}
					if ((newPhysicalLevel == PHYSICAL_THREAT_LEVEL.LOW ||
						newPhysicalLevel == PHYSICAL_THREAT_LEVEL.NORMAL) &&
						oldPhysicalLevel == PHYSICAL_THREAT_LEVEL.HIGH) {
						// Check for password
						String password = request.getPartAsStringFailsafe("masterPassword",
							SecurityLevelsToadlet.MAX_PASSWORD_LENGTH);
						if (password != null && password.length() > 0) {
							// This is actually the OLD password ...
							try {
								core.node.changeMasterPassword(password, "", false);
							} catch (IOException e) {
								if (! core.node.getMasterPasswordFile().exists()) {
									// Ok.
									System.out.println(
										"Master password file no longer " +
											"exists, assuming this is " +
											"deliberate");
								} else {
									System.err.println(
										"Cannot change password as cannot " +
											"write new passwords file: " +
											e);
									e.printStackTrace();
									String msg = "<html><head><title>" +
										l10nSec("cantWriteNewMasterKeysFileTitle") +
										"</title></head><body><h1>" +
										l10nSec("cantWriteNewMasterKeysFileTitle") +
										"</h1><p>" +
										l10nSec("cantWriteNewMasterKeysFile") +
										"<pre>";
									StringWriter sw = new StringWriter();
									PrintWriter pw = new PrintWriter(sw);
									e.printStackTrace(pw);
									pw.flush();
									msg = msg + sw.toString() +
										"</pre></body></html>";
									writeHTMLReply(ctx, 500, "Internal Error", msg);
									if (changedAnything) {
										core.storeConfig();
									}
									return;
								}
							} catch (MasterKeysWrongPasswordException e) {
								System.err.println("Wrong password!");
								page = ctx.getPageMaker()
									.getPage(l10nSec
										("passwordForDecryptTitle"),
										ctx);
								Infobox wrongPassword = new Infobox(
									InfoboxType.ERROR,
									Identifier.WRONGPASSWORD,
									("passwordWrongTitle"));
								page.content.addInfobox(wrongPassword);
								SecurityLevelsToadlet.generatePasswordFormPage(true,
									ctx.getContainer(), wrongPassword.body, false,
									true, false, newPhysicalLevel.name(), null);
								addBackToSeclevelsLink(wrongPassword.body);
								writeHTMLReply(ctx, 200, "OK", page.generate());
								if (changedAnything) {
									core.storeConfig();
								}
								return;
							} catch (MasterKeysFileSizeException e) {
								sendPasswordFileCorruptedPage(e.isTooBig(), ctx, false,
									true);
								if (changedAnything) {
									core.storeConfig();
								}
								return;
							} catch (AlreadySetPasswordException e) {
								sendChangePasswordForm(ctx, false, true,
									newPhysicalLevel.name());
								if (changedAnything) {
									core.storeConfig();
								}
								return;
							}
						} else if (core.node.getMasterPasswordFile().exists()) {
							// We need the old password
							page = ctx.getPageMaker()
								.getPage(l10nSec("passwordForDecryptTitle"), ctx);
							Infobox passwordPrompt = new Infobox(
								InfoboxType.ERROR, Category.PASSWORDPROMPT,
								l10nSec("passwordForDecryptTitle"));
							page.content.addInfobox(passwordPrompt);
							if (password != null && password.length() == 0) {
								passwordPrompt.body.addText(
									l10nSec("passwordNotZeroLength"));
							}
							SecurityLevelsToadlet
								.generatePasswordFormPage(false, ctx.getContainer(),
									passwordPrompt.body, false, true, false,
									newPhysicalLevel.name(), null);
							addBackToSeclevelsLink(passwordPrompt.body);
							writeHTMLReply(ctx, 200, "OK", page.generate());
							if (changedAnything) {
								core.storeConfig();
							}
							return;
						}
					}
					if (newPhysicalLevel == PHYSICAL_THREAT_LEVEL.MAXIMUM) {
						try {
							core.node.killMasterKeysFile();
						} catch (IOException e) {
							sendCantDeleteMasterKeysFile(ctx, newPhysicalLevel.name());
							return;
						}
					}
					node.securityLevels.setThreatLevel(newPhysicalLevel);
					changedAnything = true;
				}
			}
			if (changedAnything) {
				core.storeConfig();
			}
			if (page != null) {
				formNode.addInput(InputType.HIDDEN, "seclevels", "on");
				formNode.addInput(InputType.SUBMIT, l10n("apply"));
				formNode.addInput(InputType.RESET, l10n("undo"));
				writeHTMLReply(ctx, 200, "OK", page.generate());
				return;
			} else {
				MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
				headers.put("Location", Path.SECLEVELS.url);
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}
		} else {
			if(request.isPartSet("masterPassword")) {
				String masterPassword = request.getPartAsStringFailsafe("masterPassword", 1024);
				if(masterPassword.length() == 0) {
					sendPasswordPage(ctx, true, null);
					return;
				}
				System.err.println("Setting master password");
				try {
					node.setMasterPassword(masterPassword, false);
				} catch (AlreadySetPasswordException e) {
					System.err.println("Already set master password");
					Logger.error(this, "Already set master password");
					MultiValueTable<String,String> headers = new MultiValueTable<String,String>();
					headers.put("Location", "/");
					ctx.sendReplyHeaders(302, "Found", headers, null, 0);
					return;
				} catch (MasterKeysWrongPasswordException e) {
					sendPasswordFormPage(true, ctx);
					return;
				} catch (MasterKeysFileSizeException e) {
					sendPasswordFileCorruptedPage(e.isTooBig(), ctx, false, false);
					return;
				}
				MultiValueTable<String,String> headers = new MultiValueTable<String,String>();
				if(request.isPartSet("redirect")) {
					String to = request.getPartAsStringFailsafe("redirect", 100);
					if(to.startsWith("/")) {
						headers.put("Location", to);
						ctx.sendReplyHeaders(302, "Found", headers, null, 0);
						return;
					}
				}
				headers.put("Location", "/");
				ctx.sendReplyHeaders(302, "Found", headers, null, 0);
				return;
			}

			try {
				throw new RedirectException(Path.SECLEVELS.url);
			} catch (URISyntaxException e) {
				// Impossible
			}
		}

		MultiValueTable<String,String> headers = new MultiValueTable<String,String>();
		headers.put("Location", "/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	private void sendCantDeleteMasterKeysFile(ToadletContext ctx, String physicalSecurityLevel) throws ToadletContextClosedException, IOException {
		HTMLNode pageNode = sendCantDeleteMasterKeysFileInner(ctx, node.getMasterPasswordFile().getPath(), false, physicalSecurityLevel, this.node);
		writeHTMLReply(ctx, 200, "OK", pageNode.generate());
	}

	public static void sendCantDeleteMasterKeysFileInner(PageHelper helper, String filename, String physicalSecurityLevel) {
		Infobox content = helper.getPageContent(l10nSec("cantDeletePasswordFileTitle")).addInfobox(
			InfoboxType.ERROR, Identifier.PASSWORDERROR,
			l10nSec("cantDeletePasswordFileTitle"));
		HTMLNode form = helper.addFormChild(content.body, "/wizard/", "masterPasswordForm");
		sendCantDeleteMasterKeysFileInner(content.body, form, filename, physicalSecurityLevel);
	}

	public static HTMLNode sendCantDeleteMasterKeysFileInner(ToadletContext ctx, String filename,
	                                                         boolean forFirstTimeWizard,
	                                                         String physicalSecurityLevel, Node node) {
		Page page = ctx.getPageMaker().getPage(l10nSec("cantDeletePasswordFileTitle"), ctx);
		Infobox passwordError = new Infobox(InfoboxType.ERROR, Identifier.PASSWORDERROR,
			l10nSec("cantDeletePasswordFileTitle"));
		page.content.addInfobox(passwordError);
		HTMLNode form =
			forFirstTimeWizard ? ctx.addFormChild(passwordError.body, "/wizard/", "masterPasswordForm") :
				ctx.addFormChild(passwordError.body, Path.SECLEVELS.url, "masterPasswordForm");
		sendCantDeleteMasterKeysFileInner(passwordError.body, form, filename, physicalSecurityLevel);
		return page;
	}

	private static void sendCantDeleteMasterKeysFileInner(Box content, HTMLNode form, String filename, String physicalSecurityLevel) {
		form.addInput(InputType.HIDDEN, "security-levels.physicalThreatLevel", physicalSecurityLevel);
		form.addInput(InputType.HIDDEN, "seclevels", "true");
		form.addInput(InputType.SUBMIT, "tryAgain", l10nSec("cantDeletePasswordFileButton"));
		content.addBlockText(l10nSec("cantDeletePasswordFile", "filename", filename));
	}

	/**
	 * Send a form asking the user to change the password.
	 *
	 * @throws IOException
	 * @throws ToadletContextClosedException
	 */
	private void sendChangePasswordForm(ToadletContext ctx, boolean wrongPassword, boolean emptyPassword,
	                                    String physicalSecurityLevel)
		throws ToadletContextClosedException, IOException {
		// Must set a password!
		Page page = ctx.getPageMaker().getPage(l10nSec("changePasswordTitle"), ctx);
		Infobox passwordChange = page.content.addInfobox(InfoboxType.ERROR,
			Identifier.PASSWORDCHANGE,
			l10nSec("changePasswordTitle"));
		if (emptyPassword) {
			passwordChange.body.addBlockText(l10nSec("passwordNotZeroLength"));
		}
		if (wrongPassword) {
			passwordChange.body.addBlockText(l10nSec("wrongOldPassword"));
		}
		HTMLNode form = ctx.addFormChild(passwordChange.body, path(), "changePasswordForm");
		addPasswordChangeForm(form);
		if (physicalSecurityLevel != null) {
			form.addInput(InputType.HIDDEN, "security-levels.physicalThreatLevel", physicalSecurityLevel);
			form.addInput(InputType.HIDDEN, "seclevels", "true");
		}
		addBackToSeclevelsLink(passwordChange.body);
		writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	private void sendPasswordPage(ToadletContext ctx, boolean emptyPassword, String threatlevel)
		throws ToadletContextClosedException, IOException {
		// Must set a password!
		Page page = ctx.getPageMaker().getPage(l10nSec("setPasswordTitle"), ctx);
		Infobox passwordPrompt = page.content.addInfobox(InfoboxType.ERROR, Identifier.PASSWORDERROR,
				l10nSec("setPasswordTitle"));
		if (emptyPassword) {
			passwordPrompt.body.addBlockText(l10nSec("passwordNotZeroLength"));
		}
		SecurityLevelsToadlet
			.generatePasswordFormPage(false, ctx.getContainer(), passwordPrompt.body, false, false, true,
					threatlevel, null);
		addBackToSeclevelsLink(passwordPrompt.body);
		writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	private static void addBackToSeclevelsLink(HTMLNode content) {
		content.addBlockText().addLink(PATH, l10nSec("backToSecurityLevels"));
	}

	public void handleMethodGET(URI uri, HTTPRequest req, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		if (! ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, NodeL10n.getBase().getString("Toadlet.unauthorizedTitle"),
				NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}
		Page page = ctx.getPageMaker()
			.getPage(NodeL10n.getBase().getString("SecurityLevelsToadlet.fullTitle"), ctx);
		page.content.addChild(core.alerts.createSummary());
		drawSecurityLevelsPage(page.content, ctx);
		this.writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	private void drawSecurityLevelsPage(HTMLNode contentNode, ToadletContext ctx) {
		Infobox configformcontainer = new Infobox(InfoboxType.NORMAL, l10nSec("title"));
		contentNode.addChild(configformcontainer);
		HTMLNode formNode = ctx.addFormChild(configformcontainer.body, ".", "configFormSecLevels");
		// Network security level
		formNode.addBox(Category.CONFIGPREFIX, l10nSec("networkThreatLevelShort"));
		OutputList secLevelList = new OutputList(Category.CONFIG);
		formNode.addChild(secLevelList);
		Item seclevelGroup = secLevelList.addItem();
		seclevelGroup.addText(l10nSec("networkThreatLevel.opennetIntro"));

		NETWORK_THREAT_LEVEL networkLevel = node.securityLevels.getNetworkThreatLevel();

		HTMLNode p = seclevelGroup.addBlockText();
		p.addInlineBox(Category.BOLD, l10nSec("networkThreatLevel.opennetLabel"));
		p.addText(": " + l10nSec("networkThreatLevel.opennetExplain"));
		HTMLNode div = seclevelGroup.addBox(Category.OPENNETDIV);
		
		String controlName = "security-levels.networkThreatLevel";
		for(NETWORK_THREAT_LEVEL level : NETWORK_THREAT_LEVEL.OPENNET_VALUES) {
			HTMLNode input;
			if(level == networkLevel) {
				input = div.addBlockText().addInput(InputType.RADIO, controlName, level.name(), true);
			} else {
				input = div.addBlockText().addInput(InputType.RADIO, controlName, level.name());
			}
			input.addInlineBox(Category.BOLD, l10nSec("networkThreatLevel.name." + level));
			input.addText(": ");
			NodeL10n.getBase().addL10nSubstitution(input, "SecurityLevels.networkThreatLevel.choice."+level, new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
			HTMLNode inner = input.addBlockText(Category.ITALIC);

			NodeL10n.getBase().addL10nSubstitution(inner, "SecurityLevels.networkThreatLevel.desc."+level, new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
		}

		p = seclevelGroup.addBlockText();
		p.addInlineBox(Category.BOLD, l10nSec("networkThreatLevel.darknetLabel"));
		p.addText(": " + l10nSec("networkThreatLevel.darknetExplain"));
		div = seclevelGroup.addBox(Category.DARKNETDIV);
		
		for(NETWORK_THREAT_LEVEL level : NETWORK_THREAT_LEVEL.DARKNET_VALUES) {
			HTMLNode input;
			if(level == networkLevel) {
				input = div.addInput(InputType.RADIO, controlName, level.name(), true);
			} else {
				input = div.addInput(InputType.RADIO, controlName, level.name());
			}
			input.addInlineBox(Category.BOLD, l10nSec("networkThreatLevel.name." + level));
			input.addText(": ");
			NodeL10n.getBase().addL10nSubstitution(input, "SecurityLevels.networkThreatLevel.choice."+level, new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
			HTMLNode inner = input.addBlockText(Category.ITALIC);
			NodeL10n.getBase().addL10nSubstitution(inner, "SecurityLevels.networkThreatLevel.desc."+level, new String[] { "bold", "link" },
					new HTMLNode[] { HTMLNode.STRONG, new Link("/wizard/?step=OPENNET", Target.BLANK) });
		}
		seclevelGroup.addBlockText(Category.BOLD, l10nSec("networkThreatLevel.opennetFriendsWarning"));

		// Physical security level
		formNode.addBox(Category.CONFIGPREFIX, l10nSec("physicalThreatLevelShort"));
		secLevelList = new OutputList(Category.CONFIG);
		formNode.addChild(secLevelList);
		seclevelGroup = secLevelList.addItem();
		seclevelGroup.addText(l10nSec("physicalThreatLevel"));
		
		NodeL10n.getBase().addL10nSubstitution(seclevelGroup.addBlockText(Category.ITALIC), "SecurityLevels.physicalThreatLevelTruecrypt",
		        new String[]{"bold", "truecrypt"},
		        new HTMLNode[]{HTMLNode.STRONG,
		                new Link(ExternalLinkToadlet.escape("http://www.truecrypt.org/"), Target.BLANK)});
		OutputNode swapWarning = seclevelGroup.addBlockText(Category.ITALIC);
		OperatingSystem os = FileUtil.detectedOS;
		swapWarning.addText(NodeL10n.getBase().getString("SecurityLevels.physicalThreatLevelSwapfile",
				"operatingSystem",
				NodeL10n.getBase().getString("OperatingSystemName." + os.name())));
		if(os == FileUtil.OperatingSystem.Windows) {
			swapWarning.addText(" " + WizardL10n.l10nSec("physicalThreatLevelSwapfileWindows"));
		}

		PHYSICAL_THREAT_LEVEL physicalLevel = node.securityLevels.getPhysicalThreatLevel();

		controlName = "security-levels.physicalThreatLevel";
		for (PHYSICAL_THREAT_LEVEL level : PHYSICAL_THREAT_LEVEL.values()) {
			HTMLNode input;
			if(level == physicalLevel) {
				input = seclevelGroup.addBlockText().addInput(InputType.RADIO, controlName, level.name(), true);
			} else {
				input = seclevelGroup.addBlockText().addInput(InputType.RADIO, controlName, level.name());
			}
			input.addInlineBox(Category.BOLD, l10nSec("physicalThreatLevel.name." + level));
			input.addText(": ");
			NodeL10n.getBase().addL10nSubstitution(input, "SecurityLevels.physicalThreatLevel.choice."+level, new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
			HTMLNode inner = input.addBlockText(Category.ITALIC);
			NodeL10n.getBase().addL10nSubstitution(inner, "SecurityLevels.physicalThreatLevel.desc."+level, new String[] { "bold" },
					new HTMLNode[] { HTMLNode.STRONG });
			if(level != PHYSICAL_THREAT_LEVEL.LOW && physicalLevel == PHYSICAL_THREAT_LEVEL.LOW && node.hasDatabase() && !node.isDatabaseEncrypted()) {
				if(node.autoChangeDatabaseEncryption())
					inner.addInlineBox(Category.BOLD, " " + l10nSec("warningWillEncrypt"));
				else
					inner.addInlineBox(Category.BOLD, " " + l10nSec("warningWontEncrypt"));
			} else if(level == PHYSICAL_THREAT_LEVEL.LOW && physicalLevel != PHYSICAL_THREAT_LEVEL.LOW && node.hasDatabase() && node.isDatabaseEncrypted()) {
				if(node.autoChangeDatabaseEncryption())
					inner.addInlineBox(Category.BOLD, " " + l10nSec("warningWillDecrypt"));
				else
					inner.addInlineBox(Category.BOLD, " " + l10nSec("warningWontDecrypt"));
			}
			if(level == PHYSICAL_THREAT_LEVEL.MAXIMUM && node.hasDatabase()) {
				inner.addInlineBox(Category.BOLD, " " + l10nSec("warningMaximumWillDeleteQueue"));
			}
			if(level == PHYSICAL_THREAT_LEVEL.HIGH) {
				if(physicalLevel == level) {
					addPasswordChangeForm(inner);
				} else {
					// Add password form
					p = inner.addBlockText();
					p.addChild("label", "for", "passwordBox", l10nSec("setPassword"));
					p.addInput(InputType.PASSWORD, "masterPassword", Identifier.PASSWORDBOX);
				}
			}
		}

		// FIXME implement the rest, it should be very similar to the above.

		formNode.addInput(InputType.HIDDEN, "seclevels", "on");
		formNode.addInput(InputType.SUBMIT, l10n("apply"));
		formNode.addInput(InputType.RESET,  l10n("undo"));
	}

	private void addPasswordChangeForm(HTMLNode inner) {
		Table table = new Table();
		inner.addChild(table);
		Row row = table.addRow();
		Cell cell = row.addCell();
		cell.addChild("label", "for", "oldPasswordBox", l10nSec("oldPasswordLabel"));
		cell = row.addCell();
		cell.addInput(InputType.PASSWORD, "oldPassword", 100, Identifier.PASSWORDBOXOLD);
		row = table.addRow();
		cell = row.addCell();
		cell.addChild("label", "for", "newPasswordBox", l10nSec("newPasswordLabel"));
		cell = row.addCell();
		cell.addInput(InputType.PASSWORD, "masterPassword", 100, Identifier.PASSWORDBOX);
		HTMLNode p = inner.addBlockText();
		p.addInput(InputType.SUBMIT, "changePassword", l10nSec("changePasswordButton"));
	}

	static final String PATH = Path.SECLEVELS.url;

	@Override
	public String path() {
		return PATH;
	}

	private static String l10n(String string) {
		return NodeL10n.getBase().getString("ConfigToadlet." + string);
	}

	private static String l10nSec(String key) {
		return NodeL10n.getBase().getString("SecurityLevels."+key);
	}

	private static String l10nSec(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("SecurityLevels."+key, pattern, value);
	}

	void sendPasswordFileCorruptedPage(boolean tooBig, ToadletContext ctx, boolean forSecLevels, boolean forFirstTimeWizard) throws ToadletContextClosedException, IOException {
		HTMLNode page = sendPasswordFileCorruptedPageInner(tooBig, ctx, forSecLevels, forFirstTimeWizard, node.getMasterPasswordFile().getPath(), node);
		writeHTMLReply(ctx, 500, "Internal Server Error", page.generate());
	}

	public static void sendPasswordFileCorruptedPageInner(PageHelper helper, String masterPasswordFile) {
		Infobox infoBox = helper.getPageContent(l10nSec("passwordFileCorruptedTitle")).addInfobox(
			InfoboxType.ERROR, Identifier.PASSWORDERROR,
			l10nSec("passwordFileCorruptedTitle"));
		sendPasswordFileCorruptedPageInner(infoBox.body, masterPasswordFile);
	}

	public static HTMLNode sendPasswordFileCorruptedPageInner(boolean tooBig, ToadletContext ctx,
	                                                          boolean forSecLevels, boolean forFirstTimeWizard,
	                                                          String masterPasswordFile, Node node) {
		Page page = ctx.getPageMaker().getPage(l10nSec("passwordFileCorruptedTitle"), ctx);
		Infobox passwordCorrupted = page.content.addInfobox(InfoboxType.ERROR,
			Identifier.PASSWORDERROR,
			l10nSec("passwordFileCorruptedTitle"));
		sendPasswordFileCorruptedPageInner(passwordCorrupted.body, masterPasswordFile);
		return page;
	}

	/** Send a page asking what to do when the master password file has been corrupted.
	 * @param content containing more information. Will be added to.
	 * @param masterPasswordFile path to master password file */
	private static void sendPasswordFileCorruptedPageInner(Box content, String masterPasswordFile) {
		content.addBlockText(l10nSec("passwordFileCorrupted", "file", masterPasswordFile));
		addHomepageLink(content);
		addBackToSeclevelsLink(content);
	}

	/**
	 * Send a page asking for the master password.
	 *
	 * @param wasWrong If true, we want the master password because the user entered the wrong
	 *                 password.
	 * @param ctx
	 * @throws IOException
	 * @throws ToadletContextClosedException
	 */
	private void sendPasswordFormPage(boolean wasWrong, ToadletContext ctx)
		throws ToadletContextClosedException, IOException {
		Page page = ctx.getPageMaker().getPage(l10nSec("passwordPageTitle"), ctx);
		Infobox passwordForm = page.content.addInfobox(InfoboxType.ERROR,
			Identifier.PASSWORDERROR,
			wasWrong ? l10nSec("passwordWrongTitle") : l10nSec("enterPasswordTitle"));
		generatePasswordFormPage(wasWrong, ctx.getContainer(), passwordForm.body, false, false, false, null,
			null);
		addHomepageLink(passwordForm.body);
		writeHTMLReply(ctx, 200, "OK", page.generate());
	}

	/**
	 * @param forFirstTimeWizard used to determine form target: wizard if in the wizard, this toadlet if not.
	 */
	public static void generatePasswordFormPage(boolean wasWrong, ToadletContainer ctx, HTMLNode content, boolean forFirstTimeWizard, boolean forDowngrade, boolean forUpgrade, String physicalSecurityLevel, String redirect) {

		String postTo = forFirstTimeWizard ? FirstTimeWizardToadlet.TOADLET_URL : SecurityLevelsToadlet.PATH;
		HTMLNode form = ctx.addFormChild(content, postTo, "masterPasswordForm");
		generatePasswordFormPage(wasWrong, form, content, forDowngrade, forUpgrade, physicalSecurityLevel, redirect);
	}

	public static void generatePasswordFormPage(boolean wasWrong, HTMLNode formNode, HTMLNode content, boolean forDowngrade, boolean forUpgrade, String physicalSecurityLevel, String redirect) {
		if (forDowngrade && !wasWrong) {
			content.addText(l10nSec("passwordForDecrypt"));
		} else if (wasWrong) {
			content.addText(l10nSec("passwordWrong"));
		} else if (forUpgrade) {
			content.addText(l10nSec("setPassword"));
		} else {
			content.addText(l10nSec("enterPassword"));
		}

		formNode.addInput(InputType.PASSWORD, "masterPassword", 100);

		if(physicalSecurityLevel != null) {
			formNode.addInput(InputType.HIDDEN, "security-levels.physicalThreatLevel", physicalSecurityLevel);
			formNode.addInput(InputType.HIDDEN, "seclevels", "true");
		}
		if(redirect != null) {
			formNode.addInput(InputType.HIDDEN, "redirect", redirect);
		}
		formNode.addInput(InputType.SUBMIT, l10nSec("passwordSubmit"));
	}
}
