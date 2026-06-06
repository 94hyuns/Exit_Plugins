package com.exit.core.api;

import org.bukkit.Location;

/**
 * NpcService 로 조회한 NPC 메타데이터.
 */
public record NpcInfo(
        String owner,
        String id,
        Location location,
        String displayName,
        String skinOwner,
        int entityId
) {}
