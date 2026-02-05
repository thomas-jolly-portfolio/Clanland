package com.pixelmon.europe.clanland;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;

public class ClanSettingsManager {
    private final File file;
    private YamlConfiguration data;

    public ClanSettingsManager(ClanlandPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "clan_settings.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); }
            catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); } catch (IOException e) { e.printStackTrace(); }
    }

    public String getEnterMessage(String clanId) {
        return data.getString(clanId + ".enter", null); // Null = message par défaut
    }

    public void setEnterMessage(String clanId, String msg) {
        data.set(clanId + ".enter", msg);
        save();
    }

    public String getExitMessage(String clanId) {
        return data.getString(clanId + ".exit", null);
    }

    public void setExitMessage(String clanId, String msg) {
        data.set(clanId + ".exit", msg);
        save();
    }
}