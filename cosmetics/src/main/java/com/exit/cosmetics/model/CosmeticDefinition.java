package com.exit.cosmetics.model;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.Collections;
import java.util.Set;

/**
 * 치장 카탈로그 한 항목의 정의. config.yml에서 로드.
 *
 * <p>공통 필드: id, type, rarity, displayName, description, baseItem, modelData
 * <p>ARMOR 계열 + WEAPON: applicableTo (특정 Material에만 스킨 적용)
 * <p>WING 전용: offsetX/Y/Z, scale
 * <p>TRAIL 전용: particle, particleCount, intervalTicks
 *
 * <p>applicableTo가 비어있으면 "모든 경우에 적용". 비어있지 않으면 화이트리스트.
 * 예: NETHERITE_SWORD 검 스킨 → applicableTo=[DIAMOND_SWORD, NETHERITE_SWORD]
 */
public class CosmeticDefinition {

    private final String id;
    private final CosmeticType type;
    private final CosmeticRarity rarity;
    private final String displayName;
    private final String description;
    private final Material baseItem;
    private final int modelData;

    // ARMOR/WEAPON 전용
    private final Set<Material> applicableTo;

    // ARMOR 전용 — 1.21.4+ EquippableComponent.assetId 값.
    // null 이면 model_data 만 사용 (구식 인벤토리 아이콘만, 몸 렌더링 X).
    private final String assetId;

    // WING 전용
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double scale;

    // TRAIL 전용
    private final Particle particle;
    private final int particleCount;
    private final int intervalTicks;

    public CosmeticDefinition(String id, CosmeticType type, CosmeticRarity rarity,
                              String displayName, String description,
                              Material baseItem, int modelData,
                              Set<Material> applicableTo, String assetId,
                              double offsetX, double offsetY, double offsetZ, double scale,
                              Particle particle, int particleCount, int intervalTicks) {
        this.id = id;
        this.type = type;
        this.rarity = rarity;
        this.displayName = displayName;
        this.description = description;
        this.baseItem = baseItem;
        this.modelData = modelData;
        this.applicableTo = applicableTo != null ? applicableTo : Collections.emptySet();
        this.assetId = assetId;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.scale = scale;
        this.particle = particle;
        this.particleCount = particleCount;
        this.intervalTicks = intervalTicks;
    }

    public String getId() { return id; }
    public CosmeticType getType() { return type; }
    public CosmeticRarity getRarity() { return rarity; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getBaseItem() { return baseItem; }
    public int getModelData() { return modelData; }

    public Set<Material> getApplicableTo() { return applicableTo; }
    public String getAssetId() { return assetId; }

    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public double getScale() { return scale; }

    public Particle getParticle() { return particle; }
    public int getParticleCount() { return particleCount; }
    public int getIntervalTicks() { return intervalTicks; }

    /**
     * 주어진 Material에 이 스킨이 적용 가능한지.
     * applicableTo가 비어있으면 모든 경우에 true.
     */
    public boolean canApplyTo(Material material) {
        if (applicableTo.isEmpty()) return true;
        return applicableTo.contains(material);
    }
}
