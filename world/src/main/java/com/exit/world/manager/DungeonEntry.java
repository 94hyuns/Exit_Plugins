package com.exit.world.manager;

import com.exit.world.boss.BossArenaConfig;
import org.bukkit.Material;

/**
 * dungeons.yml 의 dungeon 항목 1개.
 * bossArena 는 보스 탭의 보스 월드에만 채워짐 (던전 탭은 null).
 */
public record DungeonEntry(
        String key,
        Tab tab,
        String worldName,
        String displayName,
        Material icon,
        long cost,
        BossArenaConfig bossArena,
        int timeLimitSec,         // > 0 시 N초 후 강제 마을 텔포 (0 = 무제한)
        int guiSlot,              // GUI 슬롯 (>= 0 시 직접 지정, -1 = auto sequential)
        String description        // 아이콘 lore 마지막 줄에 표시 (빈 문자열 시 생략)
) {
    public enum Tab { DUNGEON, BOSS }
}
