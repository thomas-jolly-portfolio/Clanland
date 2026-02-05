package com.pixelmon.europe.clanland;

import com.pixelmon.europe.clanland.hooks.UClansHook;
import com.pixelmon.europe.clanland.util.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimManager {

    private final ClanlandPlugin plugin;
    private final UClansHook uclans;
    private final Map<ChunkPos, String> claims = new ConcurrentHashMap<>();
    private final Map<String, Integer> extraClaims = new ConcurrentHashMap<>();
    private final File file;

    // NOUVEAU: Set pour suivre les clans uniques pour l'auto-unclaim
    public Set<String> getUniqueClanIds() {
        return new HashSet<>(claims.values());
    }

    public ClaimManager(ClanlandPlugin plugin, UClansHook hook) {
        this.plugin = plugin;
        this.uclans = hook;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        loadAll();
    }

    public void loadAll() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (!file.exists()) {
            try { file.createNewFile(); } catch (IOException ignored) {}
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        claims.clear();
        extraClaims.clear();

        ConfigurationSection cs = data.getConfigurationSection("claims");
        if (cs != null) {
            for (String key : cs.getKeys(false)) {
                String clanId = cs.getString(key);
                ChunkPos pos = ChunkPos.fromKey(key);
                if (pos != null && clanId != null) claims.put(pos, clanId);
            }
        }
        ConfigurationSection ex = data.getConfigurationSection("extras");
        if (ex != null) {
            for (String id : ex.getKeys(false)) {
                extraClaims.put(id, ex.getInt(id, 0));
            }
        }
    }

    // MODIFIÉ : Sauvegarde Asynchrone
    public void saveAll() {
        // Snapshot des données pour le thread async
        Map<String, String> claimsSnapshot = new HashMap<>();
        for (Map.Entry<ChunkPos, String> e : claims.entrySet()) {
            claimsSnapshot.put(e.getKey().toKey(), e.getValue());
        }
        Map<String, Integer> extrasSnapshot = new HashMap<>(extraClaims);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration out = new YamlConfiguration();
            for (Map.Entry<String, String> e : claimsSnapshot.entrySet()) {
                out.set("claims." + e.getKey(), e.getValue());
            }
            for (Map.Entry<String, Integer> e : extrasSnapshot.entrySet()) {
                out.set("extras." + e.getKey(), e.getValue());
            }
            try { out.save(file); } catch (IOException e) { e.printStackTrace(); }
        });
    }

    // --- Logic ---

    public boolean isContiguous(String clanId, ChunkPos pos) {
        if (getClaimCountForClan(clanId) == 0) return true;
        int[][] offsets = {{1,0}, {-1,0}, {0,1}, {0,-1}};
        for (int[] off : offsets) {
            ChunkPos neighbor = new ChunkPos(pos.world, pos.x + off[0], pos.z + off[1]);
            String neighborOwner = claims.get(neighbor);
            if (clanId.equals(neighborOwner)) return true;
        }
        return false;
    }

    public void removeClaimsForClan(String clanId) {
        if (clanId == null) return;
        boolean changed = false;
        Iterator<Map.Entry<ChunkPos, String>> it = claims.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().equals(clanId)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) saveAll();
    }

    public boolean isWorldAllowed(World world) {
        List<String> allowed = plugin.getConfig().getStringList("allowed-worlds");
        if (allowed == null || allowed.isEmpty()) return true;
        return allowed.contains(world.getName());
    }

    public String getOwnerIdAt(ChunkPos pos) { return claims.get(pos); }

    public boolean respectsMinDistance(String clanId, ChunkPos target) {
        int min = plugin.getConfig().getInt("min-distance-between-clans", 5);
        if (min <= 0) return true;
        for (Map.Entry<ChunkPos, String> e : claims.entrySet()) {
            if (e.getValue() == null || e.getValue().equals(clanId)) continue;
            ChunkPos other = e.getKey();
            if (!other.world.equals(target.world)) continue;
            int dx = Math.abs(other.x - target.x);
            int dz = Math.abs(other.z - target.z);
            if (Math.max(dx, dz) < min) return false;
        }
        return true;
    }

    public boolean addClaim(String clanId, ChunkPos pos) {
        if (claims.containsKey(pos)) return false;
        claims.put(pos, clanId);
        return true;
    }

    public boolean removeClaim(ChunkPos pos) { return claims.remove(pos) != null; }

    public boolean declaimById(String clanId, ChunkPos pos, boolean admin) {
        String owner = claims.get(pos);
        if (owner == null) return false;
        if (!admin && !owner.equals(clanId)) return false;
        return removeClaim(pos);
    }

    public int getClaimCountForClan(String clanId) {
        int i = 0;
        for (String id : claims.values()) { if (clanId.equals(id)) i++; }
        return i;
    }

    public int getExtraClaims(String clanId) { return extraClaims.getOrDefault(clanId, 0); }

    public void modifyExtraClaims(String clanId, int newVal, boolean absolute) {
        if (absolute) extraClaims.put(clanId, Math.max(0, newVal));
        else extraClaims.put(clanId, Math.max(0, getExtraClaims(clanId) + newVal));
    }

    public int getMaxClaims(String clanId) {
        int level = uclans.getClanLevelById(clanId).orElse(0);
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection sec = cfg.getConfigurationSection("claim-limits-by-level");
        int base = cfg.getInt("claim-limits-by-level.default", 10);
        int lim = base;
        if (sec != null && sec.isInt(String.valueOf(level))) {
            lim = sec.getInt(String.valueOf(level));
        }
        return lim + getExtraClaims(clanId);
    }

    public double nextPriceFor(String clanId) {
        int owned = getClaimCountForClan(clanId);
        double base = plugin.getConfig().getDouble("economy.base", 125.0D);
        double mult = plugin.getConfig().getDouble("economy.multiplier", 1.25D);
        return base * Math.pow(mult, owned);
    }
}