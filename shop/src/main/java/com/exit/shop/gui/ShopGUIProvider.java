package com.exit.shop.gui;

import com.exit.shop.model.ShopCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * 상점 GUI 인터페이스.
 *
 * 현재는 ChestGUI(ShopGUI)가 유일한 구현체이지만,
 * 추후 리소스팩 기반 커스텀 UI나 ProtocolLib 패킷 기반 UI로
 * 교체할 때 이 인터페이스만 새로 구현하면 된다.
 *
 * ShopPlugin에서 구현체를 교체하면 리스너 코드 수정 없이 전환 가능.
 */
public interface ShopGUIProvider {

    /** 해당 카테고리의 상점 GUI를 플레이어에게 연다 */
    void open(Player player, ShopCategory category);

    /** 특정 페이지를 지정하여 연다 */
    void open(Player player, ShopCategory category, int page);

    /** 플레이어가 현재 보고 있는 카테고리 */
    Optional<ShopCategory> getOpenCategory(UUID playerId);

    /** 현재 페이지 번호 */
    int getOpenPage(UUID playerId);

    /** GUI 상태 정리 */
    void close(UUID playerId);

    /** 클릭된 아이템에서 액션 문자열 추출 ("itemId|ACTION|amount") */
    Optional<String> getAction(ItemStack clickedItem);
}
