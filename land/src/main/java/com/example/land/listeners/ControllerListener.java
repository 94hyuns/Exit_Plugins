package com.example.land.listeners;

import com.example.land.LandPlugin;
import com.example.land.gui.ControllerGui;
import com.example.land.managers.LandManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ControllerListener implements Listener {

    private final LandPlugin plugin;

    public ControllerListener(LandPlugin plugin) {
        this.plugin = plugin;
    }

    /** 컨트롤러 아이템 설치 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getLandManager().isControllerItem(event.getItemInHand())) return;

        // 이미 설치된 컨트롤러가 있으면 막기
        if (plugin.getLandManager().hasController(player.getUniqueId())) {
            player.sendMessage("§c이미 설치된 청크 컨트롤러가 있습니다. 기존 컨트롤러를 먼저 제거해주세요.");
            event.setCancelled(true);
            return;
        }

        // 설치 위치 등록
        plugin.getLandManager().registerController(player.getUniqueId(), event.getBlock().getLocation());
        player.sendMessage("§a청크 컨트롤러가 설치되었습니다! 우클릭으로 관리 창을 열 수 있습니다.");
    }

    /** 컨트롤러 블록 우클릭 → GUI 열기 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        // 해당 위치가 이 플레이어의 컨트롤러인지 확인
        org.bukkit.Location ctrlLoc = plugin.getLandManager().getControllerLocation(player.getUniqueId());
        if (ctrlLoc == null || !ctrlLoc.getBlock().equals(block)) return;

        event.setCancelled(true);
        new ControllerGui(plugin, player).open();
    }

    /** 컨트롤러 블록 파괴 → 등록 해제 + 아이템 드롭 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        org.bukkit.Location ctrlLoc = plugin.getLandManager().getControllerLocation(player.getUniqueId());
        if (ctrlLoc == null || !ctrlLoc.getBlock().equals(event.getBlock())) return;

        event.setDropItems(false); // 기본 드롭 막고
        plugin.getLandManager().unregisterController(player.getUniqueId());

        // 컨트롤러 아이템으로 드롭
        org.bukkit.Material mat = org.bukkit.Material.valueOf(
                plugin.getConfig().getString("land.controller-material", "LODESTONE"));
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§6[청크 컨트롤러]"));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "chunk_controller"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1
        );
        item.setItemMeta(meta);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);

        player.sendMessage("§7청크 컨트롤러가 제거되었습니다.");
    }
}
