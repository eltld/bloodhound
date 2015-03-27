package berlin.kleinschmit.openfire.plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;

public class WebSpellServerlistHandler extends IQHandler {

	private static final Logger Log = LoggerFactory.getLogger(WebSpellServerlistHandler.class);

	private final IQHandlerInfo info;
    private String connectionString;
    private String dbPrefix;
    private String getServerlistSQL = "";


	public WebSpellServerlistHandler(File pluginDirectory) {
		super("WebSpell Serverlist Handler");
		info = new IQHandlerInfo("serverlist", "berlin.kleinschmit.bloodhound.webspell.serverlist");
	
        JiveGlobals.migrateProperty("webSpell.dbPrefix");

        String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
        try {
            Class.forName(jdbcDriver).newInstance();
        }
        catch (Exception e) {
            Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
            return;
        }
        connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
        
        dbPrefix = JiveGlobals.getProperty("webSpell.dbPrefix");
        getServerlistSQL = String.format("SELECT ip FROM `%sservers`", dbPrefix);
	}

	@Override
	public IQ handleIQ(IQ packet) throws UnauthorizedException {
		IQ reply = IQ.createResultIQ(packet);
		reply.setChildElement(packet.getChildElement().createCopy());
		Element query = reply.getChildElement();
		
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement(getServerlistSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
            	Element server = query.addElement("server");
            	server.addText(rs.getString(1));
            }
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

		return reply;
	}

	@Override
	public IQHandlerInfo getInfo() {
		return info;
	}

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(connectionString);
    }
}
