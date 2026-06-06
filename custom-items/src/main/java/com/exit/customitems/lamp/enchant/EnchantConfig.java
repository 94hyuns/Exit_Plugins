package com.exit.customitems.lamp.enchant;

import com.exit.customitems.util.NumUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.List;

/**
 * {@code enchants.yml} 로더 — 각 인챈트의 ValueSpec 수치를 yml에서 읽는다.
 *
 * <p>yml 구조:
 * <pre>
 * enchants:
 *   &lt;enchantId&gt;:
 *     &lt;valueName&gt;:
 *       min:  &lt;double&gt;
 *       max:  &lt;double&gt;
 *       step: &lt;double&gt;
 * </pre>
 *
 * <p>읽기 실패 / 검증 오류 시 호출자가 넘긴 기본값으로 폴백.
 * 따라서 yml 항목이 통째로 없거나 일부만 잘못 적혀도 안전.
 */
public class EnchantConfig {

    private final Plugin plugin;
    private FileConfiguration config = new YamlConfiguration();

    public EnchantConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "enchants.yml");
        if (!file.exists()) {
            plugin.saveResource("enchants.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 인챈트 단일 ValueSpec 읽기. 항목 누락이나 검증 실패 시 기본값 사용.
     *
     * @param enchantId yml 의 {@code enchants.&lt;id&gt;} 키 (예: "attack_power")
     * @param valueName 그 아래 서브키 (예: "attack" / "heal" / "cooldown")
     * @param defMin    기본 min (yml 누락 시 이 값)
     * @param defMax    기본 max
     * @param defStep   기본 step
     */
    /**
     * 단일 double 값 읽기. SET 인챈트의 per_level / base 같은 단순 계수 조회용.
     * 누락 시 기본값.
     */
    public double readDouble(String enchantId, String key, double def) {
        String path = "enchants." + enchantId + "." + key;
        return config.getDouble(path, def);
    }

    /** double 리스트 읽기 (예: 레벨별 효과 테이블). 누락/빈 리스트 시 기본값. */
    public List<Double> readDoubleList(String enchantId, String key, List<Double> def) {
        String path = "enchants." + enchantId + "." + key;
        if (!config.isList(path)) return def;
        List<Double> list = config.getDoubleList(path);
        return list.isEmpty() ? def : list;
    }

    /**
     * 방어구 SET 풀 가중치 ({@code armor_set_weights.&lt;id&gt;.&lt;column&gt;}).
     * column = "normal" 또는 "mutation". 누락 시 기본값.
     */
    public int readSetWeight(String enchantId, String column, int def) {
        String path = "armor_set_weights." + enchantId + "." + column;
        return config.getInt(path, def);
    }

    /** /skilllist /skillinfo 표시명 ({@code skills.&lt;id&gt;.name}). 누락 시 기본값. */
    public String readSkillName(String id, String def) {
        return config.getString("skills." + id + ".name", def);
    }

    /** /skilllist /skillinfo 한 줄 요약 ({@code skills.&lt;id&gt;.short}). 누락 시 기본값. */
    public String readSkillShort(String id, String def) {
        return config.getString("skills." + id + ".short", def);
    }

    public ValueSpec readSpec(String enchantId, String valueName,
                              double defMin, double defMax, double defStep) {
        String path = "enchants." + enchantId + "." + valueName;
        ConfigurationSection sub = config.getConfigurationSection(path);

        double min  = sub != null ? sub.getDouble("min",  defMin)  : defMin;
        double max  = sub != null ? sub.getDouble("max",  defMax)  : defMax;
        double step = sub != null ? sub.getDouble("step", defStep) : defStep;
        // bias 미지정 → NaN (global default 사용). 명시 시 그 값.
        double bias = (sub != null && sub.isSet("bias")) ? sub.getDouble("bias") : Double.NaN;

        try {
            return new ValueSpec(
                    NumUtil.toStored(min),
                    NumUtil.toStored(max),
                    NumUtil.toStored(step),
                    bias);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[CustomItems] enchants.yml 값 오류 (" + path + "): "
                    + "min=" + min + " max=" + max + " step=" + step + " — 기본값 사용");
            return new ValueSpec(
                    NumUtil.toStored(defMin),
                    NumUtil.toStored(defMax),
                    NumUtil.toStored(defStep));
        }
    }
}
