package freenet.clients.http.constants;

public enum Site {
	FMS("USK@0npnMrqZNKRCRoGojZV93UNHCMN-6UU3rRSAmP6jNLE,~BG-edFtdCC1cSH4O3BWdeIYa8Sw5DfyrSV-TKdO5ec,AQACAAE/fms/127/",
		"",
		"SSK@ugb~uuscsidMI-Ze8laZe~o3BUIb3S50i25RIwDH99M,9T20t3xoG-dQfMO94LGOl9AxRTkaz~TykFY-voqaTQI,AQACAAE/FAFS-49/files/fms.htm",
		""),
	FREESITEHOWTO(
		"SSK@8r-uSRcJPkAr-3v3YJR16OCx~lyV2XOKsiG4MOQQBMM,P42IgNemestUdaI7T6z3Og6P-Hi7g9U~e37R3kWGVj8,AQACAAE/freesite-HOWTO-4/",
		"",
		"",
		""),
	FROST("USK@QRZAI1nSm~dAY2hTdzVWXmEhkaI~dso0OadnppBR7kE,wq5rHGBI7kpChBe4yRmgBChIGDug7Xa5SG9vYGXdxR0,AQACAAE/frost/14/",
		"http://jtcfrost.sourceforge.net/",
		"SSK@ugb~uuscsidMI-Ze8laZe~o3BUIb3S50i25RIwDH99M,9T20t3xoG-dQfMO94LGOl9AxRTkaz~TykFY-voqaTQI,AQACAAE/FAFS-49/files/frost.htm",
		""),
	JFREESITE("USK@ZupQjDFZSc3I4orBpl1iTEAPZKo2733RxCUbZ2Q7iH0,EO8Tuf8SP3lnDjQdAPdCM2ve2RaUEN8m-hod3tQ5oQE,AQACAAE/jFreesite/19/Style/",
		"",
		"",
		""),
	JSITE("CHK@2gVK8i-oJ9bqmXOZfkRN1hqgveSUrOdzSxtkndMbLu8,OPKeK9ySG7RcKXadzNN4npe8KSDb9EbGXSiH1Me~6rQ,AAIC--8/jSite.jar",
		"http://downloads.freenetproject.org/alpha/jSite/",
		"SSK@ugb~uuscsidMI-Ze8laZe~o3BUIb3S50i25RIwDH99M,9T20t3xoG-dQfMO94LGOl9AxRTkaz~TykFY-voqaTQI,AQACAAE/FAFS-49/files/jsite.htm",
		""),
	PUBLISH("SSK@940RYvj1-aowEHGsb5HeMTigq8gnV14pbKNsIvUO~-0,FdTbR3gIz21QNfDtnK~MiWgAf2kfwHe-cpyJXuLHdOE,AQACAAE/publish-3/",
		"",
		"",
		""),
	SONE("USK@nwa8lHa271k2QvJ8aa0Ov7IHAV-DFOCFgmDt3X6BpCI,DuQSUZiI~agF8c-6tjsFFGuZ8eICrzWCILB60nT8KKo,AQACAAE/sone/43/",
		"",
		"",
		""),
	THINGAMABLOG(
		"CHK@o8j9T2Ghc9cfKMLvv9aLrHbvW5XiAMEGwGDqH2UANTk,sVxLdxoNL-UAsvrlXRZtI5KyKlp0zv3Ysk4EcO627V0,AAIC--8/thingamablog.zip",
		"http://downloads.freenetproject.org/alpha/thingamablog/thingamablog.zip",
		"",
		"");

	public final String key;
	public final String url;
	public final String helpKey;
	public final String helpUrl;

	private Site(String key, String url, String helpKey, String helpUrl) {
		this.key = key;
		this.url = url;
		this.helpKey = helpKey;
		this.helpUrl = helpUrl;
	}
}
