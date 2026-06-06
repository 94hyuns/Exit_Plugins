package com.exit.world.boss;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * BossArenaManager 에 이벤트 라우팅.
 *
 * - PlayerChangedWorldEvent: 입장(arrival) → manager.onPlayerEntered, 퇴장(departure) → manager.onPlayerLeft
 * - PlayerJoinEvent: 보스 월드에 직접 접속한 경우 입장으로 처리
 * - PlayerQuitEvent: 보스 월드에서 quit → 퇴장으로 처리
 * - MythicMobDeathEvent: 보스 사망 → manager.onMythicMobDeath
 */
public class BossArenaListener implements Listener {

    private final BossArenaManager manager;

    public BossArenaListener(BossArenaManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String fromWorld = event.getFrom().getName();
        String toWorld = player.getWorld().getName();

        // 퇴장 처리 (보스 월드에서 나간 경우)
        manager.byWorld(fromWorld).ifPresent(inst -> manager.onPlayerLeft(fromWorld));

        // 입장 처리
        manager.byWorld(toWorld).ifPresent(inst -> manager.onPlayerEntered(player, inst));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String w = player.getWorld().getName();
        manager.byWorld(w).ifPresent(inst -> manager.onPlayerEntered(player, inst));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String w = event.getPlayer().getWorld().getName();
        // quit 처리는 다음 tick 에 (이 tick 에서는 아직 카운트에 잡힘)
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("World"),
                () -> manager.byWorld(w).ifPresent(inst -> manager.onPlayerLeft(w))
        );
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMythicDeath(MythicMobDeathEvent event) {
        Player killer = event.getKiller() instanceof Player p ? p : null;
        manager.onMythicMobDeath(event.getEntity(), killer);
    }

    /**
     * 보스 entity 가 데미지 받을 때 BossArenaConfig.damageMultiplier 적용.
     * Bukkit max_health 어트리뷰트 캡 (1024) 우회용.
     * HIGHEST priority — 다른 플러그인의 데미지 수정 이후 마지막에 적용.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamage(EntityDamageEvent event) {
        var matched = manager.findByBossUuid(event.getEntity().getUniqueId());
        if (matched.isEmpty()) return;
        double mult = matched.get().config().damageMultiplier();
        if (mult == 1.0) return;
        event.setDamage(event.getDamage() * mult);
    }
}
