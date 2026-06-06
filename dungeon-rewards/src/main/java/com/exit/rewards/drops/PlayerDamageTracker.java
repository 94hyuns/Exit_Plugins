package com.exit.rewards.drops;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 엔티티에 player(또는 player projectile)가 데미지를 입힌 시각을 기록.
 *
 * RewardDispatcher 가 사망 이벤트에서 "**아주 최근에** player 데미지가 있었는지"
 * 를 판정하는 데 사용. Bukkit 의 getKiller() / lastDamageCause 는 5초 캐시
 * 동작 때문에 killall · auto-cleanup 으로 죽인 경우에도 player 가 죽인 것처럼
 * 보이는 버그가 있어, 좁은 윈도우(250ms) 기준 직접 추적 필요.
 */
public class PlayerDamageTracker implements Listener {

    private static final long REWARD_WINDOW_MS = 250L;  // 사망 250ms 안 player 데미지면 인정
    private static final long STALE_CLEANUP_MS = 30_000L;

    private final Map<UUID, Long> lastPlayerDamageMs = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player p = resolvePlayer(event);
        if (p == null) return;
        if (event.getFinalDamage() <= 0) return;
        UUID victim = event.getEntity().getUniqueId();
        lastPlayerDamageMs.put(victim, System.currentTimeMillis());
        maybePurge();
    }

    /** 데미지 이벤트에서 실제 player damager 추출 (직접 타격 + 화살/투사체 둘 다). */
    private Player resolvePlayer(EntityDamageByEntityEvent event) {
        var dmg = event.getDamager();
        if (dmg instanceof Player p) return p;
        if (dmg instanceof Projectile proj && proj.getShooter() instanceof Player shooter) return shooter;
        return null;
    }

    /** 사망 시점에 250ms 안 player 데미지가 있었나? */
    public boolean wasKilledByPlayer(UUID entityId) {
        Long ts = lastPlayerDamageMs.remove(entityId);
        if (ts == null) return false;
        return (System.currentTimeMillis() - ts) <= REWARD_WINDOW_MS;
    }

    private long lastPurge = 0L;
    private void maybePurge() {
        long now = System.currentTimeMillis();
        if (now - lastPurge < 10_000L) return;
        lastPurge = now;
        lastPlayerDamageMs.entrySet().removeIf(e -> (now - e.getValue()) > STALE_CLEANUP_MS);
    }
}
