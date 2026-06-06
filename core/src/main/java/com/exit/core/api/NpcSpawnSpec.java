package com.exit.core.api;

import org.bukkit.Location;

/**
 * NpcService.spawn() 입력 사양.
 *
 * @param owner       NPC 를 소유한 플러그인 이름 (예: "shop", "gamble"). 클릭 라우팅에 사용
 * @param id          owner 내에서 유일한 NPC 식별자
 * @param location    스폰 좌표 (yaw/pitch 포함)
 * @param displayName 머리 위 또는 이름표 (현재 미사용, 향후 확장)
 * @param skinOwner   머리/스킨을 가져올 마인크래프트 플레이어 이름 (null 이면 기본 스킨)
 * @param persist     true 면 NpcService 가 npcs.yml 에 저장하고 재시작 시 복원
 */
public record NpcSpawnSpec(
        String owner,
        String id,
        Location location,
        String displayName,
        String skinOwner,
        boolean persist
) {}
