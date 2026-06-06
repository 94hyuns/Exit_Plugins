package com.exit.cosmetics.mount;

import com.exit.cosmetics.model.CosmeticRarity;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * 탈것 정의. mounts.yml의 한 항목.
 *
 * <p>공통: id, displayName, description, rarity, mountType, entityType, movementSpeed, maxHealth
 * <p>아이콘(GUI 표시): iconMaterial, iconModelData
 * <p>말 전용 옵션: horseColor, horseStyle (null 가능)
 */
public class MountDefinition {

    private final String id;
    private final String displayName;
    private final String description;
    private final CosmeticRarity rarity;
    private final MountType mountType;
    private final EntityType entityType;
    private final double movementSpeed;
    private final double maxHealth;
    private final Material iconMaterial;
    private final int iconModelData;
    private final String horseColor;   // org.bukkit.entity.Horse.Color name 또는 null
    private final String horseStyle;   // org.bukkit.entity.Horse.Style name 또는 null
    private final int phantomSize;     // 팬텀 크기 (0~7). 0이면 가장 작음. 기본 3.
    private final int drawWeight;      // 같은 등급 내 가중치. 기본 10. 0 이면 뽑기 풀에서 제외.
    private final String modelEngineId; // ModelEngine blueprint ID (선택). 비어있으면 ModelEngine 미사용.

    public MountDefinition(String id, String displayName, String description,
                           CosmeticRarity rarity, MountType mountType, EntityType entityType,
                           double movementSpeed, double maxHealth,
                           Material iconMaterial, int iconModelData,
                           String horseColor, String horseStyle, int phantomSize, int drawWeight,
                           String modelEngineId) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.rarity = rarity;
        this.mountType = mountType;
        this.entityType = entityType;
        this.movementSpeed = movementSpeed;
        this.maxHealth = maxHealth;
        this.iconMaterial = iconMaterial;
        this.iconModelData = iconModelData;
        this.horseColor = horseColor;
        this.horseStyle = horseStyle;
        this.phantomSize = phantomSize;
        this.drawWeight = drawWeight;
        this.modelEngineId = modelEngineId;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public CosmeticRarity getRarity() { return rarity; }
    public MountType getMountType() { return mountType; }
    public EntityType getEntityType() { return entityType; }
    public double getMovementSpeed() { return movementSpeed; }
    public double getMaxHealth() { return maxHealth; }
    public Material getIconMaterial() { return iconMaterial; }
    public int getIconModelData() { return iconModelData; }
    public String getHorseColor() { return horseColor; }
    public String getHorseStyle() { return horseStyle; }
    public int getPhantomSize() { return phantomSize; }
    public int getDrawWeight() { return drawWeight; }
    public String getModelEngineId() { return modelEngineId; }
    public boolean hasModelEngine() { return modelEngineId != null && !modelEngineId.isEmpty(); }

    /** 소유 추적용 합성 cosmetic ID. CosmeticProvider에 grant 시 사용. */
    public String getOwnershipKey() {
        return "mount/" + id;
    }
}
