package com.jabulba.md5backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.logging.Level;

public class BackupFile {
    private Path liveFile;
    // private Path backupFile;
    private int id;
    private FileTime backupLastModifiedDate;
    private String backupMD5;
    private boolean backupExists;
    private MD5Backup md5Backup;

    
    public BackupFile(int id, Path liveFile, FileTime backupLastModifiedDate, String backupMD5, boolean backupExists) {
	this.id = id;
	this.liveFile = liveFile;
	// backupFile =
	// liveFile.concat(File.separator).concat(String.valueOf(lastModifiedDate)).concat(File.separator).concat(liveFile.getFileName().toString());

	this.backupLastModifiedDate = backupLastModifiedDate;
	this.backupMD5 = backupMD5;
	this.backupExists = backupExists;
	md5Backup = MD5Backup.getInstance();
    }

    public boolean wasModified() {
	boolean liveExists = getLiveExists();
	FileTime liveLastModifiedDate;

	try {
	    liveLastModifiedDate = Files.getLastModifiedTime(liveFile);
	} catch (IOException e) {
	    liveLastModifiedDate = FileTime.fromMillis(0);
	    md5Backup.getLogger().log(Level.WARNING, "Error reading last modified date for file: ".concat(liveFile.toAbsolutePath().toString()), e);
	}

	if (backupExists && !liveExists) {
	    return true;

	} else if (!backupExists && liveExists) {
	    return true;

	} else if (liveLastModifiedDate.compareTo(backupLastModifiedDate) != 0) {
	    return true;
	}
	return false;
    }

    public int getID() {
	return id;
    }

    public Path getLiveFile(){
	return liveFile;
    }
    
    public String getBackupMD5() {
	return backupMD5;
    }

    public boolean getLiveExists() {
	return Files.exists(liveFile);
    }
    
    public boolean getBackupExists(){
	return backupExists;
    }
}