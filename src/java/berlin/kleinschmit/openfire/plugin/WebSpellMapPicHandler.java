package berlin.kleinschmit.openfire.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.eclipse.jetty.http.HttpException;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.util.JiveGlobals;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class WebSpellMapPicHandler extends IQHandler {

	private static final String CHARSET = "UTF-8";
	private static final String PASSWORD = "B3q7cp5YnKjPp5mP";

	private final IQHandlerInfo info;
	private final File cacheDir;
	private final String webSpellHost;

	public WebSpellMapPicHandler(File pluginDirectory) {
		super("WebSpell UserInfo Handler");
		info = new IQHandlerInfo("mappic", "berlin.kleinschmit.bloodhound.webspell.mappic");
		cacheDir = new File(pluginDirectory, "mapPicCache");
		cacheDir.mkdir();

		JiveGlobals.migrateProperty("webSpell.host");
		webSpellHost = JiveGlobals.getProperty("webSpell.host");
	}

	@Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException {
		IQ reply = IQ.createResultIQ(packet);
		reply.setChildElement(packet.getChildElement().createCopy());
		
		Attribute attr;
		Element eltReply = reply.getChildElement();
		Element eltRequest = packet.getChildElement();
		
		attr = eltReply.attribute("game");
		if (attr != null)
			eltReply.remove(attr);

		attr = reply.getChildElement().attribute("map");
		if (attr != null)
			eltReply.remove(attr);

		attr = reply.getChildElement().attribute("mtime");
		if (attr != null)
			eltReply.remove(attr);

		// Only packets of type GET can be processed
		if (!IQ.Type.get.equals(packet.getType())) {
			reply.setError(PacketError.Condition.bad_request);
			return reply;
		}

		try {
			String game = eltRequest.attributeValue("game");
			String map = eltRequest.attributeValue("map");

			if (game == null || map == null) {
				reply.setError(PacketError.Condition.bad_request);
				return reply;
			}
			
			long mTimeReq = Long.MIN_VALUE, mTimeLoc = Long.MIN_VALUE;

			Attribute attrMtime = eltRequest.attribute("mtime");
			if (attrMtime != null)
				mTimeReq = Long.decode(attrMtime.getValue());

			File mappicFile = new File(cacheDir, game + "_" + map);
			if (mappicFile.exists())
				mTimeLoc = mappicFile.lastModified() / 1000;

			String url = webSpellHost + String.format("mappic.php?game=%s&map=%s&hash=%s",
					URLEncoder.encode(game, CHARSET),
					URLEncoder.encode(map, CHARSET),
					URLEncoder.encode(WebSpellPlugin.MD5(PASSWORD, game, map), CHARSET));

			if (mTimeLoc > Long.MIN_VALUE)
				url += String.format("&mtime=%d", mTimeLoc);

			HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
			http.connect();

			int responseCode = http.getResponseCode();

			if (responseCode == 304) {
				// Nothing to do here.
			} else if (responseCode == 200 || responseCode == 203) {
				InputStream inp = http.getInputStream();
				FileOutputStream out = new FileOutputStream(mappicFile);

				byte[] buffer = new byte[4096];
				int count;

				while ((count = inp.read(buffer, 0, buffer.length)) > 0)
					out.write(buffer, 0, count);
				out.close();
				
				if (responseCode == 200) {
					mappicFile.setLastModified((mTimeReq + 1) * 1000);
				} else {
					mappicFile.setLastModified(0);
				}					
				mTimeLoc = mTimeReq + 1;
			} else {
				if (mTimeLoc >= mTimeReq) {
					WebSpellPlugin.setImageFromFile(mappicFile, eltReply);
					PacketError error = new PacketError(PacketError.Condition.item_not_found, PacketError.Type.continue_processing, http.getResponseMessage());
					reply.setError(error);
					return reply;
				} else
					throw new HttpException(responseCode, http.getResponseMessage());
			}

			if (mTimeLoc >= mTimeReq) {
				WebSpellPlugin.setImageFromFile(mappicFile, eltReply);
			}
		} catch (Exception e) {
			PacketError err = new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.cancel, e.toString());
			reply.setError(err);
		}

		return reply;
	}

	@Override
	public IQHandlerInfo getInfo() {
		return info;
	}

}
