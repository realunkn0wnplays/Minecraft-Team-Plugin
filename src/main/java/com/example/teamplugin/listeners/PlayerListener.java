package com.example.teamplugin.listeners;


import com.example.teamplugin.TeamManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;


public class PlayerListener implements Listener {
@EventHandler
public void onJoin(PlayerJoinEvent e) {
Player p = e.getPlayer();
TeamManager.playerJoined(p);
}


@EventHandler
public void onQuit(PlayerQuitEvent e) {
Player p = e.getPlayer();
TeamManager.playerQuit(p);
}


@EventHandler
public void onDeath(PlayerDeathEvent e) {
Player victim = e.getEntity();
Player killer = victim.getKiller();
TeamManager.playerDied(victim, killer);
}


@EventHandler
public void onChat(AsyncPlayerChatEvent e) {
Player p = e.getPlayer();
try {
if (TeamManager.isTeamChatEnabled(p.getUniqueId())) {
e.setCancelled(true);
TeamManager.sendTeamChat(p, e.getMessage());
}
} catch (Exception ex) {
// Fail safe: don't break chat
p.sendMessage("§cAn error occurred processing team chat. Check server logs.");
ex.printStackTrace();
}
}
}