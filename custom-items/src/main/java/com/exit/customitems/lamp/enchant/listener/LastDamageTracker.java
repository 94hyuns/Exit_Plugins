package com.exit.customitems.lamp.enchant.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어가 마지막으로 데미지를 받은 시각(ms)을 추적.
 * 빠른 회복 인챈트의 발동 조건 판정에 사용.
 */
public class LastDamageTracker implements Listener {

    private final Map<UUID, Long> lastDamage = new HashMap<>();

    public long getLastDamage(UUID uuid) {
        return lastDamage.getOrDefault(uuid, 0L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getFinalDamage() <= 0) return;
        lastDamage.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastDamage.remove(event.getPlayer().getUniqueId());
    }
}
