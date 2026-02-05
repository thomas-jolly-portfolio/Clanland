package com.pixelmon.europe.clanland;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class HomeManager {

    private final ClanlandPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public HomeManager(ClanlandPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
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

    public void setHome(String clanId, Location loc) {
        String path = "homes." + clanId;
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", loc.getYaw());
        data.set(path + ".pitch", loc.getPitch());
        save();
    }

    public Location getHome(String clanId) {
        String path = "homes." + clanId;
        if (!data.isSet(path + ".world")) return null;

        String wName = data.getString(path + ".world");
        World w = Bukkit.getWorld(wName);
        if (w == null) return null; // Monde non chargé ou supprimé

        double x = data.getDouble(path + ".x");
        double y = data.getDouble(path + ".y");
        double z = data.getDouble(path + ".z");
        float yaw = (float) data.getDouble(path + ".yaw");
        float pitch = (float) data.getDouble(path + ".pitch");

        return new Location(w, x, y, z, yaw, pitch);
    }

    public void deleteHome(String clanId) {
        data.set("homes." + clanId, null);
        save();
    }
}