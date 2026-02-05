package com.pixelmon.europe.clanland;

import com.pixelmon.europe.clanland.commands.ClanlandCommand;
import com.pixelmon.europe.clanland.gui.GuiManager;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.listener.ClanEventListener;
import com.pixelmon.europe.clanland.listener.MovementListener;
import com.pixelmon.europe.clanland.listener.ProtectionListener;
import com.pixelmon.europe.clanland.papi.ClanlandExpansion;
import com.pixelmon.europe.clanland.util.AutoUnclaimTask;
import com.pixelmon.europe.clanland.util.ItemBuilder;
import com.pixelmon.europe.clanland.util.VisualizerTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ClanlandPlugin extends JavaPlugin {

    private static ClanlandPlugin instance;
    private ClaimManager claimManager;
    private FlagManager flagManager;
    private GuiManager guiManager;
    private AdminModeManager adminModeManager;
    private ClanSettingsManager settingsManager;
    private FlyManager flyManager;
    private HomeManager homeManager; // NOUVEAU
    private UClansHook uclans;
    private DecimalFormat priceFormat;
    private ItemStack visualizerItem;

    public static ClanlandPlugin get() { return instance; }

    public GuiManager getGuiManager() { return guiManager; }

    public ItemStack getVisualizerItem() {
        return visualizerItem != null ? visualizerItem.clone() : null;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        this.priceFormat = new DecimalFormat("#,##0.##");
        this.visualizerItem = createVisualizerItem();

        this.uclans = new UClansHook(this);
        this.adminModeManager = new AdminModeManager();
        this.claimManager = new ClaimManager(this, uclans);
        this.flagManager = new FlagManager(this);
        this.settingsManager = new ClanSettingsManager(this);
        this.homeManager = new HomeManager(this); // INITIALISATION
        this.flyManager = new FlyManager(this, claimManager, uclans);

        this.guiManager = new GuiManager(this, claimManager, flagManager, uclans, adminModeManager);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this, guiManager, claimManager, flagManager, uclans, adminModeManager), this);
        getServer().getPluginManager().registerEvents(new ClanEventListener(this, claimManager), this);
        getServer().getPluginManager().registerEvents(new MovementListener(this, claimManager, uclans, settingsManager, flyManager), this);

        // AJOUT DE homeManager DANS LE CONSTRUCTEUR
        ClanlandCommand cmd = new ClanlandCommand(this, guiManager, claimManager, flagManager, uclans, adminModeManager, settingsManager, flyManager, homeManager);
        getCommand("clanland").setExecutor(cmd);
        getCommand("clanland").setTabCompleter(cmd);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ClanlandExpansion(this, claimManager).register();
        }

        new VisualizerTask(adminModeManager, claimManager, uclans).runTaskTimer(this, 20L, 20L);

        long interval = getConfig().getLong("auto-unclaim.check-interval", 12000L);
        new AutoUnclaimTask(this, claimManager, uclans).runTaskTimerAsynchronously(this, 20L, interval);
    }

    private ItemStack createVisualizerItem() {
        if (!getConfig().getBoolean("item-visualizer.enabled", false)) return null;
        ConfigurationSection s = getConfig().getConfigurationSection("item-visualizer");
        if (s == null) return null;

        Material mat;
        try { mat = Material.valueOf(s.getString("material", "GOLD_HOE").toUpperCase()); }
        catch (Exception e) { mat = Material.GOLD_HOE; }

        List<String> lore = new ArrayList<>();
        for(String l : s.getStringList("lore")) lore.add(color(l));

        return new ItemBuilder(mat, s.getInt("data", 0))
                .name(color(s.getString("name", "&6Baguette")))
                .lore(lore)
                .build();
    }

    public boolean isVisualizerItem(ItemStack s) {
        if (visualizerItem == null || s == null || s.getType() != visualizerItem.getType()) return false;
        if (!s.hasItemMeta() || !visualizerItem.hasItemMeta()) return false;
        return s.getItemMeta().getDisplayName().equals(visualizerItem.getItemMeta().getDisplayName());
    }

    public String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    public String formatMoney(double d) { return getConfig().getString("economy.currency-prefix", "$") + priceFormat.format(d); }

    @Override
    public void onDisable() {
        try { claimManager.saveAll(); flagManager.saveAll(); settingsManager.save(); } catch (Exception e) {}
    }
}