package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * Farming 의 물뿌리개 / 스프링쿨러 아이템 생성 / 식별 API.
 *
 * <p>Farming 플러그인이 구현체를 {@code ServiceRegistry.register(WaterToolProvider.class, ...)} 로 등록.
 * 다른 플러그인 (Shop 등) 이 Farming 의 물 도구 아이템을 발급하려 할 때 이 인터페이스로 접근.
 *
 * <p>typeId 표준값:
 * <ul>
 *   <li>{@code wateringcan_copper}, {@code wateringcan_iron}, {@code wateringcan_diamond}</li>
 *   <li>{@code sprinkler_copper}, {@code sprinkler_iron}, {@code sprinkler_diamond}</li>
 * </ul>
 */
public interface WaterToolProvider {
    ItemStack createTool(String typeId, int amount);
    boolean isWateringCan(ItemStack item);
    boolean isSprinkler(ItemStack item);
}
