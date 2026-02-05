package com.pixelmon.europe.clanland.util;

import com.pixelmon.europe.clanland.ClaimManager;
import com.pixelmon.europe.clanland.ClanlandPlugin;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class AutoUnclaimTask extends BukkitRunnable {

    private final ClanlandPlugin plugin;
    private final ClaimManager claims;
    private final UClansHook uclans;

    public AutoUnclaimTask(ClanlandPlugin plugin, ClaimManager claims, UClansHook uclans) {
        this.plugin = plugin;
        this.claims = claims;
        this.uclans = uclans;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("auto-unclaim.enabled", true)) return;

        long daysLimit = plugin.getConfig().getInt("auto-unclaim.days-inactive", 30);
        long millisLimit = daysLimit * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();

        plugin.getLogger().info("Vérification des claims inactifs...");
        int count = 0;

        for (String clanId : claims.getUniqueClanIds()) {
            // Récupère le leader via UClans (UUID)
            UUID leaderId = uclans.getLeaderUUIDByClanId(clanId).orElse(null);

            if (leaderId == null) {
                // Clan supprimé ou buggé -> on nettoie
                claims.removeClaimsForClan(clanId);
                count++;
                continue;
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(leaderId);
            // Si jamais joué (bug?) ou offline depuis trop longtemps
            if (op.hasPlayedBefore() && (now - op.getLastPlayed() > millisLimit)) {
                claims.removeClaimsForClan(clanId);
                plugin.getLogger().info("Suppression claims clan " + clanId + " (Leader inactif depuis " + daysLimit + "j)");
                count++;
            }
        }

        if (count > 0) plugin.getLogger().info(count + " clans nettoyés.");
    }
}