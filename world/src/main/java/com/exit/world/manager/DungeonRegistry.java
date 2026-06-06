package com.exit.world.manager;

import com.exit.world.boss.BossArenaConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * dungeons.yml 로드 및 던전/보스 항목 + 던전 마스터 NPC 위치 보관.
 */
public class DungeonRegistry {

    private final JavaPlugin plugin;
    private final File configFile;
    private final Map<String, DungeonEntry> entries = new LinkedHashMap<>();

    private Location npcLocation;
    private String npcSkin = "Steve";
    private String npcName = "§4던전 마스터";

    public DungeonRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "dungeons.yml");
    }

    public void load() {
        entries.clear();
        if (!configFile.exists()) {
            plugin.saveResource("dungeons.yml", false);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);

        // NPC 위치
        ConfigurationSection npc = cfg.getConfigurationSection("npc");
        if (npc != null) {
            String worldName = npc.getString("world", "world_village");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                npcLocation = new Location(world,
                        npc.getDouble("x"),
                        npc.getDouble("y"),
                        npc.getDouble("z"),
                        (float) npc.getDouble("yaw"),
                        (float) npc.getDouble("pitch"));
            }
            npcSkin = npc.getString("skin", "Steve");
            npcName = npc.getString("name", "§4던전 마스터");
        }

        // 던전 항목들
        ConfigurationSection section = cfg.getConfigurationSection("dungeons");
        if (section == null) {
            plugin.getLogger().warning("[World] dungeons.yml 에 'dungeons' 섹션 없음");
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection ds = section.getConfigurationSection(key);
            if (ds == null) continue;

            DungeonEntry.Tab tab;
            try {
                tab = DungeonEntry.Tab.valueOf(ds.getString("tab", "DUNGEON").toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[World] dungeons.yml: " + key + " 의 tab 값 잘못됨, DUNGEON 으로 처리");
                tab = DungeonEntry.Tab.DUNGEON;
            }

            Material icon;
            try {
                icon = Material.valueOf(ds.getString("icon", "PAPER").toUpperCase());
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("[World] dungeons.yml: " + key + " 의 icon 값 잘못됨, PAPER 로 처리");
                icon = Material.PAPER;
            }

            String worldName = ds.getString("world-name", "");

            // boss-arena 섹션 (옵션)
            BossArenaConfig bossArena = null;
            ConfigurationSection ba = ds.getConfigurationSection("boss-arena");
            if (ba != null) {
                bossArena = new BossArenaConfig(
                        key,
                        worldName,
                        ba.getString("boss-mob", ""),
                        ba.getDouble("boss-x", 0),
                        ba.getDouble("boss-y", 64),
                        ba.getDouble("boss-z", 0),
                        ba.getInt("countdown-sec", 30),
                        ba.getInt("cooldown-sec", 600),
                        ba.getInt("grace-sec", 10),
                        ba.getInt("kill-reward-min", 0),
                        ba.getInt("kill-reward-max", 0),
                        ba.getDouble("damage-multiplier", 1.0)
                );
            }

            entries.put(key, new DungeonEntry(
                    key,
                    tab,
                    worldName,
                    ds.getString("display-name", key),
                    icon,
                    ds.getLong("cost", 0),
                    bossArena,
                    ds.getInt("time-limit-sec", 0),
                    ds.getInt("gui-slot", -1),
                    ds.getString("description", "")
            ));
        }

        plugin.getLogger().info("[World] DungeonRegistry loaded " + entries.size() + " entries");
    }

    public List<DungeonEntry> getByTab(DungeonEntry.Tab tab) {
        List<DungeonEntry> out = new ArrayList<>();
        for (DungeonEntry e : entries.values()) {
            if (e.tab() == tab) out.add(e);
        }
        return out;
    }

    public DungeonEntry getByKey(String key) {
        return entries.get(key);
    }

    /** worldName 으로 첫 매칭되는 DungeonEntry 반환 (없으면 null). */
    public DungeonEntry findByWorldName(String worldName) {
        for (DungeonEntry e : entries.values()) {
            if (e.worldName().equals(worldName)) return e;
        }
        return null;
    }

    public Location getNpcLocation() { return npcLocation == null ? null : npcLocation.clone(); }
    public String getNpcSkin() { return npcSkin; }
    public String getNpcName() { return npcName; }
}
