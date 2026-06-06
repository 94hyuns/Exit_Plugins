package com.exit.core.api;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * 패킷 기반 가짜 플레이어 NPC 서비스.
 * 모든 플러그인이 공유. owner 별 클릭 핸들러를 등록하고, owner+id 로 NPC 를 식별한다.
 *
 * 사용 예:
 *   NpcService npc = ServiceRegistry.get(NpcService.class).orElseThrow();
 *   npc.registerClickHandler("gamble", (player, npcId, attack) -> { ... });
 *   npc.spawn(new NpcSpawnSpec("gamble", "slot_main", loc, "슬롯머신", "Notch", true));
 */
public interface NpcService {

    /** owner 별 클릭 핸들러 등록. 같은 owner 재호출 시 덮어쓰기. */
    void registerClickHandler(String owner, NpcClickHandler handler);

    /** NPC 를 스폰하고 메모리에 등록. persist=true 면 npcs.yml 에 저장. */
    boolean spawn(NpcSpawnSpec spec);

    /** owner/id 로 NPC 제거. yaml 에서도 제거. */
    boolean remove(String owner, String id);

    /** owner/id 로 NPC 정보 조회. */
    Optional<NpcInfo> get(String owner, String id);

    /** 엔티티 ID 로 NPC 정보 조회 (클릭 이벤트에서 사용). */
    Optional<NpcInfo> getByEntityId(int entityId);

    /** owner 의 모든 NPC. */
    Collection<NpcInfo> getByOwner(String owner);

    /** 등록된 모든 NPC. */
    Collection<NpcInfo> all();

    /** 플레이어가 보는 월드의 모든 NPC 를 다시 패킷 전송 (월드 이동/접속 시 자동 호출됨). */
    void showAllTo(Player player);
}
