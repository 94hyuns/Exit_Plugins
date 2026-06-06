package com.example.land.listeners;

import com.example.land.LandPlugin;
import com.example.land.managers.WorldProtectionManager.WorldProtection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Set;

public class ProtectionListener implements Listener {

    private final LandPlugin plugin;

    private static final Set<org.bukkit.Material> CONTAINERS = Set.of(
            org.bukkit.Material.CHEST,
            org.bukkit.Material.TRAPPED_CHEST,
            org.bukkit.Material.BARREL,
            org.bukkit.Material.HOPPER,
            org.bukkit.Material.DROPPER,
            org.bukkit.Material.DISPENSER,
            org.bukkit.Material.FURNACE,
            org.bukkit.Material.BLAST_FURNACE,
            org.bukkit.Material.SMOKER,
            org.bukkit.Material.SHULKER_BOX,
            org.bukkit.Material.ENDER_CHEST
    );

    // 우클릭 수확/본밀/씨앗 심기 등 작물 상호작용 보호 대상.
    // Farming 의 13작물(WHEAT/CARROTS/POTATOES/BEETROOTS 베이스)을 포함하며,
    // 바닐라 NETHER_WART/COCOA 도 우클릭 수확이 가능해 함께 포함.
    private static final Set<org.bukkit.Material> CROPS = Set.of(
            org.bukkit.Material.WHEAT,
            org.bukkit.Material.CARROTS,
            org.bukkit.Material.POTATOES,
            org.bukkit.Material.BEETROOTS,
            org.bukkit.Material.NETHER_WART,
            org.bukkit.Material.COCOA
    );

    public ProtectionListener(LandPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────
    // 블록 파괴
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().hasPermission("land.admin")) return;
        checkProtection(event.getPlayer(), event.getBlock(), ProtectionType.BREAK, event);
    }

    // ─────────────────────────────────────────────
    // 블록 설치
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().hasPermission("land.admin")) return;
        checkProtection(event.getPlayer(), event.getBlock(), ProtectionType.PLACE, event);
    }

    // ─────────────────────────────────────────────
    // 양동이 비우기 (용암/물/분말눈/물고기·도롱뇽 양동이) — 유체 설치 = PLACE 로 취급
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getPlayer().hasPermission("land.admin")) return;
        Block target = event.getBlock();
        if (target == null) return;
        checkProtection(event.getPlayer(), target, ProtectionType.PLACE, event);
    }

    // ─────────────────────────────────────────────
    // 컨테이너 상호작용 (상자, 화로 등)
    // ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        org.bukkit.Material type = event.getClickedBlock().getType();
        if (!CONTAINERS.contains(type) && !CROPS.contains(type)) return;
        if (event.getPlayer().hasPermission("land.admin")) return;
        checkProtection(event.getPlayer(), event.getClickedBlock(), ProtectionType.INTERACT, event);
    }

    // ─────────────────────────────────────────────
    // 핵심 보호 로직
    // ─────────────────────────────────────────────

    private enum ProtectionType { BREAK, PLACE, INTERACT }

    /**
     * 월드 보호 → 청크 보호 순서로 판단한다.
     *
     * 1) 보호 설정 없는 월드 → 통과 (바닐라 동작)
     * 2) 월드 기본 허용 (true)  → 남의 클레임 청크만 차단
     * 3) 월드 기본 금지 (false) → land-override + 내 청크면 허용, 나머진 차단
     */
    private void checkProtection(Player player, Block block, ProtectionType type, Cancellable event) {
        String worldName = block.getWorld().getName();
        WorldProtection wp = plugin.getWorldProtectionManager().getProtection(worldName);

        // 보호 설정 없는 월드 → 간섭하지 않음
        if (wp == null) return;

        boolean worldAllows = switch (type) {
            case BREAK    -> wp.blockBreak;
            case PLACE    -> wp.blockPlace;
            case INTERACT -> wp.blockInteract;
        };

        if (worldAllows) {
            // 월드는 허용하지만, 남의 클레임 청크는 보호
            if (!plugin.getLandManager().canInteract(player, block.getChunk())) {
                event.setCancelled(true);
                player.sendMessage(
                        Component.text("이 땅의 소유자가 아닙니다.").color(NamedTextColor.RED));
            }
            return;
        }

        // ── 월드 기본: 금지 ──

        // land-override가 켜져 있고 + 내 청크(소유자/멤버)면 허용
        if (wp.landOverride && plugin.getLandManager().canInteractOwned(player, block.getChunk())) {
            return;
        }

        // 차단
        event.setCancelled(true);
        if (wp.landOverride) {
            player.sendMessage(
                    Component.text("자신의 청크에서만 블록을 조작할 수 있습니다.")
                            .color(NamedTextColor.RED));
        } else {
            player.sendMessage(
                    Component.text("이 월드에서는 블록을 조작할 수 없습니다.")
                            .color(NamedTextColor.RED));
        }
    }
}
