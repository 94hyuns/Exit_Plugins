package com.exit.job.manager;

import com.exit.job.model.JobDefinition;
import com.exit.job.model.JobType;
import com.exit.job.model.PerkInfo;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * config.yml 로드.
 *
 * <p>EXP 곡선: 다음 레벨까지 필요 EXP = expBase * (level ^ expExponent).
 * 만렙 도달 시 expForNextLevel = Long.MAX_VALUE.
 *
 * <p>능력은 yml 의 perks 항목에 id/level/name/description 형태로 정의되며 코드 측에서
 * id 매핑으로 효과를 적용한다.
 */
public class JobConfigManager {

    private final JavaPlugin plugin;
    private final EnumMap<JobType, JobDefinition> definitions = new EnumMap<>(JobType.class);

    private double expBase = 100.0;
    private double expExponent = 1.5;
    private int maxLevel = 10;

    private final Set<String> miningAllowedWorlds = new HashSet<>();
    private final EnumMap<Material, Integer> miningOreExp = new EnumMap<>(Material.class);
    private final EnumMap<Material, Material> miningOreToBlock = new EnumMap<>(Material.class);
    private double miningOreToBlockChance = 0.02;
    private double miningOreDropX2Chance = 0.10;
    private double miningExpMultiplier = 1.0;

    private final EnumMap<Material, Integer> farmingCropExp = new EnumMap<>(Material.class);
    private int fishingExpPerCatch = 10;
    private double fishingPremiumMultiplier = 2.0;

    private final Set<Material> mineralStorageWhitelist = new HashSet<>();

    public JobConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        var config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        ConfigurationSection curve = config.getConfigurationSection("exp-curve");
        if (curve != null) {
            expBase = curve.getDouble("base", 100.0);
            expExponent = curve.getDouble("exponent", 1.5);
            maxLevel = curve.getInt("max-level", 10);
            if (expBase <= 0) {
                logger.warning("[Job] exp-curve.base 값이 0 이하 → 100.0으로 보정");
                expBase = 100.0;
            }
            if (maxLevel <= 1) maxLevel = 10;
        }

        definitions.clear();
        ConfigurationSection jobsSection = config.getConfigurationSection("jobs");
        if (jobsSection == null) {
            logger.warning("[Job] config.yml의 jobs 섹션이 없음 — 직업 정의 0개로 시작");
        } else {
            for (JobType type : JobType.values()) {
                ConfigurationSection js = jobsSection.getConfigurationSection(type.id());
                if (js == null) {
                    logger.warning("[Job] " + type.id() + " 정의 누락 — 기본값으로 등록");
                    definitions.put(type, defaultDefinition(type));
                    continue;
                }
                String name = js.getString("name", type.id());
                String desc = js.getString("description", "");
                Material icon = parseMaterial(js.getString("icon"), Material.PAPER, logger);
                List<PerkInfo> perks = new ArrayList<>();
                for (Map<?, ?> raw : js.getMapList("perks")) {
                    Object idObj = raw.get("id");
                    Object lvl = raw.get("level");
                    Object pname = raw.get("name");
                    Object pdesc = raw.get("description");
                    if (idObj == null || !(lvl instanceof Number n) || pname == null) {
                        logger.warning("[Job] " + type.id() + " perks 항목 누락 (id/level/name 필요): " + raw);
                        continue;
                    }
                    perks.add(new PerkInfo(idObj.toString(),
                            n.intValue(),
                            pname.toString(),
                            pdesc == null ? "" : pdesc.toString()));
                }
                perks.sort(Comparator.comparingInt(PerkInfo::level));
                definitions.put(type, new JobDefinition(type, name, desc, icon, List.copyOf(perks)));
            }
        }

        miningAllowedWorlds.clear();
        miningOreExp.clear();
        miningOreToBlock.clear();
        ConfigurationSection mining = config.getConfigurationSection("mining");
        if (mining != null) {
            List<String> worlds = mining.getStringList("allowed-worlds");
            if (worlds.isEmpty()) worlds = List.of("world_wild");
            miningAllowedWorlds.addAll(worlds);

            ConfigurationSection ores = mining.getConfigurationSection("ores");
            if (ores != null) {
                for (String key : ores.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null) {
                        logger.warning("[Job] mining.ores 알 수 없는 Material '" + key + "' — 무시");
                        continue;
                    }
                    int exp = ores.getInt(key, 0);
                    if (exp > 0) miningOreExp.put(m, exp);
                }
            }

            ConfigurationSection toBlock = mining.getConfigurationSection("ore-to-block");
            if (toBlock != null) {
                for (String key : toBlock.getKeys(false)) {
                    Material from = Material.matchMaterial(key);
                    Material to = Material.matchMaterial(toBlock.getString(key, ""));
                    if (from == null || to == null) {
                        logger.warning("[Job] mining.ore-to-block 매핑 무효: " + key + " → " + toBlock.getString(key));
                        continue;
                    }
                    miningOreToBlock.put(from, to);
                }
            }
            miningOreToBlockChance = clamp01(mining.getDouble("ore-to-block-chance", 0.02));
            miningOreDropX2Chance = clamp01(mining.getDouble("ore-drop-x2-chance", 0.10));
            miningExpMultiplier = Math.max(0.0, mining.getDouble("exp-multiplier", 1.0));
        } else {
            miningAllowedWorlds.add("world_wild");
        }

        farmingCropExp.clear();
        ConfigurationSection farming = config.getConfigurationSection("farming");
        if (farming != null) {
            ConfigurationSection crops = farming.getConfigurationSection("crops");
            if (crops != null) {
                for (String key : crops.getKeys(false)) {
                    Material m = Material.matchMaterial(key);
                    if (m == null) {
                        logger.warning("[Job] farming.crops 알 수 없는 Material '" + key + "' — 무시");
                        continue;
                    }
                    int exp = crops.getInt(key, 0);
                    if (exp > 0) farmingCropExp.put(m, exp);
                }
            }
        }

        ConfigurationSection fishing = config.getConfigurationSection("fishing");
        if (fishing != null) {
            fishingExpPerCatch = Math.max(0, fishing.getInt("exp-per-catch", 10));
            fishingPremiumMultiplier = Math.max(1.0, fishing.getDouble("premium-multiplier", 2.0));
        }

        // 광부 보관함 화이트리스트
        mineralStorageWhitelist.clear();
        ConfigurationSection minStorage = config.getConfigurationSection("mineral-storage");
        if (minStorage != null) {
            List<String> wl = minStorage.getStringList("whitelist");
            for (String name : wl) {
                Material m = Material.matchMaterial(name);
                if (m == null) {
                    logger.warning("[Job] mineral-storage.whitelist 알 수 없는 Material '" + name + "' — 무시");
                    continue;
                }
                mineralStorageWhitelist.add(m);
            }
        }

        logger.info("[Job] 설정 로드 완료. " + definitions.size() + "직업 / maxLevel=" + maxLevel
                + " / 광석 " + miningOreExp.size() + "종, 블록변환 " + miningOreToBlock.size()
                + "종 / 작물 " + farmingCropExp.size() + "종");
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private JobDefinition defaultDefinition(JobType type) {
        return new JobDefinition(type, type.id(), "", Material.PAPER, List.of());
    }

    private Material parseMaterial(String name, Material fallback, Logger logger) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        if (m == null) {
            logger.warning("[Job] 알 수 없는 Material '" + name + "' → " + fallback);
            return fallback;
        }
        return m;
    }

    public long expForNextLevel(int currentLevel) {
        if (currentLevel >= maxLevel) return Long.MAX_VALUE;
        double needed = expBase * Math.pow(currentLevel, expExponent);
        return Math.max(1L, Math.round(needed));
    }

    public int maxLevel() { return maxLevel; }

    public JobDefinition getDefinition(JobType type) { return definitions.get(type); }

    public Collection<JobDefinition> all() { return definitions.values(); }

    public Set<String> miningAllowedWorlds() { return miningAllowedWorlds; }
    public EnumMap<Material, Integer> miningOreExp() { return miningOreExp; }
    public EnumMap<Material, Material> miningOreToBlock() { return miningOreToBlock; }
    public double miningOreToBlockChance() { return miningOreToBlockChance; }
    public double miningOreDropX2Chance() { return miningOreDropX2Chance; }
    public double miningExpMultiplier() { return miningExpMultiplier; }

    /** base exp 에 multiplier 적용한 값 (최소 1, round). 미설정 시 base 그대로. */
    public int applyMiningExpMultiplier(int base) {
        if (base <= 0) return 0;
        double scaled = base * miningExpMultiplier;
        return Math.max(1, (int) Math.round(scaled));
    }

    public EnumMap<Material, Integer> farmingCropExp() { return farmingCropExp; }
    public int fishingExpPerCatch() { return fishingExpPerCatch; }
    public double fishingPremiumMultiplier() { return fishingPremiumMultiplier; }
    public Set<Material> mineralStorageWhitelist() { return mineralStorageWhitelist; }
}
