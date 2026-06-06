package com.exit.rewards.config;

import com.exit.rewards.model.MobReward;
import com.exit.rewards.model.RewardItem;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Level;

/**
 * config.yml에서 몹별 보상 테이블을 로드/파싱.
 * reload 시 내부 맵을 통째로 교체.
 */
public class RewardConfig {

    public enum RewardTarget { KILLER_ONLY, ALL_DAMAGERS }
    public enum DropMode { DROP, GIVE }

    private final JavaPlugin plugin;
    private final Map<String, MobReward> rewards = new HashMap<>();

    private double proximityRange = 0;
    private RewardTarget rewardTarget = RewardTarget.KILLER_ONLY;
    private DropMode dropMode = DropMode.DROP;

    public RewardConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** config.yml 로드. 없으면 jar 내장본을 꺼냄. */
    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration cfg = plugin.getConfig();

        rewards.clear();
        ConfigurationSection rewardsSec = cfg.getConfigurationSection("rewards");
        if (rewardsSec == null) {
            plugin.getLogger().warning("[DungeonRewards] rewards 섹션이 없음.");
        } else {
            for (String mobId : rewardsSec.getKeys(false)) {
                ConfigurationSection mob = rewardsSec.getConfigurationSection(mobId);
                if (mob == null) continue;
                try {
                    rewards.put(mobId, parseMobReward(mobId, mob));
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "[DungeonRewards] " + mobId + " 파싱 실패: " + e.getMessage(), e);
                }
            }
        }

        ConfigurationSection settings = cfg.getConfigurationSection("settings");
        if (settings != null) {
            proximityRange = settings.getDouble("proximityRange", 0);
            rewardTarget = parseEnum(settings.getString("rewardTarget"), RewardTarget.class, RewardTarget.KILLER_ONLY);
            dropMode = parseEnum(settings.getString("dropMode"), DropMode.class, DropMode.DROP);
        }

        plugin.getLogger().info("[DungeonRewards] " + rewards.size() + "개 몹 보상 로드됨.");
    }

    private MobReward parseMobReward(String mobId, ConfigurationSection sec) {
        ConfigurationSection money = sec.getConfigurationSection("money");
        int min = money != null ? money.getInt("min", 0) : 0;
        int max = money != null ? money.getInt("max", min) : min;

        String msg = sec.getString("messageOnKill");

        List<RewardItem> dropList = new ArrayList<>();
        List<Map<?, ?>> dropsRaw = sec.getMapList("drops");
        for (Map<?, ?> map : dropsRaw) {
            try {
                dropList.add(parseDrop(map));
            } catch (Exception e) {
                plugin.getLogger().warning("[DungeonRewards] " + mobId + "의 드롭 항목 파싱 실패: " + e.getMessage());
            }
        }
        return new MobReward(mobId, min, max, msg, dropList);
    }

    private RewardItem parseDrop(Map<?, ?> map) {
        String providerName = map.get("provider") != null ? String.valueOf(map.get("provider")) : null;
        String typeId = map.get("type") != null ? String.valueOf(map.get("type")) : null;

        Material mat = null;
        if (providerName == null) {
            String itemName = String.valueOf(map.get("item"));
            mat = Material.matchMaterial(itemName);
            if (mat == null) throw new IllegalArgumentException("Unknown material: " + itemName);
        } else if (typeId == null) {
            throw new IllegalArgumentException("provider 사용 시 type 필수 (provider=" + providerName + ")");
        }

        Object amountObj = map.get("amount");
        int[] amtRange = parseRange(String.valueOf(amountObj));

        double chance = map.get("chance") instanceof Number n ? n.doubleValue() : 1.0;
        String displayName = map.get("displayName") != null ? String.valueOf(map.get("displayName")) : null;

        return new RewardItem(mat, providerName, typeId, amtRange[0], amtRange[1], chance, displayName);
    }

    private int[] parseRange(String s) {
        if (s.contains("-")) {
            // "1-3" 형태
            String[] parts = s.split("-", 2);
            int a = Integer.parseInt(parts[0].trim());
            int b = Integer.parseInt(parts[1].trim());
            return new int[]{Math.min(a, b), Math.max(a, b)};
        }
        int v = Integer.parseInt(s.trim());
        return new int[]{v, v};
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <E extends Enum<E>> E parseEnum(String s, Class<E> enumClass, E fallback) {
        if (s == null) return fallback;
        try {
            return Enum.valueOf(enumClass, s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public MobReward getReward(String mobId) {
        return rewards.get(mobId);
    }

    public Collection<MobReward> allRewards() {
        return rewards.values();
    }

    public double getProximityRange() { return proximityRange; }
    public RewardTarget getRewardTarget() { return rewardTarget; }
    public DropMode getDropMode() { return dropMode; }
}
