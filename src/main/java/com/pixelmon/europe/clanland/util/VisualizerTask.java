package com.pixelmon.europe.clanland.util;

import com.pixelmon.europe.clanland.AdminModeManager;
import com.pixelmon.europe.clanland.ClaimManager;
import com.pixelmon.europe.clanland.hooks.UClansHook;
import org.bukkit.Bukkit;
import org.bukkit.Effect; // Remettre Effect pour 1.12.2 Spigot si Particle n'existe pas, sinon Particle
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

public class VisualizerTask extends BukkitRunnable {

    private final AdminModeManager admin;
    private final ClaimManager claims;
    private final UClansHook uclans;

    public VisualizerTask(AdminModeManager admin, ClaimManager claims, UClansHook uclans) {
        this.admin = admin;
        this.claims = claims;
        this.uclans = uclans;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!admin.isVisualizing(p.getUniqueId())) continue;

            String playerClan = uclans.getClanUUIDOf(p.getUniqueId()).orElse(null);
            ChunkPos center = ChunkPos.of(p.getLocation());
            int r = 1; // Rayon de visualisation (1 chunk autour)

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    ChunkPos current = new ChunkPos(center.world, center.x + dx, center.z + dz);
                    String owner = claims.getOwnerIdAt(current);

                    if (owner != null) {
                        boolean isOwn = (playerClan != null) && owner.equals(playerClan);
                        Particle particle = isOwn ? Particle.VILLAGER_HAPPY : Particle.FLAME;

                        // Algorithme de contour : On passe l'owner pour comparer aux voisins
                        drawOutlines(p, current, particle, owner);
                    }
                }
            }
        }
    }

    private void drawOutlines(Player p, ChunkPos cp, Particle particle, String owner) {
        World w = p.getWorld();
        if (!w.getName().equals(cp.world)) return;

        int minX = cp.x << 4;
        int minZ = cp.z << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;
        int y = p.getLocation().getBlockY();

        // Vérification des 4 voisins
        // Si le voisin a le MÊME owner, on ne dessine PAS la ligne (car c'est l'intérieur du territoire)

        // 1. NORD (z-1)
        if (!isSameOwner(cp.world, cp.x, cp.z - 1, owner)) {
            for (int x = minX; x <= maxX; x += 2) spawn(p, new Location(w, x, y, minZ), particle);
        }

        // 2. SUD (z+1)
        if (!isSameOwner(cp.world, cp.x, cp.z + 1, owner)) {
            for (int x = minX; x <= maxX; x += 2) spawn(p, new Location(w, x, y, maxZ), particle);
        }

        // 3. OUEST (x-1)
        if (!isSameOwner(cp.world, cp.x - 1, cp.z, owner)) {
            for (int z = minZ; z <= maxZ; z += 2) spawn(p, new Location(w, minX, y, z), particle);
        }

        // 4. EST (x+1)
        if (!isSameOwner(cp.world, cp.x + 1, cp.z, owner)) {
            for (int z = minZ; z <= maxZ; z += 2) spawn(p, new Location(w, maxX, y, z), particle);
        }
    }

    private boolean isSameOwner(String world, int x, int z, String currentOwner) {
        ChunkPos neighbor = new ChunkPos(world, x, z);
        String neighborOwner = claims.getOwnerIdAt(neighbor);
        return Objects.equals(currentOwner, neighborOwner);
    }

    private void spawn(Player p, Location loc, Particle particle) {
        p.spawnParticle(particle, loc.add(0, 1, 0), 1, 0, 0, 0, 0);
    }
}