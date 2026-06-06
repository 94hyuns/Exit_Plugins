package com.exit.customitems.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

/**
 * 허수아비(MythicMob bv_dummy) 피해 표시 listener.
 *
 * <p>MythicMobs yml 의 placeholder 가 버전·컨텍스트에 따라 불안정하여
 * (예: {@code <skill.damage>} 가 Undefined 로 나오는 버그) custom-items 측에서
 * 직접 EntityDamageByEntityEvent 받아 attacker 에게 최종 피해량을 메시지로 전달.
 *
 * <p>식별: 허수아비는 {@link DummyKeys#marker} PDC 마커(BYTE=1) 보유.
 */
public class DummyDamageListener implements Listener {

    private final DummyKeys keys;

    public DummyDamageListener(DummyKeys keys) {
        this.keys = keys;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        // sweep 어택은 메인 hit 와 함께 발사되어 메시지 중복 → 무시
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        // 더미 식별
        if (!event.getEntity().getPersistentDataContainer().has(keys.marker, PersistentDataType.BYTE)) {
            return;
        }

        // attacker 추출 (직접 공격 또는 projectile shooter)
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile arrow) {
            ProjectileSource shooter = arrow.getShooter();
            if (shooter instanceof Player p) attacker = p;
        }
        if (attacker == null) return;

        double finalDamage = event.getFinalDamage();
        attacker.sendMessage(Component.text("[허수아비] ", NamedTextColor.DARK_GRAY)
            .append(Component.text("-" + String.format("%.1f", finalDamage), NamedTextColor.RED))
            .append(Component.text(" 데미지", NamedTextColor.GRAY)));
    }
}
