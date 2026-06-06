package com.example.land.data;

import org.bukkit.Chunk;

import java.util.Objects;

public class ChunkPos {

    private final String world;
    private final int x;
    private final int z;

    public ChunkPos(String world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    public static ChunkPos of(Chunk chunk) {
        return new ChunkPos(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public String getWorld() { return world; }
    public int getX() { return x; }
    public int getZ() { return z; }

    /** SQLite 저장 키 */
    public String toKey() {
        return world + ":" + x + ":" + z;
    }

    public static ChunkPos fromKey(String key) {
        String[] parts = key.split(":");
        return new ChunkPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos other)) return false;
        return x == other.x && z == other.z && Objects.equals(world, other.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + z + ") @ " + world;
    }
}
