package com.exit.customitems.lamp;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * config.yml 의 설정을 읽어서 타입 안전한 접근자를 제공. reload() 로 런타임 갱신 가능.
 */
public class LampConfig {

    private final Plugin plugin;

    private int[] lineCountWeights = {7, 3};         // 생활램프 기본
    private int[] lineCountWeightsCombat = {4, 6};   // 전투램프 기본 (2줄 비중 ↑)
    private double levelBias = 2.0;
    private Sound rollSound = Sound.BLOCK_ANVIL_USE;
    private float rollVolume = 1.0f;
    private float rollPitch = 1.0f;

    private Set<String> wildernessWorlds = Collections.emptySet();

    // 인챈트 롤 시 범위별 가중치
    private int weightCommon = 6;
    private int weightToolSpecific = 4;

    public LampConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();

        ConfigurationSection lamp = plugin.getConfig().getConfigurationSection("lamp");
        if (lamp == null) {
            plugin.getLogger().warning("config.yml 에 'lamp' 섹션이 없어 기본값을 사용합니다.");
        } else {
            List<Integer> weights = lamp.getIntegerList("line-count-weights");
            if (!weights.isEmpty()) {
                lineCountWeights = weights.stream().mapToInt(Integer::intValue).toArray();
            }
            List<Integer> weightsCombat = lamp.getIntegerList("line-count-weights-combat");
            if (!weightsCombat.isEmpty()) {
                lineCountWeightsCombat = weightsCombat.stream().mapToInt(Integer::intValue).toArray();
            } else {
                // 전용 키 누락 시 일반 line-count-weights 사용 (구버전 호환)
                lineCountWeightsCombat = lineCountWeights;
            }
            levelBias = lamp.getDouble("level-bias", 2.0);

            ConfigurationSection sound = lamp.getConfigurationSection("roll-sound");
            if (sound != null) {
                String name = sound.getString("name", "BLOCK_ANVIL_USE");
                try {
                    rollSound = Sound.valueOf(name);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().log(Level.WARNING, "알 수 없는 사운드: " + name + " (기본값 사용)");
                    rollSound = Sound.BLOCK_ANVIL_USE;
                }
                rollVolume = (float) sound.getDouble("volume", 1.0);
                rollPitch = (float) sound.getDouble("pitch", 1.0);
            }
        }

        List<String> worlds = plugin.getConfig().getStringList("wilderness-worlds");
        wildernessWorlds = new HashSet<>(worlds);

        ConfigurationSection ew = plugin.getConfig().getConfigurationSection("enchant-weights");
        if (ew != null) {
            weightCommon = Math.max(1, ew.getInt("common", 6));
            weightToolSpecific = Math.max(1, ew.getInt("tool-specific", 4));
        }
    }

    public int[] getLineCountWeights() { return lineCountWeights; }
    public int[] getLineCountWeightsCombat() { return lineCountWeightsCombat; }
    public double getLevelBias()       { return levelBias; }
    public Sound getRollSound()        { return rollSound; }
    public float getRollVolume()       { return rollVolume; }
    public float getRollPitch()        { return rollPitch; }

    public Set<String> getWildernessWorlds() { return wildernessWorlds; }

    public int getWeightCommon()        { return weightCommon; }
    public int getWeightToolSpecific()  { return weightToolSpecific; }
}
