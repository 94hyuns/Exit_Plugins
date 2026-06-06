package com.exit.cosmetics.mount;

import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.registry.CosmeticRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 탈것 카탈로그. mounts.yml 의 mounts: 섹션 로드.
 * 로드 시 동시에 CosmeticRegistry에 합성 CosmeticDefinition을 등록하여
 * 기존 CosmeticProvider.grantCosmetic("mount/&lt;id&gt;") 흐름이 자동으로 동작하게 한다.
 */
public class MountRegistry {

    private final Map<String, MountDefinition> defs = new LinkedHashMap<>();
    private final Logger logger;
    private final CosmeticRegistry cosmeticRegistry;

    public MountRegistry(Logger logger, CosmeticRegistry cosmeticRegistry) {
        this.logger = logger;
        this.cosmeticRegistry = cosmeticRegistry;
    }

    public void load(File mountsFile) {
        defs.clear();
        if (mountsFile == null || !mountsFile.exists()) {
            logger.warning("[Mounts] mounts.yml 파일이 없습니다.");
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(mountsFile);
        load(cfg);
    }

    public void load(FileConfiguration config) {
        defs.clear();
        List<Map<?, ?>> list = config.getMapList("mounts");
        if (list.isEmpty()) {
            logger.warning("[Mounts] mounts.yml 의 mounts: 섹션이 비어있습니다.");
            return;
        }

        int loaded = 0;
        for (Map<?, ?> raw : list) {
            try {
                MountDefinition def = parseEntry(raw);
                if (def != null) {
                    if (defs.containsKey(def.getId())) {
                        logger.warning("[Mounts] id '" + def.getId() + "' 중복 — 기존 항목 덮어씀.");
                    }
                    defs.put(def.getId(), def);
                    cosmeticRegistry.registerSynthetic(
                            def.getOwnershipKey(),
                            def.getDisplayName(),
                            def.getDescription(),
                            def.getRarity(),
                            def.getIconMaterial(),
                            def.getIconModelData()
                    );
                    loaded++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Mounts] 항목 파싱 실패: " + raw, e);
            }
        }
        logger.info("[Mounts] 카탈로그 로드 완료: " + loaded + "개");
    }

    private MountDefinition parseEntry(Map<?, ?> raw) {
        String id = str(raw, "id");
        String mountTypeStr = str(raw, "mount_type");
        String entityStr = str(raw, "entity");
        String rarityStr = str(raw, "rarity");

        if (id == null || mountTypeStr == null || entityStr == null || rarityStr == null) {
            logger.warning("[Mounts] 필수 필드(id/mount_type/entity/rarity) 누락 — skip: " + raw);
            return null;
        }

        MountType mountType = MountType.fromString(mountTypeStr);
        if (mountType == null) {
            logger.warning("[Mounts] 알 수 없는 mount_type '" + mountTypeStr + "' — skip: " + id);
            return null;
        }

        EntityType entityType;
        try {
            entityType = EntityType.valueOf(entityStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("[Mounts] 알 수 없는 entity '" + entityStr + "' — skip: " + id);
            return null;
        }

        CosmeticRarity rarity = CosmeticRarity.fromString(rarityStr);
        if (rarity == null) {
            logger.warning("[Mounts] 알 수 없는 rarity '" + rarityStr + "' — skip: " + id);
            return null;
        }

        String displayName = str(raw, "displayName");
        if (displayName == null) displayName = id;
        String description = str(raw, "description");
        if (description == null) description = "";

        double movementSpeed = toDouble(raw.get("movement_speed"), 0.3);
        double maxHealth = toDouble(raw.get("max_health"), 20.0);

        // 아이콘
        Material iconMaterial = Material.SADDLE;
        int iconModelData = 0;
        Object iconObj = raw.get("icon");
        if (iconObj instanceof Map<?, ?> iconMap) {
            String matStr = str(iconMap, "material");
            if (matStr != null) {
                Material m = Material.matchMaterial(matStr);
                if (m != null) iconMaterial = m;
            }
            iconModelData = (int) toLong(iconMap.get("model_data"), 0);
        }

        String horseColor = str(raw, "color");
        String horseStyle = str(raw, "style");
        int phantomSize = Math.max(0, Math.min(7, (int) toLong(raw.get("phantom_size"), 3)));
        int drawWeight = Math.max(0, (int) toLong(raw.get("weight"), 10));
        String modelEngineId = str(raw, "model_engine_id");

        return new MountDefinition(id, displayName, description, rarity, mountType, entityType,
                movementSpeed, maxHealth, iconMaterial, iconModelData, horseColor, horseStyle,
                phantomSize, drawWeight, modelEngineId);
    }

    public MountDefinition get(String id) {
        return defs.get(id);
    }

    public Collection<MountDefinition> getAll() {
        return Collections.unmodifiableCollection(defs.values());
    }

    public List<MountDefinition> getByRarity(CosmeticRarity rarity) {
        return defs.values().stream()
                .filter(d -> d.getRarity() == rarity)
                .collect(Collectors.toList());
    }

    /** ownershipKey ("mount/&lt;id&gt;") 로 역조회. */
    public MountDefinition findByOwnershipKey(String key) {
        if (key == null || !key.startsWith("mount/")) return null;
        return defs.get(key.substring("mount/".length()));
    }

    // ─── 파싱 유틸 ───

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static long toLong(Object v, long defaultVal) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private static double toDouble(Object v, double defaultVal) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
