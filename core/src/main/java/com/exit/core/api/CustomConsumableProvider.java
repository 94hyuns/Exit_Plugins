package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * 커스텀 소비 아이템(램프 외) 제공 인터페이스.
 *
 * <p>CustomItems 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop / NPC상점 등 다른 플러그인은 이 인터페이스만으로 소비 아이템을 다룬다.
 * LampProvider 와 별개의 카테고리 — 램프는 도구에 인챈트를 부여하는 시스템이고,
 * 여기는 사용 시 그 자체로 소모되는 아이템(인벤세이브, 음식, 기타 토큰 등).
 *
 * <p>등록된 typeId 예시:
 * <ul>
 *   <li>{@code "INV_SAVE"}  — 사망 시 인벤토리/경험치 보호 토큰</li>
 *   <li>{@code "DDUZZONKU"} — 두쫀쿠(커스텀 음식)</li>
 * </ul>
 *
 * <p>사용 예시:
 * <pre>{@code
 * CustomConsumableProvider cc = ServiceRegistry.get(CustomConsumableProvider.class).orElse(null);
 * if (cc != null) {
 *     ItemStack item = cc.createConsumable("INV_SAVE", 1);
 *     player.getInventory().addItem(item);
 * }
 * }</pre>
 */
public interface CustomConsumableProvider {

    /**
     * 소비 아이템 생성. 구매 시 플레이어에게 지급할 실제 ItemStack.
     *
     * @param typeId "INV_SAVE" / "DDUZZONKU" 등. 대소문자 구분 없음.
     * @param amount 개수 (1~64)
     * @return 생성된 ItemStack. typeId가 알 수 없으면 null.
     */
    ItemStack createConsumable(String typeId, int amount);

    /**
     * 주어진 ItemStack이 등록된 커스텀 소비 아이템인지 식별.
     *
     * @return 해당 아이템이라면 typeId, 아니면 null.
     */
    String identifyConsumable(ItemStack item);
}
