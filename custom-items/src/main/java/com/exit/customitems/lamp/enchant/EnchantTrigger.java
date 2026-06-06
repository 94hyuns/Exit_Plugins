package com.exit.customitems.lamp.enchant;

/**
 * 인챈트가 언제 발동하는지 구분. 트리거별로 "어느 아이템 슬롯의 인챈트를 조회할지"가 달라진다.
 *
 * <ul>
 *   <li>{@link #ATTACK} - 공격 시, 실제 사용된 주손 무기에 붙은 인챈트만 발동.</li>
 *   <li>{@link #DAMAGED} - 피격 시, 장착 중인 방어구 4부위 중 어디든.</li>
 *   <li>{@link #PASSIVE} - 상시, 장착 중인 방어구 4부위 중 어디든 (예: 체력 칸 증가).</li>
 *   <li>{@link #LIFE_ACTION} - 생활 행위(처치/수확/채광 등) 시, 그 행위에 실제 쓰인 주손 도구에 붙어있을 때만.</li>
 *   <li>{@link #BOW_SHOOT} - 활 발사 시, 주손 활에 붙은 인챈트만 발동.</li>
 * </ul>
 */
public enum EnchantTrigger {
    ATTACK,
    DAMAGED,
    PASSIVE,
    LIFE_ACTION,
    BOW_SHOOT
}
