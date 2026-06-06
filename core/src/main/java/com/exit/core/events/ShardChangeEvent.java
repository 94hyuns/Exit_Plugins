package com.exit.core.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 플레이어 가루(shard) 잔액이 변경될 때 발행되는 이벤트.
 * 치장 가루 교환소 UI 갱신 등에 사용.
 * BalanceChangeEvent와 동일한 패턴.
 */
public class ShardChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final long oldShards;
    private final long newShards;

    public ShardChangeEvent(UUID playerUuid, long oldShards, long newShards) {
        this.playerUuid = playerUuid;
        this.oldShards = oldShards;
        this.newShards = newShards;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public long getOldShards() { return oldShards; }
    public long getNewShards() { return newShards; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
