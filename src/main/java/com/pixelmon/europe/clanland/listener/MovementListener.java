package com.pixelmon.europe.clanland.listener;

import com.pixelmon.europe.clanland.ClaimManager;
import com.pixelmon.europe.clanland.ClanSettingsManager;
import com.pixelmon.europe.clanland.ClanlandPlugin;
import com.pixelmon.europe.clanland.FlyManager;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

public class MovementListener implements Listener {

    private final ClaimManager claims;
    private final UClansHook uclans;
    private final ClanSettingsManager settings;
    private final FlyManager flyManager;

    public MovementListener(ClanlandPlugin plugin, ClaimManager claims, UClansHook uclans, ClanSettingsManager settings, FlyManager flyManager) {
        this.claims = claims;
        this.uclans = uclans;
        this.settings = settings;
        this.flyManager = flyManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Optimisation : on ne calcule que si on change de bloc (évite le spam rotation tête)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();

        // CHECK FLY (Avec la position FUTURE 'getTo')
        // Si le joueur entre dans un chunk sauvage, le fly se coupe instantanément ici
        flyManager.checkAndApplyFly(p, e.getTo());

        // --- TITLES (Reste inchangé, mais on vérifie le changement de chunk) ---
        if (e.getFrom().getChunk() == e.getTo().getChunk()) return;

        ChunkPos from = ChunkPos.of(e.getFrom());
        ChunkPos to = ChunkPos.of(e.getTo());

        String ownerFrom = claims.getOwnerIdAt(from);
        String ownerTo = claims.getOwnerIdAt(to);

        if (!Objects.equals(ownerFrom, ownerTo)) {
            if (ownerFrom != null && ownerTo == null) {
                String customExit = settings.getExitMessage(ownerFrom);
                if (customExit != null) sendTitle(p, "", ChatColor.translateAlternateColorCodes('&', customExit));
                else sendTitle(p, "", "§a§lZone Sauvage");
            }
            else if (ownerTo != null) {
                String clanTag = uclans.getClanTagById(ownerTo).orElse("Clan Inconnu");
                String color = "§c";
                String pClan = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
                if (pClan != null && pClan.equals(ownerTo)) color = "§9";

                String customEnter = settings.getEnterMessage(ownerTo);
                if (customEnter != null) sendTitle(p, color + "§l" + clanTag, ChatColor.translateAlternateColorCodes('&', customEnter));
                else sendTitle(p, color + "§l" + clanTag, color + "Territoire de clan");
            }
        }
    }

    private void sendTitle(Player p, String title, String subtitle) {
        p.sendTitle(title, subtitle, 10, 40, 10);
    }
}