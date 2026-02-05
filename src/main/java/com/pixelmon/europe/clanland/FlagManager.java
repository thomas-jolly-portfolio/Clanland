package com.pixelmon.europe.clanland;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FlagManager {

    public enum GlobalFlag {
        BUILD, BREAK, INTERACT, INTERACT_ENTITY, ITEM_USE, PVP, EXPLOSION, CLAIM, COLLIDE, SPAWN,
        CONTAINER,
        FLAGS_CHANGE
    }

    public enum Role {
        visitor,
        member, supermember, ultramember,
        recruiter, strategist,
        officer, moderator, coleader, leader; // TOUS VOS ROLES

        public static Role fromString(String s) {
            try { return Role.valueOf(s.toLowerCase()); } catch (Exception e) { return member; }
        }
    }

    public enum Action { BUILD, BREAK, INTERACT }

    private final ClanlandPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public FlagManager(ClanlandPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flags.yml");
        loadAll();
    }

    public void loadAll() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (!file.exists()) { try { file.createNewFile(); } catch (IOException ignored) {} }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void saveAll() throws IOException { data.save(file); }

    public boolean getGlobal(String clanId, Role role, GlobalFlag flag) {
        String path = "flags." + clanId + "." + role.name() + ".globals." + flag.name();
        if (data.isSet(path)) return data.getBoolean(path);
        String dpath = "flags.default-role-flags." + role.name() + ".globals." + flag.name();
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.isSet(dpath)) return cfg.getBoolean(dpath);
        return false;
    }

    public void setGlobal(String clanId, Role role, GlobalFlag flag, boolean value) {
        data.set("flags." + clanId + "." + role.name() + ".globals." + flag.name(), value);
    }

    public boolean getItemFlag(String clanId, Role role, Action action, String itemKey) {
        String path = "flags." + clanId + "." + role.name() + ".items." + action.name() + "." + itemKey;
        if (data.isSet(path)) return data.getBoolean(path);
        String dpath = "flags.default-role-flags." + role.name() + ".items." + action.name() + "." + itemKey;
        FileConfiguration cfg = plugin.getConfig();
        if (cfg.isSet(dpath)) return cfg.getBoolean(dpath);
        return false;
    }

    public void setItemFlag(String clanId, Role role, Action action, String itemKey, boolean value) {
        data.set("flags." + clanId + "." + role.name() + ".items." + action.name() + "." + itemKey, value);
    }

    public List<String> getAllItemKeysAny() {
        Set<String> keys = new LinkedHashSet<>();
        if (plugin.getConfig().isList("flags.flag-items")) {
            keys.addAll(plugin.getConfig().getStringList("flags.flag-items"));
        }
        return new ArrayList<>(keys);
    }
}