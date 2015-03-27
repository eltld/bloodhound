package berlin.kleinschmit.openfire.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.codec.binary.Base64;
import org.dom4j.Element;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.handler.IQHandler;

public class WebSpellPlugin implements Plugin {

	private IQRouter router;
	private Collection<IQHandler> handlers = new ArrayList<IQHandler>();

	private static MessageDigest md5 = null;
	static {
		try {
			md5 = MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
		}
	}

	@Override
	public void initializePlugin(PluginManager manager, File pluginDirectory) {

		router = XMPPServer.getInstance().getIQRouter();
		handlers.add(new WebSpellUserInfoHandler(pluginDirectory));
		handlers.add(new WebSpellServerlistHandler(pluginDirectory));
		handlers.add(new WebSpellMapPicHandler(pluginDirectory));

		for (IQHandler handler : handlers)
			router.addHandler(handler);
	}

	@Override
	public void destroyPlugin() {
		for (IQHandler handler : handlers)
			router.removeHandler(handler);
	}

	public static String MD5(String... sources) {

		md5.reset();
		for (String source : sources)
			try {
				md5.update(source.getBytes("UTF-8"));
			} catch (UnsupportedEncodingException e) {
			}

		BigInteger bigInt = new BigInteger(1, md5.digest());
		String hashtext = bigInt.toString(16);

		while (hashtext.length() < 32) {
			hashtext = "0" + hashtext;
		}
		
		return hashtext;
	}
	

	public static void setImageFromFile(File imageFile, Element elt) throws IOException {
		FileInputStream file = new FileInputStream(imageFile);
		int count = (int) imageFile.length();
		byte[] buffer = new byte[count];
		file.read(buffer, 0, count);
		file.close();
		elt.addText(new String(Base64.encodeBase64(buffer)));

		String ext = imageFile.getName();
		ext = ext.substring(ext.length() - 3);
		if (ext.equalsIgnoreCase("jpg"))
			ext = "jpeg";

		elt.addAttribute("mimeType", "image/" + ext);
	}

}
