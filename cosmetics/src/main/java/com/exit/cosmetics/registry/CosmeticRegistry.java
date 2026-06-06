package com.exit.cosmetics.registry;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.model.CosmeticType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 치장 카탈로그 등록소. config.yml의 cosmetics: 섹션을 로드한다.
 * 잘못된 항목은 로그만 남기고 skip.
 */
public class CosmeticRegistry {

    private final Map<String, CosmeticDefinition> defs = new LinkedHashMap<>();
    private final Logger logger;

    public CosmeticRegistry(Logger logger) {
        this.logger = logger;
    }

    public void load(FileConfiguration config) {
        defs.clear();
        List<Map<?, ?>> list = config.getMapList("cosmetics");
        if (list.isEmpty()) {
            logger.warning("[Cosmetics] config.yml의 cosmetics: 섹션이 비어있습니다.");
            return;
        }

        int loaded = 0;
        for (Map<?, ?> raw : list) {
            try {
                CosmeticDefinition def = parseEntry(raw);
                if (def != null) {
                    if (defs.containsKey(def.getId())) {
                        logger.warning("[Cosmetics] id '" + def.getId() + "' 중복 — 기존 항목 덮어씀.");
                    }
                    defs.put(def.getId(), def);
                    loaded++;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Cosmetics] 항목 파싱 실패: " + raw, e);
            }
        }
        logger.info("[Cosmetics] 카탈로그 로드 완료: " + loaded + "개");
    }

    private CosmeticDefinition parseEntry(Map<?, ?> raw) {
        String id = str(raw, "id");
        String typeStr = str(raw, "type");
        String rarityStr = str(raw, "rarity");
        String displayName = str(raw, "displayName");
        String description = str(raw, "description");
        String baseItemStr = str(raw, "base_item");

        if (id == null || typeStr == null || rarityStr == null || baseItemStr == null) {
            logger.warning("[Cosmetics] 필수 필드(id/type/rarity/base_item) 누락 — skip: " + raw);
            return null;
        }

        CosmeticType type = CosmeticType.fromString(typeStr);
        if (type == null) {
            logger.warning("[Cosmetics] 알 수 없는 type '" + typeStr + "' — skip: " + id);
            return null;
        }

        CosmeticRarity rarity = CosmeticRarity.fromString(rarityStr);
        if (rarity == null) {
            logger.warning("[Cosmetics] 알 수 없는 rarity '" + rarityStr + "' — skip: " + id);
            return null;
        }

        Material baseItem = Material.matchMaterial(baseItemStr);
        if (baseItem == null) {
            logger.warning("[Cosmetics] 알 수 없는 base_item '" + baseItemStr + "' — skip: " + id);
            return null;
        }

        int modelData = (int) num(raw, "model_data", 0);

        // ARMOR/WEAPON 전용: applicable_to
        Set<Material> applicableTo = parseApplicableTo(raw, id);

        // ARMOR 전용: asset_id (1.21.4+ EquippableComponent 의 model/asset_id)
        String assetId = str(raw, "asset_id");

        // WING 전용
        double ox = 0, oy = 0, oz = 0, scale = 1.0;
        if (type == CosmeticType.WING) {
            Object offsetObj = raw.get("offset");
            if (offsetObj instanceof List<?> offsetList && offsetList.size() == 3) {
                ox = toDouble(offsetList.get(0));
                oy = toDouble(offsetList.get(1));
                oz = toDouble(offsetList.get(2));
            }
            Object scaleObj = raw.get("scale");
            if (scaleObj instanceof Number n) scale = n.doubleValue();
        }

        // TRAIL 전용
        Particle particle = null;
        int particleCount = 0;
        int intervalTicks = 5;
        if (type == CosmeticType.TRAIL) {
            String particleStr = str(raw, "particle");
            if (particleStr != null) {
                try {
                    particle = Particle.valueOf(particleStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    logger.warning("[Cosmetics] 알 수 없는 particle '" + particleStr + "' — skip: " + id);
                    return null;
                }
            } else {
                logger.warning("[Cosmetics] TRAIL인데 particle 필드 누락 — skip: " + id);
                return null;
            }
            particleCount = (int) num(raw, "count", 3);
            intervalTicks = (int) num(raw, "interval_ticks", 5);
        }

        if (displayName == null) displayName = id;
        if (description == null) description = "";

        return new CosmeticDefinition(id, type, rarity, displayName, description,
                baseItem, modelData, applicableTo, assetId,
                ox, oy, oz, scale,
                particle, particleCount, intervalTicks);
    }

    private Set<Material> parseApplicableTo(Map<?, ?> raw, String id) {
        Object v = raw.get("applicable_to");
        if (!(v instanceof List<?> list) || list.isEmpty()) return Collections.emptySet();
        Set<Material> result = EnumSet.noneOf(Material.class);
        for (Object o : list) {
            if (o == null) continue;
            Material m = Material.matchMaterial(o.toString());
            if (m == null) {
                logger.warning("[Cosmetics] applicable_to의 알 수 없는 Material '" + o + "' — skip (" + id + ")");
                continue;
            }
            result.add(m);
        }
        return result;
    }

    /**
     * 합성 등록 — config.yml 카탈로그가 아닌 외부 시스템(예: MountRegistry)이
     * 자기만의 yml에서 정의한 항목을 소유 추적용으로 등록하는 통로.
     * type은 MOUNT로 고정. 기존 항목과 ID 충돌 시 덮어씀.
     */
    public void registerSynthetic(String id, String displayName, String description,
                                  CosmeticRarity rarity, Material iconMaterial, int iconModelData) {
        if (id == null || rarity == null || iconMaterial == null) return;
        CosmeticDefinition def = new CosmeticDefinition(
                id, CosmeticType.MOUNT, rarity,
                displayName != null ? displayName : id,
                description != null ? description : "",
                iconMaterial, iconModelData,
                Collections.emptySet(), null,
                0, 0, 0, 1.0,
                null, 0, 0
        );
        defs.put(id, def);
    }

    // ─── 조회 ───

    public CosmeticDefinition get(String id) {
        return defs.get(id);
    }

    public Collection<CosmeticDefinition> getAll() {
        return Collections.unmodifiableCollection(defs.values());
    }

    public List<CosmeticDefinition> getByRarity(CosmeticRarity rarity) {
        return defs.values().stream()
                .filter(d -> d.getRarity() == rarity)
                .collect(Collectors.toList());
    }

    public List<CosmeticDefinition> getByType(CosmeticType type) {
        return defs.values().stream()
                .filter(d -> d.getType() == type)
                .collect(Collectors.toList());
    }

    // ─── 파싱 유틸 ───

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static long num(Map<?, ?> m, String key, long defaultVal) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
