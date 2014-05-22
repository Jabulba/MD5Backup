package com.jabulba.md5backup.managers;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;

import org.bukkit.configuration.file.YamlConfiguration;

import com.jabulba.md5backup.BackupFile;

public class ConfigurationManager {

    public static final String TICKET_URL = "http://dev.bukkit.org/profiles/Jabulba/";
    public static final String HELP_IMPROVE_MESSAGE = "Help improve the plugin by opening a ticket with this error at ".concat(TICKET_URL);

    private static final String INFO_LINE = "======================================================================================";
    private static final String INFO_TITLE = "============================== Backup Versions Avalible ==============================";
    private File configFile = null;
    private YamlConfiguration config = null;
    // config variables
    private String version;
    private String databaseFileName;
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
    ConfigurationManager(){
	
    }
}
