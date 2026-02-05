package com.pixelmon.europe.clanland.hooks;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

public class UClansStorage {

    private final File dir;
    private long lastPlayersTs = -1L;
    private long lastClansTs = -1L;
    private YamlConfiguration players;
    private YamlConfiguration clans;

    public UClansStorage(File dir) {
        this.dir = dir;
        reloadIfNeeded();
    }

    private void reloadIfNeeded() {
        File p = new File(dir, "players.yml");
        File c = new File(dir, "clans.yml");
        if (players == null || p.lastModified() != lastPlayersTs) {
            players = YamlConfiguration.loadConfiguration(p);
            lastPlayersTs = p.lastModified();
        }
        if (clans == null || c.lastModified() != lastClansTs) {
            clans = YamlConfiguration.loadConfiguration(c);
            lastClansTs = c.lastModified();
        }
    }

    private ConfigurationSection clansData() {
        reloadIfNeeded();
        ConfigurationSection data = clans.getConfigurationSection("data");
        return (data != null) ? data : clans;
    }

    private ConfigurationSection playersData() {
        reloadIfNeeded();
        ConfigurationSection data = players.getConfigurationSection("data");
        if (data != null) return data;
        ConfigurationSection legacy = players.getConfigurationSection("players");
        return (legacy != null) ? legacy : players;
    }

    public Optional<String> getClanIdOfPlayer(UUID player) {
        reloadIfNeeded();
        String direct = playersData().getString(player.toString() + ".clan", null);
        if (direct != null && !direct.isEmpty() && !"null".equalsIgnoreCase(direct)) return Optional.of(direct);

        ConfigurationSection data = clansData();
        for (String id : data.getKeys(false)) {
            String leader = data.getString(id + ".leader", data.getString(id + ".owner", ""));
            if (leader != null && leader.equalsIgnoreCase(player.toString())) return Optional.of(id);
            if (data.isList(id + ".members")) {
                for (Object obj : data.getList(id + ".members")) {
                    if (obj != null && obj.toString().equalsIgnoreCase(player.toString())) return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    public Optional<String> getClanTag(UUID clanId) {
        reloadIfNeeded();
        String key = clanId.toString();
        ConfigurationSection data = clansData();
        if (data.isString(key + ".tag")) return Optional.of(data.getString(key + ".tag"));
        if (data.isString(key + ".tag_raw")) return Optional.of(data.getString(key + ".tag_raw"));
        return Optional.empty();
    }

    public Optional<String> getLeaderName(UUID clanId) {
        reloadIfNeeded();
        String key = clanId.toString();
        ConfigurationSection data = clansData();
        String leader = data.getString(key + ".leader", data.getString(key + ".owner", null));
        return (leader != null) ? Optional.of(leader) : Optional.empty();
    }

    public Optional<Integer> getClanLevel(UUID clanId) {
        reloadIfNeeded();
        String key = clanId.toString();
        ConfigurationSection data = clansData();
        if (data.isInt(key + ".level")) return Optional.of(data.getInt(key + ".level"));
        return Optional.of(0);
    }

    // NEW: best-effort lecture du nom dans players.yml
    public Optional<String> getPlayerName(UUID player) {
        reloadIfNeeded();
        String base = player.toString();
        ConfigurationSection pd = playersData();
        if (pd == null) return Optional.empty();
        String[] keys = new String[] { ".name", ".lastName", ".nickname", ".display" };
        for (String k : keys) {
            String v = pd.getString(base + k, null);
            if (v != null && !v.trim().isEmpty()) return Optional.of(v);
        }
        return Optional.empty();
    }
}
