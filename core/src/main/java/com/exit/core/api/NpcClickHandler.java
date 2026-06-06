package com.exit.core.api;

import org.bukkit.entity.Player;

/**
 * NPC 클릭 이벤트 콜백.
 * 각 owner 플러그인이 자기 NPC 의 클릭 동작을 정의한다.
 */
@FunctionalInterface
public interface NpcClickHandler {
    /**
     * @param player 클릭한 플레이어
     * @param npcId  NpcSpawnSpec.id (owner 플러그인 내에서 유일한 식별자)
     * @param attack true 면 좌클릭(공격), false 면 우클릭(상호작용)
     */
    void onClick(Player player, String npcId, boolean attack);
}
