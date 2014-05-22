package com.jabulba.md5backup.threads;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.jabulba.md5backup.MD5Backup;

public class SqlManager extends Thread {
    private static String sqlClass = "org.sqlite.JDBC";
    private Connection connection;
    private Statement statement;
    private MD5Backup plugin;
    private List<String> queue = new ArrayList<String>();

    //
    //
    //
    private Thread blinker;

    public void start() {
	blinker = new Thread(this);
	blinker.start();
    }

    public void run() {
	statement = connection.createStatement();
	statement.setQueryTimeout(30); // set timeout to 30 sec.
	while (!this.isInterrupted()) {

	    try {
		Thread.sleep(5000);
	    } catch (InterruptedException e) {
		// TODO Save queue to file before interruption!
		//e.printStackTrace();
	    }
	}
	statement = null;
	connection = null;

    }
    
    private boolean checkConnection(){
	if( connection.isValid(5000)){
	    return true;
	}
	return false;
    }
    //
    //
    //

    // initialize driver, setup variables and get ready to create connections and relay information between the plugin and DB!
    public SqlManager() throws SQLException {
	plugin = MD5Backup.getInstance();
	try {
	    Class.forName(sqlClass);
	} catch (ClassNotFoundException e) {
	    throw new RuntimeException("Reboot the universe, Things are messed up!\nOn the real side: org.sqlite.JDBC class was not found. Where is your SQLite Library!?", e);
	}
    }

    public void newConnection() throws SQLException {
	connection = DriverManager.getConnection("jdbc:sqlite:".concat(plugin.getDataFolder().getAbsolutePath()).concat(File.separator).concat(plugin.getDatabaseFileName()));
	statement = connection.createStatement();
	statement.setQueryTimeout(30); // set timeout to 30 sec.
    }

    /**
     * Checks if the global connection to the Database is still valid.
     * 
     * @return <b>true</b> if the connection is valid.<br>
     *         <b>false</b> if the connection is no longer valid.
     * @throws SQLException
     */
    public boolean connectionStatus() {
	try {
	    if (connection != null && connection.isValid(30)) {
		return true;
	    }
	} catch (SQLException e) {
	    e.printStackTrace();
	}
	return false;
    }

    public void updateFile(String fileName, long modifiedDate, String MD5, int fileExists) {
	if (!connectionStatus()) {
	    return;
	    // TODO: queue update for later. remember to use the queued update on the check if its newer!
	}
	fileName = fileName.replaceAll("'", "''");

	try {
	    statement.executeUpdate("update MD5Backups SET modifiedDate=".concat(String.valueOf(modifiedDate)).concat(", MD5='").concat(MD5).concat("', fileExists=").concat(String.valueOf(fileExists)).concat(" WHERE filename='").concat(fileName).concat("'"));
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    // TODO: DECEMT ERROR MESSAGE!
	    plugin.getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	}
    }

    public int insertFile(String fileName, long modifiedDate, String MD5, int fileExists) {
	fileName = fileName.replaceAll("'", "''");

	try {
	    statement.executeUpdate("insert into MD5Backups(fileName, modifiedDate, MD5, fileExists) values('".concat(fileName).concat("', ").concat(String.valueOf(modifiedDate)).concat(", '").concat(MD5).concat("', ").concat(String.valueOf(fileExists)).concat(")"));

	    ResultSet rs = statement.executeQuery("select id from MD5Backups where fileName='".concat(fileName).concat("'"));
	    return rs.getInt("id");

	    // statement.executeUpdate("insert into MD5Timeline(ref, modifiedDate, MD5, fileExists) values(".concat(String.valueOf(id)).concat(", ").concat(String.valueOf(modifiedDate)).concat(", '").concat(MD5).concat("', ").concat(String.valueOf(fileExists)).concat(")"));
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    plugin.getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	    return 0;
	}
    }
}
