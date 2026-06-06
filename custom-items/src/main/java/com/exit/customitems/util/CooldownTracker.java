package com.exit.customitems.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 단순한 player + key 쌍 쿨다운 트래커.
 * 같은 인챈트 키에 대한 동일 플레이어의 다음 발동 가능 시각만 기록.
 */
public final class CooldownTracker {

    private final Map<Key, Long> until = new HashMap<>();

    public boolean isReady(UUID player, String key) {
        Long t = until.get(new Key(player, key));
        return t == null || t <= System.currentTimeMillis();
    }

    public void start(UUID player, String key, long durationMs) {
        until.put(new Key(player, key), System.currentTimeMillis() + durationMs);
    }

    /** 남은 쿨다운 (ms). 끝났거나 기록 없으면 0. */
    public long remainingMs(UUID player, String key) {
        Long t = until.get(new Key(player, key));
        if (t == null) return 0L;
        long r = t - System.currentTimeMillis();
        return Math.max(0L, r);
    }

    /** 플레이어 퇴장 시 정리. */
    public void clear(UUID player) {
        until.entrySet().removeIf(e -> e.getKey().player.equals(player));
    }

    private record Key(UUID player, String key) {}
}
