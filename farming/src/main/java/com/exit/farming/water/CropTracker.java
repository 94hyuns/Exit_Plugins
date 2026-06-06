package com.exit.farming.water;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * 위치별 작물 추적. 씨앗 심을 때 (Location → Crop) 기록 → 성장/본밀 이벤트 시 직접 조회.
 *
 * <p><b>왜 필요한가</b>: BlockGrowEvent / BlockFertilizeEvent 시점에 block.getBlockData() 가
 * 이미 새 age 로 갱신된 상태를 반환 (Paper 1.21+). age 매칭으로 seedling 식별하면 한 칸 옆
 * 작물에 매칭돼 잘못된 mature 적용. 본밀의 경우 increment 가 가변(+2~+5)이라 더 심각.
 *
 * <p>이 tracker 가 "여기 어떤 작물을 심었나" 를 명시 기록하므로 age 추측 없이 정확한 작물 mature 적용.
 *
 * <p>저장: plugins/Farming/plants.yml, key=world:x:y:z, value=crop id.
 */
public class CropTracker {

    private final FarmingPlugin plugin;
    private final File file;
    private final Map<String, Crop> plants = new HashMap<>();
    private boolean dirty = false;

    public CropTracker(FarmingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plants.yml");
    }

    public void load() {
        plants.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("plants");
        if (sec == null) return;
        int loaded = 0, skipped = 0;
        for (String key : sec.getKeys(false)) {
            Crop c = Crop.byId(sec.getString(key));
            if (c != null) { plants.put(key, c); loaded++; }
            else skipped++;
        }
        plugin.getLogger().info("작물 추적 " + loaded + "개 로드"
                + (skipped > 0 ? " (잘못된 항목 " + skipped + "개 무시)" : "") + ".");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, Crop> e : plants.entrySet()) {
            yml.set("plants." + e.getKey(), e.getValue().id());
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "plants.yml 저장 실패", ex);
        }
    }

    public void flush() {
        if (dirty) save();
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }

    public void put(Block b, Crop c) {
        plants.put(key(b), c);
        dirty = true;
    }

    public Crop get(Block b) {
        return plants.get(key(b));
    }

    public void remove(Block b) {
        if (plants.remove(key(b)) != null) dirty = true;
    }

    public int size() {
        return plants.size();
    }
}
