package com.example.teamplugin;


import com.example.teamplugin.commands.TeamCommand;
import com.example.teamplugin.listeners.PlayerListener;
import org.bukkit.plugin.java.JavaPlugin;


public final class TeamsPlugin extends JavaPlugin {
private static TeamsPlugin instance;


@Override
public void onEnable() {
instance = this;
saveDefaultConfig();


// Initialize manager
TeamManager.init(this);


// Register commands & listeners
if (getCommand("team") != null) getCommand("team").setExecutor(new TeamCommand());
getServer().getPluginManager().registerEvents(new PlayerListener(), this);


// Start autosave
int interval = getConfig().getInt("autosave-interval", 60);
getServer().getScheduler().runTaskTimerAsynchronously(this, TeamManager::saveTeams, interval * 20L, interval * 20L);


getLogger().info("TeamsPlugin enabled (1.21.9 compatible)");
}


@Override
public void onDisable() {
getLogger().info("Saving teams...");
TeamManager.saveTeams();
getLogger().info("TeamsPlugin disabled");
}


public static TeamsPlugin getInstance() {
return instance;
}
}