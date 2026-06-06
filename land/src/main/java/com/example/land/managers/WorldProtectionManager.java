package com.example.land.managers;

import com.example.land.LandPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * World 플러그인의 worlds.yml에서 월드별 보호 설정을 읽어온다.
 *
 * protection:
 *   block-break: true/false      — 기본 블록 파괴 허용 여부
 *   block-place: true/false      — 기본 블록 설치 허용 여부
 *   block-interact: true/false   — 기본 컨테이너 상호작용 허용 여부
 *   land-override: true/false    — 청크 소유자가 보호를 무시 가능한지
 */
public class WorldProtectionManager {

    private final LandPlugin plugin;
    private final Map<String, WorldProtection> protections = new HashMap<>();

    public WorldProtectionManager(LandPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * World 플러그인의 worlds.yml을 읽어 보호 설정을 로드한다.
     * (예전엔 Core/worlds.yml 을 참조했으나, 월드 설정은 World 플러그인 소관이라 경로 변경됨)
     */
    public void load() {
        protections.clear();

        File worldFolder = new File(plugin.getDataFolder().getParentFile(), "World");
        File worldsFile = new File(worldFolder, "worlds.yml");

        if (!worldsFile.exists()) {
            plugin.getLogger().warning("World/worlds.yml 파일을 찾을 수 없습니다. 월드 보호가 비활성화됩니다.");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(worldsFile);
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null) return;

        for (String key : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(key);
            if (worldSection == null) continue;

            String worldName = worldSection.getString("world-name");
            if (worldName == null) continue;

            ConfigurationSection prot = worldSection.getConfigurationSection("protection");
            if (prot == null) continue;

            WorldProtection wp = new WorldProtection(
                    prot.getBoolean("block-break", true),
                    prot.getBoolean("block-place", true),
                    prot.getBoolean("block-interact", true),
                    prot.getBoolean("land-override", false)
            );

            protections.put(worldName, wp);
            plugin.getLogger().info("월드 보호 로드: " + worldName
                    + " [break=" + wp.blockBreak + ", place=" + wp.blockPlace
                    + ", interact=" + wp.blockInteract + ", landOverride=" + wp.landOverride + "]");
        }
    }

    /**
     * 해당 월드의 보호 설정을 반환한다.
     * 설정이 없으면 null (= 보호 없음, 바닐라 동작).
     */
    public WorldProtection getProtection(String worldName) {
        return protections.get(worldName);
    }

    /**
     * 월드별 보호 설정 데이터
     */
    public static class WorldProtection {
        public final boolean blockBreak;
        public final boolean blockPlace;
        public final boolean blockInteract;
        public final boolean landOverride;

        public WorldProtection(boolean blockBreak, boolean blockPlace,
                               boolean blockInteract, boolean landOverride) {
            this.blockBreak = blockBreak;
            this.blockPlace = blockPlace;
            this.blockInteract = blockInteract;
            this.landOverride = landOverride;
        }
    }
}
