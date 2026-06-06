package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.WillOWispEnchant;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 도깨비불 (SET) 시각 효과 — 파란 영혼불 파티클이 플레이어 주위를 공전.
 *
 * <p>매 5틱마다 실행. 각 플레이어에 대해 도깨비불 부위 카운트만큼 파티클 spawn.
 * 360° / N 간격으로 배치, 시간에 따라 회전 (1초 1바퀴).
 * 행운 시너지 적용 — SetLevelCounter 가 자동으로 행운 부위 +1 카운트.
 */
public class WillOWispParticleTask extends BukkitRunnable {

    private static final double ORBIT_RADIUS = 1.0;
    private static final double ORBIT_HEIGHT = 1.0;
    private static final long ROTATION_PERIOD_TICKS = 20L;  // 1초 1바퀴

    private final Plugin plugin;
    private final EnchantStorage storage;
    private final NamespacedKey kWisp;

    private long currentTick = 0;

    public WillOWispParticleTask(Plugin plugin, EnchantStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.kWisp = WillOWispEnchant.keyOf(plugin);
    }

    public void start() {
        runTaskTimer(plugin, 100L, 5L);
    }

    @Override
    public void run() {
        currentTick += 5;
        double angleBase = (currentTick / (double) ROTATION_PERIOD_TICKS) * Math.PI * 2;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || !p.isOnline()) continue;
            int level = SetLevelCounter.countOnArmor(p, kWisp, storage);
            if (level <= 0) continue;

            Location base = p.getLocation();
            double baseY = base.getY() + ORBIT_HEIGHT;
            for (int i = 0; i < level; i++) {
                double a = angleBase + (i * 2 * Math.PI / level);
                double dx = Math.cos(a) * ORBIT_RADIUS;
                double dz = Math.sin(a) * ORBIT_RADIUS;
                Location loc = new Location(base.getWorld(),
                        base.getX() + dx, baseY, base.getZ() + dz);
                base.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }
}
