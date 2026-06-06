package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 커스텀 램프 아이템 제공 인터페이스.
 * CustomItems 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop / NPC상점 등 다른 플러그인은 이 인터페이스만 통해 램프를 다룬다.
 *
 * <p>사용 예시 (상점 플러그인):
 * <pre>{@code
 * LampProvider lamp = ServiceRegistry.get(LampProvider.class).orElse(null);
 * if (lamp != null) {
 *     ItemStack lifeLamp = lamp.createLamp("LIFE", 1);
 *     player.getInventory().addItem(lifeLamp);
 * }
 * }</pre>
 */
public interface LampProvider {

    /**
     * 등록된 모든 램프 타입의 메타데이터.
     * 상점 config 작성이나 카탈로그 GUI 구성에 사용.
     */
    List<LampInfo> listTypes();

    /**
     * 램프 아이템 생성. 구매 시 플레이어에게 지급할 실제 ItemStack.
     *
     * @param typeId "LIFE" / "COMBAT" 등. 대소문자 구분 없음.
     * @param amount 개수 (1~64)
     * @return 생성된 램프 ItemStack. typeId가 알 수 없으면 null.
     */
    ItemStack createLamp(String typeId, int amount);

    /**
     * 주어진 ItemStack이 램프인지 식별.
     * 환불이나 상인 역방향 거래에서 "이 아이템이 어떤 램프인지" 확인할 때 사용.
     *
     * @return 램프라면 typeId ("LIFE" / "COMBAT"), 아니면 null.
     */
    String identify(ItemStack item);
}
