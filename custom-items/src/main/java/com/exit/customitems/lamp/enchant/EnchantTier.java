package com.exit.customitems.lamp.enchant;

/**
 * 전투 인챈트의 줄 분류. 한 도구의 1번째 줄(BASIC)과 2번째 줄(SET)을 분리해서 롤한다.
 *
 * <ul>
 *   <li>{@link #BASIC} - 무난한 깡스탯 옵션. 1줄 롤 시 항상 이쪽에서 선택.</li>
 *   <li>{@link #SET}   - 시너지/조건부 옵션. 2줄 롤 시 두 번째 줄에 등장.</li>
 * </ul>
 *
 * <p>표시 라벨: 방어구 SET 은 "세트", 무기 SET 은 "유니크" 로 표기. BASIC 은 둘 다 "기본".
 *
 * <p>LIFE 카테고리는 이 분류를 사용하지 않는다 (모두 BASIC 으로 취급되며 평면 롤).
 */
public enum EnchantTier {
    BASIC,
    SET;

    public String displayName(boolean isWeaponEnchant) {
        return switch (this) {
            case BASIC -> "기본";
            case SET -> isWeaponEnchant ? "유니크" : "세트";
        };
    }
}
