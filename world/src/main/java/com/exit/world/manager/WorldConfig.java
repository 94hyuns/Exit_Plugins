package com.exit.world.manager;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * 월드별 규칙과 설정을 담는 데이터 모델.
 * worlds.yml에서 읽어와서 WorldManager가 관리한다.
 */
public class WorldConfig {

    private final String key;
    private final String worldName;
    private final String displayName;
    private final boolean monsterSpawn;
    private final boolean pvp;
    private final boolean flat;
    private final String biome;
    private final int borderSize;
    private final double borderCenterX;
    private final double borderCenterZ;
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;
    private final String description;
    private final String warning;
    private final String difficulty;   // PEACEFUL/EASY/NORMAL/HARD or null (unchanged)
    private final String timeLock;     // "day"/"night"/숫자/null
    private final boolean autoCleanupOnEmpty;  // 빈 월드 10초 후 MM mob 일괄 정리
    private final java.util.List<String> blockedCommands;  // 이 월드에서 차단할 명령어 (slash 제외)

    public WorldConfig(String key, String worldName, String displayName,
                       boolean monsterSpawn, boolean pvp,
                       boolean flat, String biome, int borderSize,
                       double borderCenterX, double borderCenterZ,
                       double spawnX, double spawnY, double spawnZ,
                       String description, String warning,
                       String difficulty, String timeLock,
                       boolean autoCleanupOnEmpty,
                       java.util.List<String> blockedCommands) {
        this.key = key;
        this.worldName = worldName;
        this.displayName = displayName;
        this.monsterSpawn = monsterSpawn;
        this.pvp = pvp;
        this.flat = flat;
        this.biome = biome;
        this.borderSize = borderSize;
        this.borderCenterX = borderCenterX;
        this.borderCenterZ = borderCenterZ;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.spawnZ = spawnZ;
        this.description = description;
        this.warning = warning;
        this.difficulty = difficulty;
        this.timeLock = timeLock;
        this.autoCleanupOnEmpty = autoCleanupOnEmpty;
        // 소문자 + slash 제거로 정규화해 보관
        java.util.List<String> norm = new java.util.ArrayList<>();
        if (blockedCommands != null) {
            for (String c : blockedCommands) {
                if (c == null) continue;
                String s = c.trim().toLowerCase();
                if (s.startsWith("/")) s = s.substring(1);
                if (!s.isEmpty()) norm.add(s);
            }
        }
        this.blockedCommands = java.util.Collections.unmodifiableList(norm);
    }

    /**
     * 이 월드의 스폰 Location을 반환한다.
     * 월드가 로드되지 않았으면 null.
     */
    public Location getSpawnLocation() {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, spawnX, spawnY, spawnZ);
    }

    public String getKey() { return key; }
    public String getWorldName() { return worldName; }
    public String getDisplayName() { return displayName; }
    public boolean isMonsterSpawn() { return monsterSpawn; }
    public boolean isPvp() { return pvp; }
    public boolean isFlat() { return flat; }
    public String getBiome() { return biome; }
    public int getBorderSize() { return borderSize; }
    public double getBorderCenterX() { return borderCenterX; }
    public double getBorderCenterZ() { return borderCenterZ; }
    public double getSpawnX() { return spawnX; }
    public double getSpawnY() { return spawnY; }
    public double getSpawnZ() { return spawnZ; }
    public String getDescription() { return description; }
    public String getWarning() { return warning; }
    public String getDifficulty() { return difficulty; }
    public String getTimeLock() { return timeLock; }
    public boolean isAutoCleanupOnEmpty() { return autoCleanupOnEmpty; }
    public java.util.List<String> getBlockedCommands() { return blockedCommands; }
}
