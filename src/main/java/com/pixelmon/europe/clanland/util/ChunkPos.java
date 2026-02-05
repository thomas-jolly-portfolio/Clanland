package com.pixelmon.europe.clanland.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

public class ChunkPos {
    public final String world;
    public final int x;
    public final int z;

    public ChunkPos(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkPos of(Location loc) {
        return new ChunkPos(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static ChunkPos of(Chunk chunk) {
        return new ChunkPos(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String toKey() {
        return world + ";" + x + ";" + z;
    }

    public static ChunkPos fromKey(String key) {
        try {
            String[] p = key.split(";");
            return new ChunkPos(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos other = (ChunkPos) o;
        return this.x == other.x && this.z == other.z && this.world.equals(other.world);
    }

    @Override
    public int hashCode() {
        int result = world.hashCode();
        result = 31 * result + x;
        result = 31 * result + z;
        return result;
    }
}