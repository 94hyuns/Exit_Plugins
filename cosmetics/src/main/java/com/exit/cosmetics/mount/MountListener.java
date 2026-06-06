package com.exit.cosmetics.mount;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.UUID;

/**
 * 탈것 자동 해제 트리거.
 *
 * <ul>
 *   <li>로그아웃 / 사망 / 월드이동 / 원거리 텔레포트 → despawn</li>
 *   <li>탈것 엔티티 사망 → despawn (팬텀 무적이라 보통 미발생)</li>
 *   <li>탑승 중 내림 → despawn 안함. 다시 GUI에서 클릭하면 해제됨.</li>
 * </ul>
 */
public class MountListener implements Listener {

    private final MountManager manager;

    public MountListener(MountManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.despawn(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        manager.despawn(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        manager.despawn(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getFrom().getWorld() == null || e.getTo() == null) return;
        // 다른 월드 또는 64블록 이상 원거리 텔레포트 시 해제
        if (!e.getFrom().getWorld().equals(e.getTo().getWorld())
                || e.getFrom().distanceSquared(e.getTo()) > 64 * 64) {
            manager.despawn(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onMountEntityDeath(EntityDeathEvent e) {
        Entity entity = e.getEntity();
        if (!manager.isActiveMountEntity(entity)) return;
        UUID owner = manager.findOwnerOfEntity(entity.getUniqueId());
        if (owner != null) manager.despawn(owner);
    }

    @EventHandler
    public void onMountEntityDamage(EntityDamageEvent e) {
        if (manager.isActiveMountEntity(e.getEntity())) {
            // 탈것은 데미지 받지 않음
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onMountCombust(EntityCombustEvent e) {
        if (manager.isActiveMountEntity(e.getEntity())) {
            // 햇빛 등으로 인한 발화 차단 (팬텀 등)
            e.setCancelled(true);
        }
    }

    /**
     * 자기 탈것 우클릭 시 재탑승. 다른 사람 탈것 클릭은 차단 (이벤트 cancel).
     * MainHand 만 처리 (offhand 중복 이벤트 무시).
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = e.getRightClicked();
        if (!manager.isActiveMountEntity(clicked)) return;
        UUID owner = manager.findOwnerOfEntity(clicked.getUniqueId());
        if (owner == null) return;
        if (!owner.equals(e.getPlayer().getUniqueId())) {
            // 다른 사람 탈것 — 상호작용 차단
            e.setCancelled(true);
            return;
        }
        // 본인 탈것: 이미 탑승이면 그대로 두고, 아니면 재탑승
        if (!clicked.getPassengers().contains(e.getPlayer())) {
            e.setCancelled(true);
            manager.reMount(e.getPlayer());
        }
    }
}
