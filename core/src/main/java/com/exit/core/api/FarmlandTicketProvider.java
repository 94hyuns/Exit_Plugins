package com.exit.core.api;

import org.bukkit.inventory.ItemStack;

/**
 * 경작지 티켓 아이템 제공 인터페이스.
 * Farming 플러그인이 구현체를 ServiceRegistry에 등록하고,
 * Shop 등 다른 플러그인은 이 인터페이스만 통해 티켓 아이템을 다룬다.
 *
 * <p>"경작지 티켓"이란: 마을 서버에서 일반 흙 블록에 우클릭하여 소유권 있는
 * 경작지를 생성할 수 있는 소비 아이템. Farming 플러그인이 클레임 DB에 등록.
 *
 * <p>티켓 사용 로직(흙 → 경작지 전환 + 클레임 등록)은 Farming 내부에서 처리.
 * 외부 플러그인은 단지 티켓 아이템을 발급받거나 식별하는 것만 가능.
 */
public interface FarmlandTicketProvider {

    /**
     * 티켓 ItemStack 생성.
     * @param amount 개수 (1~64)
     * @return 티켓 ItemStack
     */
    ItemStack createTicket(int amount);

    /** 주어진 ItemStack이 경작지 티켓인지 식별. */
    boolean isTicket(ItemStack item);
}
