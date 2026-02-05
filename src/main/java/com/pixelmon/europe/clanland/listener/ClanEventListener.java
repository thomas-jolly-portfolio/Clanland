package com.pixelmon.europe.clanland.listener;

import com.pixelmon.europe.clanland.ClaimManager;
import com.pixelmon.europe.clanland.ClanlandPlugin;
import me.ulrich.clans.events.ClanDeleteEvent; // Import direct API
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ClanEventListener implements Listener {

    private final ClanlandPlugin plugin;
    private final ClaimManager claims;

    public ClanEventListener(ClanlandPlugin plugin, ClaimManager claims) {
        this.plugin = plugin;
        this.claims = claims;
    }

    // Priorité MONITOR : On laisse UClans faire son travail, puis on nettoie juste après.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClanDelete(ClanDeleteEvent e) {
        if (e.getClanID() != null) {
            String clanId = e.getClanID().toString();

            // On compte combien de claims ils avaient pour les logs
            int count = claims.getClaimCountForClan(clanId);

            if (count > 0) {
                // NETTOYAGE CRITIQUE : Supprime tous les claims du fichier
                claims.removeClaimsForClan(clanId);

                plugin.getLogger().info("[Clanland] Clan dissous (" + clanId + "). " + count + " claims ont été libérés automatiquement.");
            }
        }
    }
}