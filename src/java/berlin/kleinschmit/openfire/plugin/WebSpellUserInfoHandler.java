package berlin.kleinschmit.openfire.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.eclipse.jetty.http.HttpException;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class WebSpellUserInfoHandler extends IQHandler {

	private static final String CHARSET = "UTF-8";
	private static final String PASSWORD = "Lcr42ca4twyKE8Lm";

	private final IQHandlerInfo info;
	private final File cacheDir;
	private final String webSpellHost;

	// private XMPPServer server;
	private UserManager userManager;

	public WebSpellUserInfoHandler(File pluginDirectory) {
		super("WebSpell UserInfo Handler");
		info = new IQHandlerInfo("userinfo", "berlin.kleinschmit.bloodhound.webspell.userinfo");
		cacheDir = new File(pluginDirectory, "userInfoCache");
		cacheDir.mkdir();

		JiveGlobals.migrateProperty("webSpell.host");
		webSpellHost = JiveGlobals.getProperty("webSpell.host");
	}

	@Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException {
		IQ reply = IQ.createResultIQ(packet);
		reply.setChildElement(packet.getChildElement().createCopy());

		// Only packets of type GET can be processed
		if (!IQ.Type.get.equals(packet.getType()) || packet.getTo() == null) {
			reply.setError(PacketError.Condition.bad_request);
			return reply;
		}

		String username = packet.getTo().getNode();

		try {
			Iterator<?> I = reply.getChildElement().elementIterator();

			while (I.hasNext()) {
				Element elt = (Element) I.next();
				if (elt.getName().equals("nickname"))
					provideNickname(username, reply, elt);
				else if (elt.getName().equals("avatar"))
					provideAvatar(username, reply, elt);
			}
		} catch (UserNotFoundException e) {
			reply.setError(PacketError.Condition.item_not_found);
		} catch (Exception e) {
			PacketError err = new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.cancel, e.toString());
			reply.setError(err);
		}

		return reply;
	}

	private void provideAvatar(String username, IQ reply, Element elt) throws MalformedURLException, IOException {
		long mTimeReq = Long.MIN_VALUE, mTimeLoc = Long.MIN_VALUE;

		Attribute attrMtime = elt.attribute("mtime");
		if (attrMtime != null)
			mTimeReq = Long.decode(attrMtime.getValue());

		File avatarFile = new File(cacheDir, username);
		if (avatarFile.exists())
			mTimeLoc = avatarFile.lastModified() / 1000;

		String url = webSpellHost + String.format("avatar.php?name=%s&hash=%s",
				URLEncoder.encode(username, CHARSET),
				URLEncoder.encode(WebSpellPlugin.MD5(PASSWORD, username), CHARSET));

		if (mTimeLoc > Long.MIN_VALUE)
			url += String.format("&mtime=%d", mTimeLoc);

		HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
		http.connect();

		int responseCode = http.getResponseCode();
		if (responseCode == 304) {
			// Nothing to do here.
		} else if (responseCode == 200) {
			InputStream inp = http.getInputStream();
			FileOutputStream out = new FileOutputStream(avatarFile);

			byte[] buffer = new byte[4096];
			int count;

			while ((count = inp.read(buffer, 0, buffer.length)) > 0)
				out.write(buffer, 0, count);
			out.close();

			avatarFile.setLastModified((mTimeReq + 1) * 1000);
			mTimeLoc = mTimeReq + 1;
		} else {
			if (mTimeLoc >= mTimeReq) {
				WebSpellPlugin.setImageFromFile(avatarFile, elt);
				PacketError error = new PacketError(PacketError.Condition.item_not_found, PacketError.Type.continue_processing, http.getResponseMessage());
				reply.setError(error);
				return;
			} else
				throw new HttpException(responseCode, http.getResponseMessage());
		}

		if (mTimeLoc >= mTimeReq)
			WebSpellPlugin.setImageFromFile(avatarFile, elt);
		else
			reply.getChildElement().remove(elt);
	}

	private void provideNickname(String username, IQ reply, Element elt) throws UserNotFoundException {
		User user = userManager.getUser(username);
		elt.addText(user.getName());
	}

	@Override
	public void initialize(XMPPServer server) {
		super.initialize(server);
		// this.server = server;
		this.userManager = server.getUserManager();

	}

	@Override
	public IQHandlerInfo getInfo() {
		return info;
	}

}
