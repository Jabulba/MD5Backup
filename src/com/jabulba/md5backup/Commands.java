package com.jabulba.md5backup;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class Commands implements CommandExecutor {

    private MD5Backup plugin;

    public Commands(MD5Backup plugin) {
	this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
	if (args.length < 1) {
	    sender.sendMessage("Not enough arguments!");
	    return false;
	}

	if (!sender.hasPermission("md5backup.admin")) {
	    sender.sendMessage("Insuficient permissions.");
	    return true;
	}

	if (args[0].equalsIgnoreCase("bk")) {
	    plugin.createBackup();
	    return true;
	} else if (args[0].equalsIgnoreCase("file")) {
	    if (args.length < 2) {
		sender.sendMessage("Specify file path! ex: world/level.dat");
		return false;
	    }
	    plugin.backupFileInfo(args[1]);
	    return true;
	}
	return false;
    }
}
