package com.pixelmon.europe.clanland;

import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlyManager implements Listener {

    private final ClanlandPlugin plugin;
    private final ClaimManager claims;
    private final UClansHook uclans;
    private final Set<UUID> flyingPlayers = new HashSet<>();
    private final Set<UUID> noFallDamage = new HashSet<>();

    public FlyManager(ClanlandPlugin plugin, ClaimManager claims, UClansHook uclans) {
        this.plugin = plugin;
        this.claims = claims;
        this.uclans = uclans;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean toggleFly(Player p) {
        if (flyingPlayers.contains(p.getUniqueId())) {
            flyingPlayers.remove(p.getUniqueId());
            disableFly(p);
            return false;
        } else {
            flyingPlayers.add(p.getUniqueId());
            checkAndApplyFly(p, p.getLocation()); // Active immédiatement
            return true;
        }
    }

    // MODIFIÉ: Prend désormais une Location en paramètre pour être prédictif
    public void checkAndApplyFly(Player p, Location loc) {
        if (!flyingPlayers.contains(p.getUniqueId())) return;

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        ChunkPos pos = ChunkPos.of(loc);
        String owner = claims.getOwnerIdAt(pos);
        String playerClan = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);

        // Si c'est SON territoire
        if (owner != null && owner.equals(playerClan)) {
            if (!p.getAllowFlight()) {
                p.setAllowFlight(true);
                p.sendMessage(ChatColor.GREEN + "Vol activé (Zone Clan).");
            }
        } else {
            // Sortie de territoire -> Désactivation immédiate
            disableFly(p);
        }
    }

    private void disableFly(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        if (p.getAllowFlight()) {
            p.setAllowFlight(false);
            p.setFlying(false);
            p.sendMessage(ChatColor.YELLOW + "Vol désactivé (Sortie de zone).");

            // Protection chute 5 secondes
            noFallDamage.add(p.getUniqueId());
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    noFallDamage.remove(p.getUniqueId()), 100L);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (noFallDamage.contains(e.getEntity().getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        flyingPlayers.remove(e.getPlayer().getUniqueId());
        noFallDamage.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (e.getPlayer().getGameMode() != GameMode.CREATIVE) {
            e.getPlayer().setAllowFlight(false);
            e.getPlayer().setFlying(false);
        }
    }
}