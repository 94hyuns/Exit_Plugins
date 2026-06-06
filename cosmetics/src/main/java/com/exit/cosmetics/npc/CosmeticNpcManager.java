package com.exit.cosmetics.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * 치장 상인 NPC 관리. Villager 엔티티 기반.
 *
 * <p>NPC 식별: PersistentDataContainer에 COSMETICS_NPC_KEY=1 저장. 서버 재시작 후에도 유지.
 * <p>위치 저장: config.yml의 npc: 섹션. /치장 setnpc 로 관리자가 지정.
 *
 * <p>한 서버에 한 개의 상인만 운영. /치장 setnpc 재호출 시 기존 NPC는 제거.
 */
public class CosmeticNpcManager {

    public static final NamespacedKey NPC_KEY =
            new NamespacedKey("cosmetics", "npc_marker");

    private final JavaPlugin plugin;
    private Location npcLocation;

    public CosmeticNpcManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** config에서 위치 로드. 서버 기동 시 호출. */
    public void loadLocation(FileConfiguration config) {
        if (!config.contains("npc.world")) {
            npcLocation = null;
            return;
        }
        String worldName = config.getString("npc.world");
        World world = worldName != null ? Bukkit.getWorld(worldName) : null;
        if (world == null) {
            plugin.getLogger().warning("[Cosmetics] NPC 위치의 월드 '" + worldName + "'를 찾을 수 없음.");
            npcLocation = null;
            return;
        }
        double x = config.getDouble("npc.x");
        double y = config.getDouble("npc.y");
        double z = config.getDouble("npc.z");
        float yaw = (float) config.getDouble("npc.yaw");
        float pitch = (float) config.getDouble("npc.pitch");
        npcLocation = new Location(world, x, y, z, yaw, pitch);
    }

    /** config에 저장된 위치에 NPC 스폰 (서버 기동 시 호출). */
    public void spawnIfConfigured() {
        if (npcLocation == null) return;
        // 기존 NPC 정리 후 새로 생성
        removeAllNpcs();
        spawnAt(npcLocation);
    }

    /**
     * 플레이어가 /치장 setnpc 를 호출한 위치에 NPC 재배치.
     * 기존 NPC 제거 → config 갱신 → 새 위치에 스폰.
     */
    public void relocateNpc(Location location) {
        removeAllNpcs();
        npcLocation = location.clone();
        saveLocationToConfig();
        spawnAt(npcLocation);
    }

    /** NPC 제거 + config에서 위치 정보 삭제. */
    public void removeNpc() {
        removeAllNpcs();
        npcLocation = null;
        clearLocationInConfig();
    }

    /** 엔티티가 치장 NPC인지 식별. */
    public boolean isCosmeticNpc(Entity entity) {
        if (!(entity instanceof Villager)) return false;
        return entity.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.BYTE);
    }

    public Optional<Location> getLocation() {
        return Optional.ofNullable(npcLocation);
    }

    // ─── 내부 ───

    private void spawnAt(Location loc) {
        Villager villager = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        // Bold/Italic 명시적으로 끔 (메모리 정책: GUI/네임플레이트 Bold 금지)
        villager.customName(Component.text("치장 상인")
                .color(NamedTextColor.AQUA)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setSilent(true);
        villager.setProfession(Villager.Profession.CLERIC);
        villager.setVillagerLevel(5);
        villager.setPersistent(true); // 청크 언로드 시에도 유지
        villager.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void removeAllNpcs() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Villager.class)) {
                if (isCosmeticNpc(entity)) entity.remove();
            }
        }
    }

    private void saveLocationToConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("npc.world", npcLocation.getWorld().getName());
        config.set("npc.x", npcLocation.getX());
        config.set("npc.y", npcLocation.getY());
        config.set("npc.z", npcLocation.getZ());
        config.set("npc.yaw", npcLocation.getYaw());
        config.set("npc.pitch", npcLocation.getPitch());
        saveConfig(config);
    }

    private void clearLocationInConfig() {
        FileConfiguration config = plugin.getConfig();
        config.set("npc", null);
        saveConfig(config);
    }

    private void saveConfig(FileConfiguration config) {
        try {
            File file = new File(plugin.getDataFolder(), "config.yml");
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[Cosmetics] config.yml 저장 실패: " + e.getMessage());
        }
    }
}
