package com.exit.world.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 월드 목록 관리, 규칙 적용, 플레이어 이동을 담당한다.
 * 월드 폴더가 없으면 jar 내장 zip에서 자동 복원한다.
 */
public class WorldManager {

    private final Logger logger;
    private final File configFile;
    private final JavaPlugin plugin;
    private final Map<String, WorldConfig> worlds = new LinkedHashMap<>();

    public WorldManager(File dataFolder, Logger logger, JavaPlugin plugin) {
        this.logger = logger;
        this.configFile = new File(dataFolder, "worlds.yml");
        this.plugin = plugin;
    }

    public void initialize() {
        if (!configFile.exists()) {
            logger.warning("[World] worlds.yml이 없습니다.");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        ConfigurationSection section = config.getConfigurationSection("worlds");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection ws = section.getConfigurationSection(key);
            if (ws == null) continue;

            double spawnX = ws.getDouble("spawn-location.x", 0.5);
            double spawnZ = ws.getDouble("spawn-location.z", 0.5);

            WorldConfig wc = new WorldConfig(
                key,
                ws.getString("world-name", key),
                ws.getString("display-name", key),
                ws.getBoolean("monster-spawn", true),
                ws.getBoolean("pvp", false),
                ws.getBoolean("flat", false),
                ws.getString("biome", ""),
                ws.getInt("border-size", 0),
                ws.getDouble("border-center.x", spawnX),
                ws.getDouble("border-center.z", spawnZ),
                spawnX,
                ws.getDouble("spawn-location.y", 65.0),
                spawnZ,
                ws.getString("description", ""),
                ws.getString("warning", ""),
                ws.getString("difficulty", null),
                ws.getString("time-lock", null),
                ws.getBoolean("auto-cleanup-on-empty", false),
                ws.getStringList("blocked-commands")
            );

            worlds.put(key, wc);
            loadWorld(wc);
        }

        logger.info("[World] " + worlds.size() + "개 월드 로드 완료.");
    }

    private void loadWorld(WorldConfig wc) {
        World world = Bukkit.getWorld(wc.getWorldName());

        if (world == null) {
            // 월드 폴더가 없으면 jar 내장 zip에서 복원
            File worldDir = new File(Bukkit.getWorldContainer(), wc.getWorldName());
            if (!worldDir.exists()) {
                restoreWorld(wc.getWorldName());
            }

            WorldCreator creator = new WorldCreator(wc.getWorldName());

            if (wc.isFlat()) {
                creator.type(org.bukkit.WorldType.FLAT);
            }

            // WorldPlugin 의 getDefaultWorldGenerator 명시적 호출.
            // bukkit.yml 에 worlds.<name>.generator 가 없어도 우리의 VoidWorldGenerator 적용되도록.
            org.bukkit.generator.ChunkGenerator gen =
                    plugin.getDefaultWorldGenerator(wc.getWorldName(), null);
            if (gen != null) {
                creator.generator(gen);
                logger.info("[World] " + wc.getWorldName() + " custom generator 적용: " + gen.getClass().getSimpleName());
            }

            world = creator.createWorld();
            logger.info("[World] 월드 로드: " + wc.getWorldName());
        }

        if (world != null) {
            applyRules(world, wc);
            applyBorder(world, wc);
        }
    }

    /**
     * jar 내장 zip(worlds/월드이름.zip)에서 월드 폴더를 복원한다.
     * zip 내부 최상위 폴더명이 월드 이름과 일치해야 한다.
     */
    private void restoreWorld(String worldName) {
        String resourcePath = "worlds/" + worldName + ".zip";
        try (InputStream is = plugin.getResource(resourcePath)) {
            if (is == null) {
                logger.info("[World] 내장 월드 없음: " + resourcePath);
                return;
            }

            File serverRoot = Bukkit.getWorldContainer().getCanonicalFile();
            try (ZipInputStream zis = new ZipInputStream(is)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    File target = new File(serverRoot, entry.getName()).getCanonicalFile();

                    // zip slip 방지
                    if (!target.toPath().startsWith(serverRoot.toPath())) {
                        throw new IOException("잘못된 zip 경로: " + entry.getName());
                    }

                    if (entry.isDirectory()) {
                        target.mkdirs();
                    } else {
                        target.getParentFile().mkdirs();
                        Files.copy(zis, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                    zis.closeEntry();
                }
            }

            logger.info("[World] 월드 복원 완료: " + worldName);
        } catch (IOException e) {
            logger.severe("[World] 월드 복원 실패: " + worldName + " — " + e.getMessage());
        }
    }

    public void applyRules(World world, WorldConfig wc) {
        world.setSpawnFlags(wc.isMonsterSpawn(), true);
        world.setPVP(wc.isPvp());
        if (wc.getDifficulty() != null && !wc.getDifficulty().isBlank()) {
            try {
                org.bukkit.Difficulty diff = org.bukkit.Difficulty.valueOf(wc.getDifficulty().toUpperCase());
                world.setDifficulty(diff);
                logger.info("[World] " + wc.getWorldName() + " difficulty → " + diff);
            } catch (IllegalArgumentException ex) {
                logger.warning("[World] " + wc.getWorldName() + ": 잘못된 difficulty 값 '"
                        + wc.getDifficulty() + "' (PEACEFUL/EASY/NORMAL/HARD)");
            }
        }
        // 시간 고정: time-lock 옵션
        String lock = wc.getTimeLock();
        if (lock != null && !lock.isBlank()) {
            Long ticks = parseTimeLock(lock);
            if (ticks != null) {
                world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setTime(ticks);
                logger.info("[World] " + wc.getWorldName() + " time → " + lock + " (" + ticks + " ticks, daylight cycle off)");
            } else {
                logger.warning("[World] " + wc.getWorldName() + ": 잘못된 time-lock 값 '"
                        + lock + "' (day/night/midnight/noon/숫자)");
            }
        }
    }

    private Long parseTimeLock(String s) {
        switch (s.toLowerCase()) {
            case "day":      return 1000L;
            case "noon":     return 6000L;
            case "night":    return 13000L;
            case "midnight": return 18000L;
            case "sunrise":  return 23000L;
            default:
                try { return Long.parseLong(s.trim()); }
                catch (NumberFormatException e) { return null; }
        }
    }

    /**
     * 월드 보더 설정. border-size > 0 인 경우에만 적용.
     */
    private void applyBorder(World world, WorldConfig wc) {
        if (wc.getBorderSize() <= 0) return;

        WorldBorder border = world.getWorldBorder();
        border.setCenter(wc.getBorderCenterX(), wc.getBorderCenterZ());
        border.setSize(wc.getBorderSize());
        border.setDamageBuffer(0);      // 경계선 넘는 즉시 데미지
        border.setDamageAmount(0.5);    // 틱당 데미지
        border.setWarningDistance(10);  // 10블록 전 경고
        border.setWarningTime(5);

        logger.info("[World] 보더 설정: " + wc.getWorldName()
            + " → " + wc.getBorderSize() + "×" + wc.getBorderSize()
            + " center(" + wc.getBorderCenterX() + ", " + wc.getBorderCenterZ() + ")");
    }

    /** worlds.yml 의 world-name 으로 텔포 (dungeons.yml 에서 사용). */
    public boolean teleportByWorldName(Player player, String worldName) {
        for (WorldConfig wc : worlds.values()) {
            if (wc.getWorldName().equals(worldName)) {
                return teleportTo(player, wc);
            }
        }
        return false;
    }

    private boolean teleportTo(Player player, WorldConfig wc) {
        // 던전/보스 입장: worlds.yml 의 spawn-location 좌표를 그대로 사용 (Y 보정 없음).
        // 외부 맵의 정확한 스폰 지점을 보존하기 위함.
        Location location = wc.getSpawnLocation();
        if (location == null) return false;
        player.teleport(location);
        return true;
    }

    public boolean teleportPlayer(Player player, String worldKey) {
        WorldConfig wc = worlds.get(worldKey);
        if (wc == null) return false;

        var location = wc.getSpawnLocation();
        if (location == null) return false;

        // 안전한 Y 좌표
        int safeY = location.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) + 1;
        location.setY(safeY);

        player.teleport(location);
        return true;
    }

    public WorldConfig getWorldConfig(World world) {
        return worlds.values().stream()
            .filter(wc -> wc.getWorldName().equals(world.getName()))
            .findFirst()
            .orElse(null);
    }

    public Collection<WorldConfig> getAllWorlds() { return worlds.values(); }
    public WorldConfig getConfig(String key) { return worlds.get(key); }
}
