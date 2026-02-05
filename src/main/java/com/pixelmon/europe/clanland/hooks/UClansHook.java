package com.pixelmon.europe.clanland.hooks;

import com.pixelmon.europe.clanland.ClanlandPlugin;
import me.ulrich.clans.Clans;
import me.ulrich.clans.data.ClanData;
import me.ulrich.clans.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin; // IMPORT IMPORTANT

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class UClansHook {

    private final ClanlandPlugin plugin;
    private final Logger log;
    private final UClansStorage storage;
    private final Clans api; // Instance directe de l'API

    public UClansHook(ClanlandPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.storage = new UClansStorage(new File(plugin.getDataFolder().getParentFile(), "UltimateClans/storage/yaml"));

        // Récupération de l'instance API directement
        if (Bukkit.getPluginManager().isPluginEnabled("UltimateClans")) {
            // CORRECTION : Utilisation de la méthode standard Bukkit car Clans.get() n'existe pas en 8.6.0
            this.api = JavaPlugin.getPlugin(Clans.class);
            log.info("Hook UltimateClans (Natif API) activé.");
        } else {
            this.api = null;
            log.warning("UltimateClans introuvable ! Mode fallback activé.");
        }
    }

    public Optional<String> getClanUUIDOf(UUID player) {
        if (api == null) return storage.getClanIdOfPlayer(player);
        // Appel direct API : getClanID retourne Optional<UUID>
        return api.getPlayerAPI().getClanID(player).map(UUID::toString);
    }

    public Optional<String> getRoleName(UUID player) {
        if (api == null) return Optional.empty();
        // Appel direct API : getPlayerData -> getRole()
        Optional<PlayerData> pd = api.getPlayerAPI().getPlayerData(player);
        return pd.map(PlayerData::getRole);
    }

    public Optional<Integer> getClanLevelById(String clanId) {
        if (api == null) return Optional.of(1);
        try {
            UUID uuid = UUID.fromString(clanId);
            // Appel direct : getClan -> getLevel
            return api.getClanAPI().getClan(uuid).map(ClanData::getLevel);
        } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<String> getClanTagById(String clanId) {
        if (api == null) return storage.getClanTag(UUID.fromString(clanId));
        try {
            // Appel direct : getClan -> getTag
            return api.getClanAPI().getClan(UUID.fromString(clanId)).map(ClanData::getTag);
        } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<UUID> getLeaderUUIDByClanId(String clanId) {
        if (api == null) return Optional.empty();
        try {
            // Appel direct : getClan -> getLeader
            return api.getClanAPI().getClan(UUID.fromString(clanId)).map(ClanData::getLeader);
        } catch (Exception e) { return Optional.empty(); }
    }

    public Optional<String> getLeaderNameByClanId(String clanId) {
        return getLeaderUUIDByClanId(clanId).map(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) return p.getName();
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return op.getName() != null ? op.getName() : uuid.toString();
        });
    }

    public Optional<Double> getClanBank(String clanId) {
        if (api == null) return Optional.of(0.0);
        try {
            return api.getClanAPI().getClan(UUID.fromString(clanId)).map(ClanData::getBank);
        } catch (Exception e) { return Optional.empty(); }
    }

    public boolean withdrawClan(String clanId, double amount) {
        if (api == null) return false;
        try {
            UUID uuid = UUID.fromString(clanId);
            Optional<ClanData> clanOpt = api.getClanAPI().getClan(uuid);

            if (clanOpt.isPresent()) {
                ClanData clan = clanOpt.get();
                double current = clan.getBank() != null ? clan.getBank() : 0.0;

                if (current >= amount) {
                    double newVal = current - amount;
                    clan.setBank(newVal);
                    // Sauvegarde explicite via l'API pour être sûr que ça s'enregistre
                    api.getClanAPI().saveClanData(clan, true);
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}