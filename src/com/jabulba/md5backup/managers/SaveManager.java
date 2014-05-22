/**
 * VGScoreboardPing
 * Copyright (C) 2013 Caio Cogliatti Jabulka (Jabulba) <http://www.jabulba.com>
 * 
 * This file is part of VGScoreboardPing.
 * VGScoreboardPing was originally a module of VG Server Manager(All Right Reserved until public release)
 * 
 * VGScoreboardPing is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * VGScoreboardPing is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with VGScoreboardPing.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jabulba.md5backup.managers;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.World;

import com.jabulba.md5backup.MD5Backup;

public class SaveManager extends Thread {
    private final MD5Backup plugin;
    private static SaveManager instance;
    public static SaveManager getInstance() {
        return instance;
    }

    /**
     * True if the save task has been postponed because the backup task was running<br>
     * False otherwise
     */
    private boolean postponed = false;
    private volatile boolean running = false;

    public SaveManager(MD5Backup plugin) {
	this.plugin = plugin;
	instance = this;
    }

    @Override
    public void run() {
	if (running) {
	    return;
	}
	running = true;

	if (plugin.getBackupThread().isRunning()) {
	    running = false;
	    if (postponed) {
		return;
	    }
	    postponed = true;
	    plugin.getLogger().info("Backup task is still running.  Autosave will be postponed until it finishes.");
	    return;
	}
	postponed = false;

	saveAll();
	running = false;
    }

    public void saveAll() {
	// plugin.getServer().broadcastMessage("Synchronizing server data with disk...");
	plugin.getLogger().info("Saving players.");
	plugin.getServer().savePlayers();
	saveWorlds();
	// plugin.getServer().broadcastMessage("Data Synchronization complete at ".concat(new Date().toGMTString()));
    }

    public void saveWorlds() {
	// call syncronous taks!
	plugin.getLogger().info("Saving worlds.");
	//disableWorldSave();
	for (World world : plugin.getServer().getWorlds()) {
	    world.save();// TODO: syncronize with server
	}
	//enableWorldSave();
    }

    private boolean worldsSaveDisabled = false;
    private List<World> saveDisabledWorlds = new ArrayList<World>();

    public void disableWorldSave() {
	if (worldsSaveDisabled) {
	    plugin.getLogger().info("Worlds save is already disabled.");
	    return;
	}
	saveDisabledWorlds.clear();// in case something went wrong
	worldsSaveDisabled = true;
	List<World> worldsList = plugin.getServer().getWorlds();
	for (World world : worldsList) {
	    // syncronous task autoSaveWorld(World world, boolean enableSave)
	    // where enableSave is if the world save is disabled. Then Enable and Disable it.
	    if (world.isAutoSave()) {
		world.setAutoSave(false);
		saveDisabledWorlds.add(world);
	    }
	}
	plugin.getLogger().info("Worlds save disabled.");
    }

    public void enableWorldSave() {
	if (!worldsSaveDisabled) {
	    plugin.getLogger().info("Worlds save is already enabled.");
	    return;
	}
	for (World world : saveDisabledWorlds) {
	    // syncronous task autoSaveWorld(World world, boolean enableSave)
	    // where enableSave is if the world save is disabled. Then Enable and Disable it.
	    world.setAutoSave(true);
	    saveDisabledWorlds.remove(world);
	}
	worldsSaveDisabled = false;
	plugin.getLogger().info("Worlds save enabled.");
    }

}
