package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 커스텀 무기 아이템 제공 인터페이스.
 * CustomItems 플러그인이 구현체를 ServiceRegistry 에 등록하고,
 * DungeonRewards 등 다른 플러그인은 이 인터페이스로만 무기 ItemStack 을 생성한다.
 *
 * <p>사용 예시 (DungeonRewards drops):
 * <pre>{@code
 * WeaponProvider w = ServiceRegistry.get(WeaponProvider.class).orElse(null);
 * if (w != null) {
 *     ItemStack frost = w.createWeapon("FROSTMOURNE", 1);
 *     world.dropItemNaturally(loc, frost);
 * }
 * }</pre>
 */
public interface WeaponProvider {

    /** 등록된 모든 무기 타입 카탈로그. */
    List<String> listTypes();

    /**
     * 무기 아이템 생성.
     *
     * @param typeId "FROSTMOURNE" 등 (대소문자 무시).
     * @param amount 개수
     * @return 생성된 ItemStack. typeId 미정의 시 null.
     */
    ItemStack createWeapon(String typeId, int amount);

    /**
     * 주어진 ItemStack 이 우리 무기인지 식별.
     *
     * @return 무기라면 typeId, 아니면 null.
     */
    String identify(ItemStack item);
}
