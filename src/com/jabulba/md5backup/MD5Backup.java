package com.jabulba.md5backup;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.jabulba.md5backup.threads.BackupThread;
import com.twmacinta.util.MD5;

public class MD5Backup extends JavaPlugin {
    public static final String TICKET_URL = "http://dev.bukkit.org/profiles/Jabulba/";
    public static final String HELP_IMPROVE_MESSAGE = "Help improve the plugin by opening a ticket with this error at ".concat(TICKET_URL);

    private static final String INFO_LINE = "======================================================================================";
    private static final String INFO_TITLE = "============================== Backup Versions Avalible ==============================";

    private static MD5Backup instance;
    public static MD5Backup getInstance() {
        return instance;
    }

    private File configFile = null;
    private YamlConfiguration config = null;

    // config variables
    private String version;
    private String databaseFileName;
    public String getDatabaseFileName() {
        return databaseFileName;
    }
    private String databaseURL;
    public String getdatabaseURL() {
        return databaseURL;
    }

    // private List<String> backupSchedule;
    // TODO create version check
    // TODO add metrics
    // TODO add backup shedules
    private Path backupStorageFolder;
    public Path getBackupStorageFolder() {
        return backupStorageFolder;
    }

    private HashSet<Path> excludedFiles = new HashSet<Path>();
    public HashSet<Path> getExcludedFiles() {
        return excludedFiles;
    }
    
    private HashSet<Path> volatileFiles = new HashSet<Path>();
    public HashSet<Path> getVolatileFiles() {
        return excludedFiles;
    }

    private HashMap<String, BackupFile> backupFileMap = new HashMap<String, BackupFile>();
    public HashMap<String, BackupFile> getBackupFileMap() {
        return backupFileMap;
    }

    private Path serverRootFolder;
    public Path getServerRootFolder() {
        return serverRootFolder;
    }

    // Global connection handle
    private Connection connection = null;
    private int connectionUsers = 0;
    private Statement statement = null;

    //REFACTOR
    private BackupThread backupThread;
    /**
     * Returns a Path object representing the absolute path of this path. <br>
     * @return a BackupThread object representing the backup thread
     */
    public BackupThread getBackupThread(){
	return backupThread;
    }
    
    
    //END REFACTOR
    public void onEnable() {
	instance = this;
	loadConfig();
	loadDatabase();
    }

    private void loadConfig() {
	serverRootFolder = Paths.get("").toAbsolutePath();
	configFile = new File(getDataFolder().getPath().concat(File.separator).concat("config.yml"));
	saveDefaultConfig();
	config = YamlConfiguration.loadConfiguration(configFile);

	version = config.getString("version", "unknown");

	backupStorageFolder = Paths.get(config.getString("BackupFolder", "Backup"));

	for (String excludedFile : config.getStringList("Excluded Files")) {
	    try {
		Path path = Paths.get(excludedFile);
		excludedFiles.add(path.toAbsolutePath());
	    } catch (InvalidPathException e) {
		getLogger().log(Level.SEVERE, "Invalid path in excluded files list. Ignoring exclusion, file WILL be backed up!", e);
	    }
	}
	excludedFiles.add(backupStorageFolder.toAbsolutePath());
	for (String volatileFile : config.getStringList("Volatile Files")) {
	    try {
		Path path = Paths.get(volatileFile);
		volatileFiles.add(path.toAbsolutePath());
	    } catch (InvalidPathException e) {
		getLogger().log(Level.SEVERE, "Invalid path in excluded files list. Ignoring exclusion, file WILL be backed up!", e);
	    }
	}
	excludedFiles.add(backupStorageFolder.toAbsolutePath());

	databaseFileName = config.getString("Database File", "Backup.db");

	getCommand("bk").setUsage("/bk b\n  Create a complete backup of the server. Changed files are skipped.");
	getCommand("bk").setExecutor(new Commands(this));
	getLogger().log(Level.CONFIG, "Config ".concat(version).concat("loaded!"));
    }

    private void loadDatabase() {
	try {
	    Class.forName("org.sqlite.JDBC");
	} catch (ClassNotFoundException e) {
	    getLogger().info("======================================================");
	    getLogger().info("==== SQLITE DRIVER NOT FOUND! DISABLING MD5BACKUP ====");
	    getLogger().info("======================================================");
	    getLogger().log(Level.SEVERE, "SQLite Driver not found!", e);
	    instance = null;
	    getServer().getPluginManager().disablePlugin(this);
	    return;
	}

	Connection connection = null;
	try {
	    // create a database connection
	    if (connectionUsers == 0) {
		connectionUsers++;
		connection = DriverManager.getConnection("jdbc:sqlite:".concat(getDataFolder().getAbsolutePath()).concat(File.separator).concat(databaseFileName));
		statement = connection.createStatement();
		statement.setQueryTimeout(30); // set timeout to 30 sec.
	    } else {
		connectionUsers++;
	    }
	    statement.executeUpdate("CREATE TABLE IF NOT EXISTS MD5Backups (id integer primary key asc not null, filename text not null unique, modifiedDate integer not null, MD5 text, fileExists boolean not null)");
	    statement.executeUpdate("CREATE TABLE IF NOT EXISTS MD5Timeline (ref integer not null, modifiedDate integer not null, MD5 text, fileExists boolean not null)");
	    statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS MD5TimelineInserterA AFTER UPDATE ON MD5Backups BEGIN insert into MD5Timeline(ref, modifiedDate, MD5, fileExists) values(new.id, new.modifiedDate, new.MD5, new.fileExists); END;");
	    statement.executeUpdate("CREATE TRIGGER IF NOT EXISTS MD5TimelineInserterB AFTER INSERT ON MD5Backups BEGIN insert into MD5Timeline(ref, modifiedDate, MD5, fileExists) values(new.id, new.modifiedDate, new.MD5, new.fileExists); END;");

	    ResultSet rs = statement.executeQuery("select * from MD5Backups");
	    while (rs.next()) {
		int id = rs.getInt("id");
		if (id == 0) {
		    continue;
		}

		String fileName = rs.getString("filename");
		Path filePath = Paths.get(fileName);

		long modifiedDateLong = rs.getLong("modifiedDate");
		FileTime modifiedDate = FileTime.fromMillis(modifiedDateLong);

		String MD5 = rs.getString("MD5");

		boolean backupExists = rs.getBoolean("fileExists");

		backupFileMap.put(fileName, new BackupFile(id, filePath, modifiedDate, MD5, backupExists));
	    }
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	} finally {
	    connectionUsers--;
	    if (connectionUsers == 0) {
		try {
		    if (connection != null) {
			connection.close();
			connection = null;
			statement = null;
		    }
		} catch (SQLException e) {
		    // connection close failed.
		    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
		}
	    }
	}
    }

    public void onDisable() {
	instance = null;
	try {
	    if (connection != null) {
		connection.close();
		connection = null;
		statement = null;
	    }
	} catch (SQLException e) {
	    // connection close failed.
	    System.err.println(e);
	}

    }

    protected void createBackup() {
	getLogger().info("Starting server backup!");
	getServer().broadcastMessage("Server backup started on main thread!");
	getServer().broadcastMessage("Stopping server ticks.");
	getServer().savePlayers();
	List<World> worlds = getServer().getWorlds();
	for (World world : worlds) {
	    if (world.isAutoSave()) {
		world.setAutoSave(false);
		world.save();
	    } else {
		world.setAutoSave(true);
		world.save();
		worlds.remove(world);
		world.setAutoSave(false);
	    }
	}
	if (connection == null) {
	    try {
		Class.forName("org.sqlite.JDBC");
	    } catch (ClassNotFoundException e) {
		getLogger().info("======================================================");
		getLogger().info("==== SQLITE DRIVER NOT FOUND! DISABLING MD5BACKUP ====");
		getLogger().info("======================================================");
		getLogger().log(Level.SEVERE, "SQLite Driver not found!");
		e.printStackTrace();
		instance = null;
		getServer().getPluginManager().disablePlugin(this);
		return;
	    }
	}

	try {
	    // create a database connection
	    if (connectionUsers == 0) {
		connectionUsers++;
		connection = DriverManager.getConnection("jdbc:sqlite:".concat(getDataFolder().getAbsolutePath()).concat(File.separator).concat(databaseFileName));
		statement = connection.createStatement();
		statement.setQueryTimeout(30); // set timeout to 30 sec.
	    } else {
		connectionUsers++;
	    }
	    try {
		Files.walkFileTree(serverRootFolder, new SimpleFileVisitor<Path>() {

		    @Override
		    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
			if (excludedFiles.contains(path)) {
			    getLogger().info("Skipping folder: ".concat(path.toAbsolutePath().toString()));
			    return FileVisitResult.SKIP_SUBTREE;
			}
			getLogger().info("Visiting folder: ".concat(path.toAbsolutePath().toString()));
			return FileVisitResult.CONTINUE;
		    }

		    @Override
		    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
			if (excludedFiles.contains(path)) {
			    getLogger().info("Skipping file: ".concat(path.toAbsolutePath().toString()));
			    return FileVisitResult.CONTINUE;
			}

			path = serverRootFolder.relativize(path);

			BackupFile backupFile = backupFileMap.get(path.toString());
			if (backupFile != null) {
			    if (!backupFile.wasModified()) {
				return FileVisitResult.CONTINUE;
			    }
			}

			String liveMD5;
			try {
			    liveMD5 = MD5.asHex(MD5.getHash(path.toFile()));
			} catch (IOException e) {
			    // excludedFiles.add(path.toAbsolutePath());
			    if (e.getMessage().equals("The process cannot access the file because another process has locked a portion of the file")) {
				MD5Backup.instance.getLogger().info("Error during backup of file: ".concat(path.toAbsolutePath().toString()));
				MD5Backup.instance.getLogger().log(Level.WARNING, "Attempted to access the file while locked! If this exception persists you can add this file to the Excluded Files list in the config.cfg file!", e);
			    }
			    return FileVisitResult.CONTINUE;
			}

			getLogger().info(liveMD5);
			if (backupFile != null) {
			    getLogger().info(backupFile.getBackupMD5());

			}
			if (backupFile == null || !backupFile.getBackupMD5().equals(liveMD5)) {
			    Date currentDate = new Date();
			    Path backupDir = backupStorageFolder.resolve(path);
			    Path backupFilePath = backupDir.resolve(String.valueOf(currentDate.getTime()).concat(File.separator).concat(path.getFileName().toString()));
			    try {
				Files.createDirectories(backupFilePath.getParent());
			    } catch (Exception e) {
				MD5Backup.instance.getLogger().info("Error during backup of file: ".concat(path.toAbsolutePath().toString()));
				MD5Backup.instance.getLogger().log(Level.WARNING, "Error creating backup dir: ".concat(backupFilePath.getParent().toString()).concat(" The file will be ignored!"), e);
				MD5Backup.instance.getLogger().info(HELP_IMPROVE_MESSAGE);
				return FileVisitResult.CONTINUE;
			    }
			    Files.copy(path, backupFilePath, StandardCopyOption.COPY_ATTRIBUTES);
			    FileTime lastModified = Files.getLastModifiedTime(backupFilePath);
			    int id;
			    if (backupFile == null) {
				id = insertFileToDB(path.toString(), lastModified.toMillis(), liveMD5, 1);
			    } else {
				id = backupFile.getID();
				updateDBWithFile(path.toString(), lastModified.toMillis(), liveMD5, 1);
			    }
			    backupFileMap.put(path.toString(), new BackupFile(id, path, lastModified, liveMD5, true));
			} else {
			    getLogger().info("Date missmatch but MD5 match! Ignoring file: ".concat(path.toAbsolutePath().toString()));
			}
			return FileVisitResult.CONTINUE;
		    }
		});
	    } catch (IOException e) {
		getLogger().log(Level.SEVERE, "Unhandled exception during server backup.", e);
		MD5Backup.instance.getLogger().info(HELP_IMPROVE_MESSAGE);
	    }

	    getLogger().info("Visiting dead entries...");
	    for (Entry<String, BackupFile> backupFileEntry : backupFileMap.entrySet()) {
		BackupFile backupFile = backupFileEntry.getValue();
		if (!backupFile.getLiveExists() && backupFile.getBackupExists()) {
		    Path backupLiveFile = backupFile.getLiveFile();
		    getLogger().info("Updating entry for: ".concat(backupLiveFile.toAbsolutePath().toString()));
		    long sysTime = System.currentTimeMillis();
		    backupFileMap.put(backupFileEntry.getKey(), new BackupFile(backupFile.getID(), backupLiveFile, FileTime.fromMillis(sysTime), "", false));
		    updateDBWithFile(backupFileEntry.getKey(), sysTime, "", 0);
		}
	    }
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	} finally {
	    connectionUsers--;
	    if (connectionUsers == 0) {
		try {
		    if (connection != null) {
			connection.close();
			connection = null;
			statement = null;
		    }
		} catch (SQLException e) {
		    // connection close failed.
		    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
		}
	    }
	}
	for (World world : worlds) {
	    world.setAutoSave(true);
	}
	getServer().broadcastMessage("Resuming server ticks.");
	getServer().broadcastMessage("Server backup main thread finished!");
	getLogger().info("Server backup complete!");
    }

    public void updateDBWithFile(String fileName, long modifiedDate, String MD5, int fileExists) {
	fileName = fileName.replaceAll("'", "''");

	try {
	    statement.executeUpdate("update MD5Backups SET modifiedDate=".concat(String.valueOf(modifiedDate)).concat(", MD5='").concat(MD5).concat("', fileExists=").concat(String.valueOf(fileExists)).concat(" WHERE filename='").concat(fileName).concat("'"));
	    // statement.executeUpdate("insert into MD5Timeline(ref, modifiedDate, MD5, fileExists) values(".concat(String.valueOf(id)).concat(", ").concat(String.valueOf(modifiedDate))
	    // +
	    // ", '".concat(MD5).concat("', ").concat(String.valueOf(fileExists)).concat(")"));
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	}
    }

    public int insertFileToDB(String fileName, long modifiedDate, String MD5, int fileExists) {
	fileName = fileName.replaceAll("'", "''");

	try {
	    statement.executeUpdate("insert into MD5Backups(fileName, modifiedDate, MD5, fileExists) values('".concat(fileName).concat("', ").concat(String.valueOf(modifiedDate)).concat(", '").concat(MD5).concat("', ").concat(String.valueOf(fileExists)).concat(")"));

	    ResultSet rs = statement.executeQuery("select id from MD5Backups where fileName='".concat(fileName).concat("'"));
	    return rs.getInt("id");

	    // statement.executeUpdate("insert into MD5Timeline(ref, modifiedDate, MD5, fileExists) values(".concat(String.valueOf(id)).concat(", ").concat(String.valueOf(modifiedDate)).concat(", '").concat(MD5).concat("', ").concat(String.valueOf(fileExists)).concat(")"));
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	    return 0;
	}
    }

    protected void backupFileInfo(String file) {
	if (connection == null) {
	    try {
		Class.forName("org.sqlite.JDBC");
	    } catch (ClassNotFoundException e) {
		getLogger().info("======================================================");
		getLogger().info("==== SQLITE DRIVER NOT FOUND! DISABLING MD5BACKUP ====");
		getLogger().info("======================================================");
		getLogger().log(Level.SEVERE, "SQLite Driver not found!", e);
		instance = null;
		getServer().getPluginManager().disablePlugin(this);
		return;
	    }
	}

	try {
	    // create a database connection
	    if (connectionUsers == 0) {
		connectionUsers++;
		connection = DriverManager.getConnection("jdbc:sqlite:".concat(getDataFolder().getAbsolutePath()).concat(File.separator).concat(databaseFileName));
		statement = connection.createStatement();
		statement.setQueryTimeout(30); // set timeout to 30 sec.
	    } else {
		connectionUsers++;
	    }

	    Path filePath = Paths.get(file);
	    ResultSet rs = statement.executeQuery("select * from MD5Backups where fileName='".concat(filePath.toString()).concat("'"));
	    if (!rs.isBeforeFirst()) {
		getLogger().info("Backup not found for the file ".concat(file));
		return;
	    }
	    int id = rs.getInt("id");
	    String idString = String.valueOf(id);
	    long modifiedDateLong = rs.getLong("modifiedDate");
	    FileTime modifiedDate = FileTime.fromMillis(modifiedDateLong);
	    String MD5 = rs.getString("MD5");

	    boolean backupExists = rs.getBoolean("fileExists");

	    getLogger().info(INFO_LINE);
	    getLogger().info("ID: ".concat(idString).concat("\t File: ").concat(filePath.getFileName().toString()));
	    getLogger().info("Live Path: ".concat(filePath.toAbsolutePath().toString()));
	    // filePath = serverRootFolder.relativize(filePath);
	    Path backupDir = backupStorageFolder.resolve(filePath);
	    getLogger().info("Backup path: ".concat(backupDir.toAbsolutePath().toString()));
	    getLogger().info("Last backup date: ".concat(modifiedDate.toString()));
	    if (backupExists) {
		getLogger().info("Last backup MD5: ".concat(MD5));
	    } else {
		getLogger().info("This file was not present during last backup and is marked as dead.");
	    }
	    getLogger().info(INFO_LINE);
	    getLogger().info(INFO_TITLE);
	    getLogger().info("Alive\tBackup Date\t\t\t\tMD5");

	    rs = statement.executeQuery("select * from MD5Timeline where ref='".concat(idString).concat("'"));
	    while (rs.next()) {
		modifiedDateLong = rs.getLong("modifiedDate");
		modifiedDate = FileTime.fromMillis(modifiedDateLong);
		MD5 = rs.getString("MD5");
		backupExists = rs.getBoolean("fileExists");
		getLogger().info(String.valueOf(backupExists).concat("\t").concat(new Date(modifiedDate.toMillis()).toString()).concat("\t\t").concat(MD5));
	    }
	    getLogger().info(INFO_LINE);
	} catch (SQLException e) {
	    // if the error message is "out of memory",
	    // it probably means no database file is found
	    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
	} finally {
	    connectionUsers--;
	    if (connectionUsers == 0) {
		try {
		    if (connection != null) {
			connection.close();
			connection = null;
			statement = null;
		    }
		} catch (SQLException e) {
		    // connection close failed.
		    getLogger().log(Level.SEVERE, "SQLEXCEPTION!!!", e);
		}
	    }
	}
    }
}
