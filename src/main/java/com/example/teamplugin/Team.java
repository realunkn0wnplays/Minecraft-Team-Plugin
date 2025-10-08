package com.example.teamplugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class Team {
    private String name;
    private UUID owner;
    private final Set<UUID> members = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<UUID> invited = Collections.synchronizedSet(new LinkedHashSet<>());
    private int kills = 0;
    private long playtimeSeconds = 0L;

    public Team(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
    }

    // default constructor
    public Team() {}

    public String getName() { return name; }
    public UUID getOwner() { return owner; }
    public Set<UUID> getMembers() { return members; }
    public Set<UUID> getInvited() { return invited; }
    public int getKills() { return kills; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }

    public synchronized void addMember(UUID uuid) { members.add(uuid); invited.remove(uuid); }
    public synchronized void removeMember(UUID uuid) { members.remove(uuid); }
    public synchronized void invite(UUID uuid) { invited.add(uuid); }
    public synchronized void uninvite(UUID uuid) { invited.remove(uuid); }
    public synchronized void addKills(int k) { kills += k; }
    public synchronized void addPlaytime(long seconds) { playtimeSeconds += seconds; }

    public synchronized void setOwner(UUID newOwner) {
        this.owner = newOwner;
        members.add(newOwner);
    }

    public long getScore() {
        return (long) kills * 100 + (playtimeSeconds / 60);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("owner", owner.toString());
        m.put("members", members.stream().map(UUID::toString).collect(Collectors.toList()));
        m.put("invited", invited.stream().map(UUID::toString).collect(Collectors.toList()));
        m.put("kills", kills);
        m.put("playtimeSeconds", playtimeSeconds);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static Team deserialize(Map<String, Object> map) {
        Team t = new Team();
        t.name = (String) map.get("name");
        t.owner = UUID.fromString((String) map.get("owner"));

        List<String> membersList = (List<String>) map.getOrDefault("members", new ArrayList<>());
        for (String s : membersList) t.members.add(UUID.fromString(s));

        List<String> invitedList = (List<String>) map.getOrDefault("invited", new ArrayList<>());
        for (String s : invitedList) t.invited.add(UUID.fromString(s));

        t.kills = ((Number) map.getOrDefault("kills", 0)).intValue();
        t.playtimeSeconds = ((Number) map.getOrDefault("playtimeSeconds", 0L)).longValue();
        return t;
    }

    public String getDisplayName() {
        return "§b" + name + " §7(" + members.size() + " members)";
    }

    public List<String> getOnlineMemberNames() {
        return members.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.toList());
    }
}
