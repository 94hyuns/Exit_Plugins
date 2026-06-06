package com.exit.job.storage;

import com.exit.job.api.event.JobLevelUpEvent;
import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.Sound;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 광부 보관함 라이프사이클:
 *
 * <ul>
 *   <li>우클릭 → GUI 오픈</li>
 *   <li>정보 영역(rawSlot 0~8) 클릭 차단</li>
 *   <li>저장 슬롯(rawSlot 9~53): 화이트리스트 광물만 허용 — 닫힐 때 검증해서 위반 시 인벤 반환</li>
 *   <li>JobLevelUpEvent miner Lv2 도달 → 1회 지급 (PDC marker)</li>
 *   <li>onJoin 백업: miner Lv2+ + PDC 마커 없으면 지급 (setlevel 시나리오)</li>
 * </ul>
 */
public class MineralStorageListener implements Listener {

    private final JavaPlugin plugin;
    private final MineralStorageManager manager;
    private final MineralStorageGUI gui;
    private final JobManager jobManager;
    private final JobConfigManager configManager;
    private final NamespacedKey grantedKey;

    public MineralStorageListener(JavaPlugin plugin, MineralStorageManager manager,
                                   MineralStorageGUI gui, JobManager jobManager,
                                   JobConfigManager configManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.grantedKey = new NamespacedKey(plugin, "mineral_storage_granted");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (!MineralStorageItem.isStorage(hand, plugin)) return;
        event.setCancelled(true);
        gui.open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MineralStorageGUI.Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();

        // 정보 영역 (rawSlot 0~8) — cancel + 버튼 처리
        if (raw >= 0 && raw < MineralStorageGUI.STORAGE_OFFSET) {
            event.setCancelled(true);
            handleButton(event, player, holder);
            return;
        }
        // nav 버튼 (45, 53)
        if (raw == MineralStorageGUI.SLOT_PREV_PAGE || raw == MineralStorageGUI.SLOT_NEXT_PAGE) {
            event.setCancelled(true);
            handleButton(event, player, holder);
        }
    }

    private void handleButton(InventoryClickEvent event, Player player, MineralStorageGUI.Holder holder) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String tag = clicked.getItemMeta().getPersistentDataContainer()
                .get(MineralStorageGUI.ACTION_KEY, PersistentDataType.STRING);
        if (tag == null) return;
        switch (tag) {
            case "TOGGLE_AUTO_COLLECT" -> {
                // ★ 복사 버그 방지: GUI 재오픈 안 하고 토글 슬롯만 in-place 교체.
                // 재오픈하면 새 inv 가 stale 데이터로 빌드되어 close 후 storage 복사됨.
                boolean newState = !manager.isAutoCollect(player.getUniqueId());
                manager.setAutoCollect(player.getUniqueId(), newState);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, newState ? 1.5f : 0.7f);
                holder.getInventory().setItem(MineralStorageGUI.SLOT_TOGGLE, gui.buildToggleButton(newState));
            }
            case "PAGE_PREV" -> changePage(player, holder, -1);
            case "PAGE_NEXT" -> changePage(player, holder, +1);
        }
    }

    private void changePage(Player player, MineralStorageGUI.Holder holder, int delta) {
        int totalPages = manager.pagesFor(player.getUniqueId());
        int next = holder.getCurrentPage() + delta;
        if (next < 0 || next >= totalPages) return;
        // 현재 페이지 슬롯 → holder.data dump
        MineralStorageGUI.dumpPageToData(holder, holder.getInventory());
        holder.setCurrentPage(next);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        gui.renderInto(holder, player);
    }

    /**
     * 자동 수집 ON 이고 stack 이 mineral whitelist 면 보관함에 직접 deposit.
     * 전부 적재 시 이벤트 cancel + Item 엔티티 제거. 일부만 들어가면 Item 의 ItemStack 갱신만 (잔여분 정상 픽업).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        if (!configManager.mineralStorageWhitelist().contains(stack.getType())) return;
        if (!manager.isAutoCollect(player.getUniqueId())) return;

        ItemStack leftover = manager.tryDeposit(player.getUniqueId(), stack);
        if (leftover == null) {
            event.setCancelled(true);
            event.getItem().remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.8f);
        } else {
            event.getItem().setItemStack(leftover);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MineralStorageGUI.Holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < MineralStorageGUI.STORAGE_OFFSET) {
                event.setCancelled(true);
                return;
            }
            if (slot == MineralStorageGUI.SLOT_PREV_PAGE || slot == MineralStorageGUI.SLOT_NEXT_PAGE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof MineralStorageGUI.Holder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 현재 페이지 슬롯 → holder.data dump
        MineralStorageGUI.dumpPageToData(holder, top);

        ItemStack[] data = holder.getData();
        if (data == null) return;

        // non-whitelist 아이템 인벤 반환 + 광물만 보관함에 유지
        var whitelist = configManager.mineralStorageWhitelist();
        for (int i = 0; i < data.length; i++) {
            ItemStack s = data[i];
            if (s == null || s.getType().isAir()) { data[i] = null; continue; }
            if (whitelist.contains(s.getType())) continue;
            data[i] = null;
            var leftover = player.getInventory().addItem(s);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(rest -> player.getWorld()
                        .dropItemNaturally(player.getLocation(), rest));
            }
        }
        manager.save(holder.getOwner(), data);
    }

    @EventHandler
    public void onLevelUp(JobLevelUpEvent event) {
        if (event.getType() != JobType.MINER) return;
        if (event.getOldLevel() >= 2 || event.getNewLevel() < 2) return;
        grantIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onJoinBackup(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (jobManager.getLevel(p.getUniqueId(), JobType.MINER) < 2) return;
            grantIfNeeded(p);
        }, 40L);
    }

    private void grantIfNeeded(Player player) {
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(grantedKey, PersistentDataType.BYTE)) return;
        var leftover = player.getInventory().addItem(MineralStorageItem.create(plugin));
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> player.getWorld()
                    .dropItemNaturally(player.getLocation(), s));
        }
        pdc.set(grantedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(Component.text("광부 능력 해금: ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("광부의 보관함 지급").color(NamedTextColor.AQUA))
                .decoration(TextDecoration.BOLD, false));
    }
}
