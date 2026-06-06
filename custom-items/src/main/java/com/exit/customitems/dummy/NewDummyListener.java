package com.exit.customitems.dummy;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * v2 허수아비 데미지 처리. 단일 hit 당 메시지 1회 보장.
 */
public final class NewDummyListener implements Listener {

    private final DummyKeys keys;
    private final NewDummyManager manager;
    // 동일 entity 에 동일 tick 중복 호출 dedupe
    private final WeakHashMap<UUID, Long> lastTick = new WeakHashMap<>();
    // HIGHEST 단계에서 raw final damage 저장 → MONITOR 단계에서 표시용으로 복원
    private final Map<UUID, Double> pendingRawDamage = new HashMap<>();

    public NewDummyListener(DummyKeys keys, NewDummyManager manager) {
        this.keys = keys;
        this.manager = manager;
    }

    /** 더미가 한 번에 max HP 이상 데미지를 받아도 죽지 않도록 cap. raw 는 별도 저장해 표시는 그대로. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void capDamage(EntityDamageEvent event) {
        if (!NewDummyManager.isNewDummy(event.getEntity(), keys)) return;
        if (!(event.getEntity() instanceof LivingEntity dummy)) return;
        double finalDamage = event.getFinalDamage();
        double currentHp = dummy.getHealth();
        if (finalDamage >= currentHp) {
            pendingRawDamage.put(dummy.getUniqueId(), finalDamage);
            // base 를 currentHp - 1 로 cap → final 도 그 이하 (cow armor 0). 안 죽음.
            double safeBase = Math.max(0.1, currentHp - 1.0);
            event.setDamage(safeBase);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // sweep 어택 무시 (vanilla 이중 firing)
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (!NewDummyManager.isNewDummy(event.getEntity(), keys)) return;
        if (!(event.getEntity() instanceof LivingEntity dummy)) return;

        // 동일 tick 중복 firing 차단 (커스텀 무기가 여러 데미지 이벤트 발사하는 케이스)
        long now = Bukkit.getCurrentTick();
        Long prev = lastTick.get(dummy.getUniqueId());
        if (prev != null && prev == now) return;
        lastTick.put(dummy.getUniqueId(), now);

        // attacker 추출
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile arrow) {
            ProjectileSource s = arrow.getShooter();
            if (s instanceof Player p) attacker = p;
        }

        Double raw = pendingRawDamage.remove(dummy.getUniqueId());
        double damage = raw != null ? raw : event.getFinalDamage();
        manager.onDamaged(dummy, damage, attacker);
    }
}
