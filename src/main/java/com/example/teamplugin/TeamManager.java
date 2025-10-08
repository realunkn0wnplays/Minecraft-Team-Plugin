package com.example.teamplugin;

import com.example.teamplugin.util.EncryptionUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TeamManager {

    private static TeamsPlugin plugin;
    private static File dataFile;

    // Thread-safe structures
    private static final Map<String, Team> teams = new ConcurrentHashMap<>();            // key = lowercase team name
    private static final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();     // player UUID -> teamLowerName
    private static final Map<UUID, Boolean> teamChatToggled = new ConcurrentHashMap<>(); // player UUID -> team chat enabled?
    private static final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();         // player UUID -> join timestamp (ms)

    // -------- Initialization --------
    public static void init(TeamsPlugin pluginInstance) {
        plugin = pluginInstance;
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "teams.yml");
        loadTeams();
    }

    // -------- Team management (basic) --------
    public static synchronized boolean createTeam(Player owner, String name) {
        if (playerTeams.containsKey(owner.getUniqueId())) {
            owner.sendMessage("§cYou already have a team or are a member of one.");
            return false;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (teams.containsKey(key)) {
            owner.sendMessage("§cThat team name is taken.");
            return false;
        }
        Team t = new Team(name, owner.getUniqueId());
        teams.put(key, t);
        playerTeams.put(owner.getUniqueId(), key);
        owner.sendMessage("§aTeam '" + name + "' created.");
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean invitePlayer(Player owner, String targetName) {
        String ownerTeamKey = playerTeams.get(owner.getUniqueId());
        if (ownerTeamKey == null) { owner.sendMessage("§cYou must own a team to invite."); return false; }
        Team team = teams.get(ownerTeamKey);
        if (team == null) { owner.sendMessage("§cYour team was not found."); return false; }
        if (!team.getOwner().equals(owner.getUniqueId())) { owner.sendMessage("§cOnly the owner can invite."); return false; }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            owner.sendMessage("§cPlayer not online or not found.");
            return false;
        }
        if (playerTeams.containsKey(target.getUniqueId())) { owner.sendMessage("§cThat player is already in a team."); return false; }

        team.invite(target.getUniqueId());
        if (target.isOnline()) target.sendMessage("§aYou have been invited to team '" + team.getName() + "'. Use /team join " + team.getName());
        owner.sendMessage("§aInvited " + target.getName() + " to the team.");
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean joinTeam(Player player, String teamName) {
        String key = teamName.toLowerCase(Locale.ROOT);
        Team t = teams.get(key);
        if (t == null) { player.sendMessage("§cThat team does not exist."); return false; }
        if (!t.getInvited().contains(player.getUniqueId())) { player.sendMessage("§cYou are not invited to that team."); return false; }
        if (playerTeams.containsKey(player.getUniqueId())) { player.sendMessage("§cYou are already in a team."); return false; }

        t.addMember(player.getUniqueId());
        playerTeams.put(player.getUniqueId(), key);
        player.sendMessage("§aYou joined team '" + t.getName() + "'.");
        // notify online members
        for (UUID m : t.getMembers()) {
            Player p = Bukkit.getPlayer(m);
            if (p != null && p.isOnline() && !p.getUniqueId().equals(player.getUniqueId())) {
                p.sendMessage("§a" + player.getName() + " joined the team.");
            }
        }
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean kickPlayer(Player owner, String targetName) {
        String ownerTeamKey = playerTeams.get(owner.getUniqueId());
        if (ownerTeamKey == null) { owner.sendMessage("§cYou are not in a team."); return false; }
        Team team = teams.get(ownerTeamKey);
        if (team == null) { owner.sendMessage("§cYour team could not be found."); return false; }
        if (!team.getOwner().equals(owner.getUniqueId())) { owner.sendMessage("§cOnly the owner can kick."); return false; }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { owner.sendMessage("§cPlayer not found or not online."); return false; }
        if (!team.getMembers().contains(target.getUniqueId())) { owner.sendMessage("§cThat player is not in your team."); return false; }
        if (team.getOwner().equals(target.getUniqueId())) { owner.sendMessage("§cYou cannot kick the owner."); return false; }

        team.removeMember(target.getUniqueId());
        playerTeams.remove(target.getUniqueId());
        owner.sendMessage("§aKicked " + target.getName() + " from the team.");
        if (target.isOnline()) target.sendMessage("§cYou were kicked from team '" + team.getName() + "'.");
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean leaveTeam(Player player) {
        String key = playerTeams.get(player.getUniqueId());
        if (key == null) { player.sendMessage("§cYou are not in a team."); return false; }
        Team team = teams.get(key);
        if (team == null) { player.sendMessage("§cYour team no longer exists."); playerTeams.remove(player.getUniqueId()); return false; }
        if (team.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cOwner cannot leave. Transfer ownership or disband the team.");
            return false;
        }
        team.removeMember(player.getUniqueId());
        playerTeams.remove(player.getUniqueId());
        player.sendMessage("§aYou left team '" + team.getName() + "'.");
        for (UUID m : team.getMembers()) {
            Player p = Bukkit.getPlayer(m);
            if (p != null && p.isOnline()) p.sendMessage("§c" + player.getName() + " left the team.");
        }
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean transferOwnership(Player owner, String targetName) {
        String ownerTeamKey = playerTeams.get(owner.getUniqueId());
        if (ownerTeamKey == null) { owner.sendMessage("§cYou are not in a team."); return false; }
        Team team = teams.get(ownerTeamKey);
        if (team == null) { owner.sendMessage("§cYour team could not be found."); return false; }
        if (!team.getOwner().equals(owner.getUniqueId())) { owner.sendMessage("§cOnly the owner can transfer ownership."); return false; }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { owner.sendMessage("§cPlayer not found or not online."); return false; }
        if (!team.getMembers().contains(target.getUniqueId())) { owner.sendMessage("§cThat player is not a member of your team."); return false; }

        team.setOwner(target.getUniqueId());
        playerTeams.put(target.getUniqueId(), ownerTeamKey);
        owner.sendMessage("§aOwnership transferred to " + target.getName() + ".");
        if (target.isOnline()) target.sendMessage("§aYou are now the owner of team '" + team.getName() + "'.");
        saveTeamsAsync();
        return true;
    }

    public static synchronized boolean disbandTeam(Player player, String teamName) {
        String key = teamName.toLowerCase(Locale.ROOT);
        Team team = teams.get(key);
        if (team == null) { player.sendMessage("§cThat team does not exist."); return false; }
        if (!team.getOwner().equals(player.getUniqueId())) { player.sendMessage("§cOnly the owner can disband the team."); return false; }

        for (UUID member : new HashSet<>(team.getMembers())) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) p.sendMessage("§cTeam '" + team.getName() + "' has been disbanded.");
            playerTeams.remove(member);
        }
        teams.remove(key);
        saveTeamsAsync();
        player.sendMessage("§aTeam disbanded.");
        return true;
    }

    // -------- Team chat / leaderboard / tracking --------

    public static void toggleTeamChat(Player player) {
        UUID id = player.getUniqueId();
        boolean now = !teamChatToggled.getOrDefault(id, false);
        teamChatToggled.put(id, now);
        player.sendMessage(now ? "§aTeam chat enabled (your chat will go only to team members)." : "§cTeam chat disabled.");
    }

    public static boolean isTeamChatEnabled(UUID uuid) {
        return teamChatToggled.getOrDefault(uuid, false);
    }

    public static void sendTeamChat(Player sender, String message) {
        Optional<Team> opt = getTeamForPlayer(sender.getUniqueId());
        if (opt.isEmpty()) { sender.sendMessage("§cYou are not in a team."); return; }
        Team t = opt.get();
        String prefix = "§8[§bTeam§8] §r" + sender.getName() + ": ";
        for (UUID member : t.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) p.sendMessage(prefix + message);
        }
    }

    public static List<Team> getTopTeams(int limit) {
        return teams.values().stream()
                .sorted(Comparator.comparingLong(Team::getScore).reversed())
                .limit(limit)
                .toList();
    }

    // -------- Player event handlers (join/quit/death) --------

    public static void playerJoined(Player player) {
        joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public static void playerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = joinTimes.remove(uuid);
        if (start != null) {
            long seconds = (System.currentTimeMillis() - start) / 1000L;
            String teamKey = playerTeams.get(uuid);
            if (teamKey != null) {
                Team t = teams.get(teamKey);
                if (t != null) {
                    t.addPlaytime(seconds);
                    saveTeamsAsync();
                }
            }
        }
    }

    public static void playerDied(Player victim, Player killer) {
        if (killer == null) return;
        UUID k = killer.getUniqueId();
        String teamKey = playerTeams.get(k);
        if (teamKey != null) {
            Team t = teams.get(teamKey);
            if (t != null) {
                t.addKills(1);
                saveTeamsAsync();
            }
        }
    }

    // -------- Persistence: encrypted YAML --------

    public static synchronized void saveTeams() {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            Map<String, Object> tmap = new LinkedHashMap<>();
            for (Team t : teams.values()) {
                tmap.put(t.getName().toLowerCase(Locale.ROOT), t.serialize());
            }
            root.put("teams", tmap);

            String yamlString = new org.yaml.snakeyaml.Yaml().dump(root);
            byte[] yamlBytes = yamlString.getBytes(StandardCharsets.UTF_8);

            String passphrase = firstPassphrase();
            byte[] salt = EncryptionUtil.generateSalt();
            byte[] iv = EncryptionUtil.generateIV();

            SecretKey key = EncryptionUtil.deriveKeyFromPassword(passphrase, salt);
            byte[] cipher = EncryptionUtil.encrypt(yamlBytes, key, iv);

            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("data", Base64.getEncoder().encodeToString(cipher));
            cfg.set("iv", Base64.getEncoder().encodeToString(iv));
            cfg.set("salt", Base64.getEncoder().encodeToString(salt));
            cfg.save(dataFile);
            plugin.getLogger().info("Saved teams (encrypted).");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving teams", e);
        }
    }

    public static void saveTeamsAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, TeamManager::saveTeams);
    }

    public static synchronized void loadTeams() {
        try {
            if (!dataFile.exists()) {
                plugin.getLogger().info("No teams.yml found, starting fresh.");
                return;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
            String dataB64 = cfg.getString("data");
            String ivB64 = cfg.getString("iv");
            String saltB64 = cfg.getString("salt");
            if (dataB64 == null || ivB64 == null || saltB64 == null) {
                plugin.getLogger().warning("teams.yml exists but missing encrypted data - ignoring.");
                return;
            }

            byte[] cipher = Base64.getDecoder().decode(dataB64);
            byte[] iv = Base64.getDecoder().decode(ivB64);
            byte[] salt = Base64.getDecoder().decode(saltB64);

            String passphrase = firstPassphrase();
            SecretKey key = EncryptionUtil.deriveKeyFromPassword(passphrase, salt);
            byte[] yamlBytes = EncryptionUtil.decrypt(cipher, key, iv);
            String yamlString = new String(yamlBytes, StandardCharsets.UTF_8);

            org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
            Object loaded = yaml.load(yamlString);
            if (!(loaded instanceof Map)) {
                plugin.getLogger().warning("Decrypted teams.yml didn't contain expected format.");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> root = (Map<String, Object>) loaded;
            Object teamsObj = root.get("teams");
            if (!(teamsObj instanceof Map)) return;
            @SuppressWarnings("unchecked")
            Map<Object, Object> tmap = (Map<Object, Object>) teamsObj;

            teams.clear();
            playerTeams.clear();
            for (Map.Entry<Object, Object> e : tmap.entrySet()) {
                String keyName = e.getKey().toString();
                @SuppressWarnings("unchecked")
                Map<String, Object> tdata = (Map<String, Object>) e.getValue();
                Team t = Team.deserialize(tdata);
                teams.put(keyName.toLowerCase(Locale.ROOT), t);
                for (UUID member : t.getMembers()) {
                    playerTeams.put(member, keyName.toLowerCase(Locale.ROOT));
                }
            }
            plugin.getLogger().info("Loaded " + teams.size() + " teams from encrypted teams.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed loading teams (possible corrupt file or wrong passphrase). Backing up and starting fresh.", e);
            try {
                File backup = new File(plugin.getDataFolder(), "teams.yml.bak");
                Files.copy(dataFile.toPath(), backup.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Backup of corrupted teams.yml saved as teams.yml.bak");
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to backup corrupted teams.yml", ex);
            }
        }
    }

    // -------- Utility getters --------

    public static Optional<Team> getTeamByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(teams.get(name.toLowerCase(Locale.ROOT)));
    }

    public static Optional<Team> getTeamForPlayer(UUID uuid) {
        String t = playerTeams.get(uuid);
        if (t == null) return Optional.empty();
        return Optional.ofNullable(teams.get(t));
    }

    // -------- Helper: read passphrase from env or config --------
    private static String firstPassphrase() {
        String env = System.getenv("TEAMS_ENCRYPTION_PASS");
        if (env != null && !env.isBlank()) return env;
        return plugin.getConfig().getString("encryption-passphrase", "change_this_passphrase_immediately");
    }
}
