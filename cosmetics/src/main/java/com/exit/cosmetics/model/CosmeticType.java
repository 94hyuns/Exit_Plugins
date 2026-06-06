package com.exit.cosmetics.model;

import org.bukkit.inventory.EquipmentSlot;

/**
 * 치장 타입. DB의 equipped_cosmetics.slot 컬럼에 문자열로 저장된다.
 * 한 플레이어는 타입당 1개씩만 장착 가능.
 *
 * <p>구현 방식:
 * <ul>
 *   <li>HAT/CHEST/LEGS/FEET: {@code Player.sendEquipmentChange}로 viewer들에게 가짜 장비 패킷 (3인칭만)</li>
 *   <li>WEAPON: 실제 주손 ItemStack의 CustomModelData 수정 (1인칭까지 표시). 원본은 PDC에 백업.</li>
 *   <li>WING: ItemDisplay passenger</li>
 *   <li>TRAIL: 스케줄러 파티클</li>
 * </ul>
 */
public enum CosmeticType {
    HAT(EquipmentSlot.HEAD),
    CHEST(EquipmentSlot.CHEST),
    LEGS(EquipmentSlot.LEGS),
    FEET(EquipmentSlot.FEET),
    WEAPON(EquipmentSlot.HAND),
    WING(null),
    TRAIL(null),
    /**
     * 탈것. 옷장이 아닌 별도 /ride GUI에서 관리되며 "장착"이 아닌 "소환" 개념.
     * 합성 CosmeticDefinition으로 등록되어 소유 추적만 CosmeticProvider 재사용.
     */
    MOUNT(null);

    private final EquipmentSlot equipmentSlot;

    CosmeticType(EquipmentSlot slot) {
        this.equipmentSlot = slot;
    }

    /** 장비 슬롯 매핑. WING/TRAIL은 null (슬롯 개념 없음). */
    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    /** sendEquipmentChange 계열 (방어구 4종). */
    public boolean isArmorSlot() {
        return this == HAT || this == CHEST || this == LEGS || this == FEET;
    }

    public static CosmeticType fromString(String s) {
        if (s == null) return null;
        try {
            return CosmeticType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
