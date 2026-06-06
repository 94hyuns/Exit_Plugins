package com.exit.core.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 플레이어 잔액이 변경될 때 발행되는 이벤트.
 * Economy 플러그인의 사이드바 갱신, 알림 등에 사용.
 */
public class BalanceChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final long oldBalance;
    private final long newBalance;

    public BalanceChangeEvent(UUID playerUuid, long oldBalance, long newBalance) {
        this.playerUuid = playerUuid;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public long getOldBalance() {
        return oldBalance;
    }

    public long getNewBalance() {
        return newBalance;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
