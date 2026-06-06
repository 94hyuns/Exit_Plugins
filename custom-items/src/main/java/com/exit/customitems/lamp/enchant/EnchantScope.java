package com.exit.customitems.lamp.enchant;

/**
 * 인챈트의 적용 범위 분류. 롤 가중치 산정에 사용.
 *
 * <ul>
 *   <li>{@link #COMMON} - 해당 램프 카테고리의 모든 도구에 적용 가능 (예: 생활도구 전체).
 *       상대적으로 자주 롤되도록 가중치 상향.</li>
 *   <li>{@link #TOOL_SPECIFIC} - 특정 도구 하나 또는 소수에만 적용 가능 (예: 곡괭이 전용).
 *       상대적으로 드물게 롤되도록 가중치 하향.</li>
 * </ul>
 *
 * 실제 가중치 값은 config.yml 의 enchant-weights 섹션에서 조정.
 */
public enum EnchantScope {
    COMMON,
    TOOL_SPECIFIC
}
