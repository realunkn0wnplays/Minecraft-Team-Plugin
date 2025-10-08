package com.example.teamplugin.commands;

import com.example.teamplugin.Team;
import com.example.teamplugin.TeamManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class TeamCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player p = (Player) sender;
        return handleCommand(p, args);
    }

    private boolean handleCommand(Player p, String[] args) {
        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "top":
                List<Team> top = TeamManager.getTopTeams(10);
                p.sendMessage("§6--- Top Teams ---");
                for (int i = 0; i < top.size(); i++) {
                    Team t = top.get(i);
                    p.sendMessage("§e" + (i + 1) + ". §b" + t.getName()
                            + " §7| Kills: " + t.getKills()
                            + " | Playtime: " + formatSeconds(t.getPlaytimeSeconds())
                            + " | Score: " + t.getScore());
                }
                break;

            case "chat":
                if (args.length == 1) {
                    TeamManager.toggleTeamChat(p);
                } else {
                    String message = String.join(" ", args).substring(5); // skip "chat "
                    TeamManager.sendTeamChat(p, message.trim());
                }
                break;

            default:
                p.sendMessage("§cUnknown subcommand. Use §e/team help");
                break;
        }
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§6--- Team Plugin Help ---");
        p.sendMessage("§a/team help §7- Show this help");
        p.sendMessage("§a/team create [name] §7- Create a team (1 per player)");
        p.sendMessage("§a/team invite [player] §7- Invite a player to your team");
        p.sendMessage("§a/team join [team] §7- Join an invited team");
        p.sendMessage("§a/team kick [player] §7- Kick a team member (owner only)");
        p.sendMessage("§a/team leave §7- Leave your current team");
        p.sendMessage("§a/team transfer [player] §7- Transfer ownership to another member");
        p.sendMessage("§a/team disband [team] §7- Disband your team (owner only)");
        p.sendMessage("§a/team top §7- Show top 10 teams by score");
        p.sendMessage("§a/team chat §7- Toggle team chat mode");
        p.sendMessage("§a/team chat [message] §7- Send a message to team chat");
    }

    private String formatSeconds(long s) {
        long hours = s / 3600;
        long mins = (s % 3600) / 60;
        long secs = s % 60;
        return String.format("%dh %02dm %02ds", hours, mins, secs);
    }
}
