package com.exit.farming.farmland;

import com.exit.farming.FarmingPlugin;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 월드 이름 → FarmlandPolicy 매핑 로더.
 *
 * config.yml의 worlds 섹션에서 로드:
 *
 *   worlds:
 *     world_village:
 *       policy: MANAGED
 *     world_wild:
 *       policy: FORBIDDEN
 *     world_dungeon:
 *       policy: FORBIDDEN
 *     default-policy: FREE
 */
public class WorldPolicyManager {

    private final FarmingPlugin plugin;
    private final Map<String, FarmlandPolicy> worldPolicies = new HashMap<>();
    private FarmlandPolicy defaultPolicy = FarmlandPolicy.FREE;

    public WorldPolicyManager(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        worldPolicies.clear();

        var worldsSection = plugin.getConfig().getConfigurationSection("worlds");
        if (worldsSection == null) {
            plugin.getLogger().info("config.yml 에 worlds 섹션 없음. 모든 월드 FREE 정책 적용.");
            return;
        }

        String defaultStr = worldsSection.getString("default-policy", "FREE");
        try {
            defaultPolicy = FarmlandPolicy.valueOf(defaultStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("잘못된 default-policy: " + defaultStr + ". FREE로 대체.");
            defaultPolicy = FarmlandPolicy.FREE;
        }

        for (String key : worldsSection.getKeys(false)) {
            if ("default-policy".equals(key)) continue;
            ConfigurationSection entry = worldsSection.getConfigurationSection(key);
            if (entry == null) continue;
            String policyStr = entry.getString("policy", "FREE");
            try {
                FarmlandPolicy p = FarmlandPolicy.valueOf(policyStr.toUpperCase(Locale.ROOT));
                worldPolicies.put(key, p);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("월드 " + key + " 정책값 잘못됨: " + policyStr);
            }
        }

        plugin.getLogger().info("월드 정책 로드됨: " + worldPolicies + " (default=" + defaultPolicy + ")");
    }

    public FarmlandPolicy policyOf(World world) {
        if (world == null) return defaultPolicy;
        return worldPolicies.getOrDefault(world.getName(), defaultPolicy);
    }

    public FarmlandPolicy policyOf(String worldName) {
        return worldPolicies.getOrDefault(worldName, defaultPolicy);
    }
}
