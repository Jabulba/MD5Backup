package com.jabulba.md5backup.threads;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.logging.Level;

import com.jabulba.md5backup.BackupFile;
import com.jabulba.md5backup.MD5Backup;
import com.jabulba.md5backup.managers.SaveManager;
import com.twmacinta.util.MD5;

public class BackupThread extends Thread {
    private MD5Backup md5Backup;
    private SaveManager saveManager;
    private boolean running = false;

    BackupThread(String name) {
	md5Backup = MD5Backup.getInstance();
	saveManager = SaveManager.getInstance();
    }

    public void run() {
	if(running){
	    md5Backup.getLogger().warning("An attempt to start a backup has been called, but the backup is ongoing! Aborted.");
	    return;
	}
	running = true;
	md5Backup.getServer().broadcastMessage("Starting server backup!");
	saveManager.saveAll();
	saveManager.disableWorldSave();
	// connect to sql server
	backupVolatileData();
	backupStaticData();
	checkDeadEntries();
	purgeBackups();
	// disconnect from sql server
	saveManager.enableWorldSave();
	md5Backup.getServer().getLogger().info("Server backup complete!");
	running = false;
    }

    public void start() {
	if(running){
	    md5Backup.getLogger().warning("An attempt to start a backup has been called, but the backup is ongoing! Aborted.");
	    return;
	}
    }

    public boolean isRunning() {
	return running;
    }

    private void backupVolatileData() {
	for (Path entry : md5Backup.getVolatileFiles()) {
	    if (Files.isDirectory(entry)) {
		try {
		    Files.walkFileTree(md5Backup.getServerRootFolder(), new SimpleFileVisitor<Path>() {
			private MD5Backup md5Backup = MD5Backup.getInstance();
			private HashSet<Path> excludedFiles = md5Backup.getExcludedFiles();

			@Override
			public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
			    if (excludedFiles.contains(path)) {
				md5Backup.getLogger().info("Skipping excuded folder: ".concat(path.toAbsolutePath().toString()));
				return FileVisitResult.SKIP_SUBTREE;
			    }
			    md5Backup.getLogger().info("Visiting folder: ".concat(path.toAbsolutePath().toString()));
			    return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
			    if (excludedFiles.contains(path)) {
				md5Backup.getLogger().info("Skipping excuded file: ".concat(path.toAbsolutePath().toString()));
				return FileVisitResult.CONTINUE;
			    }
			    backupFile(path);
			    return FileVisitResult.CONTINUE;
			}
		    });
		} catch (IOException e) {
		    md5Backup.getLogger().log(Level.SEVERE, "Unhandled exception during volatile backup.", e);
		    md5Backup.getLogger().info(MD5Backup.HELP_IMPROVE_MESSAGE);
		}
	    } else {
		try {
		    backupFile(entry);
		} catch (IOException e) {
		    md5Backup.getLogger().log(Level.SEVERE, "Unhandled exception during volatile backup.", e);
		    md5Backup.getLogger().info(MD5Backup.HELP_IMPROVE_MESSAGE);
		}
	    }
	}
    }

    // TODO: separate most of the backup process to its own methods or even class.
    private void backupStaticData() {
	try {
	    Files.walkFileTree(md5Backup.getServerRootFolder(), new SimpleFileVisitor<Path>() {
		private MD5Backup md5Backup = MD5Backup.getInstance();
		private HashSet<Path> excludedFiles = md5Backup.getExcludedFiles();
		private HashSet<Path> volatileFiles = md5Backup.getVolatileFiles();

		@Override
		public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
		    if (excludedFiles.contains(path) || volatileFiles.contains(path)) {
			md5Backup.getLogger().info("Skipping excuded or volatile folder: ".concat(path.toAbsolutePath().toString()));
			return FileVisitResult.SKIP_SUBTREE;
		    }
		    md5Backup.getLogger().info("Visiting folder: ".concat(path.toAbsolutePath().toString()));
		    return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
		    if (excludedFiles.contains(path) || volatileFiles.contains(path)) {
			md5Backup.getLogger().info("Skipping excuded or volatile file: ".concat(path.toAbsolutePath().toString()));
			return FileVisitResult.CONTINUE;
		    }
		    backupFile(path);
		    return FileVisitResult.CONTINUE;
		}
	    });
	} catch (IOException e) {
	    md5Backup.getLogger().log(Level.SEVERE, "Unhandled exception during server backup.", e);
	    md5Backup.getLogger().info(MD5Backup.HELP_IMPROVE_MESSAGE);
	}
    }

    private void checkDeadEntries() {
	md5Backup.getLogger().info("Visiting dead entries...");
	for (Entry<String, BackupFile> backupFileEntry : md5Backup.getBackupFileMap().entrySet()) {
	    BackupFile backupFile = backupFileEntry.getValue();
	    if (!backupFile.getLiveExists() && backupFile.getBackupExists()) {
		Path backupLiveFile = backupFile.getLiveFile();
		md5Backup.getLogger().info("Updating entry for: ".concat(backupLiveFile.toAbsolutePath().toString()));
		long sysTime = System.currentTimeMillis();
		md5Backup.getBackupFileMap().put(backupFileEntry.getKey(), new BackupFile(backupFile.getID(), backupLiveFile, FileTime.fromMillis(sysTime), "", false));
		md5Backup.updateDBWithFile(backupFileEntry.getKey(), sysTime, "", 0);
	    }
	}
    }

    private void purgeBackups() {

    }

    private void backupFile(Path path) throws IOException {
	path = md5Backup.getServerRootFolder().relativize(path);

	BackupFile backupFile = md5Backup.getBackupFileMap().get(path.toString());
	if (backupFile != null) {
	    if (!backupFile.wasModified()) {
		return;
	    }
	}

	String liveMD5;
	try {
	    liveMD5 = MD5.asHex(MD5.getHash(path.toFile()));
	} catch (IOException e) {
	    // excludedFiles.add(path.toAbsolutePath());
	    if (e.getMessage().equals("The process cannot access the file because another process has locked a portion of the file")) {
		md5Backup.getLogger().info("Error during backup of file: ".concat(path.toAbsolutePath().toString()));
		md5Backup.getLogger().log(Level.WARNING, "Attempted to access the file while locked! If this exception persists you can add this file to the Excluded Files list in the config.cfg file!", e);
	    }
	    return;
	}

	md5Backup.getLogger().info(liveMD5);
	if (backupFile != null) {
	    md5Backup.getLogger().info(backupFile.getBackupMD5());

	}
	if (backupFile == null || !backupFile.getBackupMD5().equals(liveMD5)) {
	    Date currentDate = new Date();
	    Path backupDir = md5Backup.getBackupStorageFolder().resolve(path);
	    Path backupFilePath = backupDir.resolve(String.valueOf(currentDate.getTime()).concat(File.separator).concat(path.getFileName().toString()));
	    try {
		Files.createDirectories(backupFilePath.getParent());
	    } catch (Exception e) {
		md5Backup.getLogger().info("Error during backup of file: ".concat(path.toAbsolutePath().toString()));
		md5Backup.getLogger().log(Level.WARNING, "Error creating backup dir: ".concat(backupFilePath.getParent().toString()).concat(" The file will be ignored!"), e);
		md5Backup.getLogger().info(MD5Backup.HELP_IMPROVE_MESSAGE);
		return;
	    }
	    Files.copy(path, backupFilePath, StandardCopyOption.COPY_ATTRIBUTES);
	    FileTime lastModified = Files.getLastModifiedTime(backupFilePath);
	    int id;
	    if (backupFile == null) {
		id = md5Backup.insertFileToDB(path.toString(), lastModified.toMillis(), liveMD5, 1);
	    } else {
		id = backupFile.getID();
		md5Backup.updateDBWithFile(path.toString(), lastModified.toMillis(), liveMD5, 1);
	    }
	    md5Backup.getBackupFileMap().put(path.toString(), new BackupFile(id, path, lastModified, liveMD5, true));
	} else {
	    md5Backup.getLogger().info("Date missmatch but MD5 match! Ignoring file: ".concat(path.toAbsolutePath().toString()));
	}
    }
}
