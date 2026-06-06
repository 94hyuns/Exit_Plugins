package com.exit.farming.water;

import com.exit.farming.FarmingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * 배치된 스프링쿨러 위치 + 등급 저장소. plugins/Farming/sprinklers.yml.
 *
 * 형식:
 * <pre>
 * sprinklers:
 *   - {world: world_village, x: 10, y: 64, z: -3, tier: COPPER}
 *   - ...
 * </pre>
 *
 * 주기적 flush(dirty 시) + onDisable 에서 final flush.
 */
public class SprinklerStore {

    public record Sprinkler(Location loc, WaterTier tier) {
        public String key() {
            return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        }
    }

    private final FarmingPlugin plugin;
    private final File file;
    /** key → Sprinkler */
    private final Map<String, Sprinkler> sprinklers = new HashMap<>();
    private boolean dirty = false;

    public SprinklerStore(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sprinklers.yml");
    }

    public void load() {
        sprinklers.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = yml.getMapList("sprinklers");
        int loaded = 0, skipped = 0;
        for (Map<?, ?> m : raw) {
            try {
                String worldName = String.valueOf(m.get("world"));
                int x = ((Number) m.get("x")).intValue();
                int y = ((Number) m.get("y")).intValue();
                int z = ((Number) m.get("z")).intValue();
                String tierStr = String.valueOf(m.get("tier"));
                WaterTier tier = WaterTier.valueOf(tierStr);
                World w = Bukkit.getWorld(worldName);
                if (w == null) { skipped++; continue; }
                Location loc = new Location(w, x, y, z);
                Sprinkler s = new Sprinkler(loc, tier);
                sprinklers.put(s.key(), s);
                loaded++;
            } catch (Exception e) {
                skipped++;
            }
        }
        plugin.getLogger().info("스프링쿨러 " + loaded + "개 로드"
                + (skipped > 0 ? " (잘못된 항목 " + skipped + "개 무시)" : "") + ".");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>(sprinklers.size());
        for (Sprinkler s : sprinklers.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", s.loc.getWorld().getName());
            m.put("x", s.loc.getBlockX());
            m.put("y", s.loc.getBlockY());
            m.put("z", s.loc.getBlockZ());
            m.put("tier", s.tier.name());
            list.add(m);
        }
        yml.set("sprinklers", list);
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "sprinklers.yml 저장 실패", e);
        }
    }

    public void flush() {
        if (dirty) save();
    }

    public void register(Location loc, WaterTier tier) {
        Sprinkler s = new Sprinkler(loc.clone(), tier);
        sprinklers.put(s.key(), s);
        dirty = true;
    }

    public Sprinkler unregister(Location loc) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        Sprinkler removed = sprinklers.remove(key);
        if (removed != null) dirty = true;
        return removed;
    }

    public Sprinkler get(Location loc) {
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        return sprinklers.get(key);
    }

    public Collection<Sprinkler> all() {
        return Collections.unmodifiableCollection(sprinklers.values());
    }

    public int size() {
        return sprinklers.size();
    }
}
