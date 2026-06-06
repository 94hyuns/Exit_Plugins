package com.exit.farming.storage;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.farming.FarmingPlugin;
import com.exit.farming.item.CropItem;
import com.exit.job.api.event.JobLevelUpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumSet;
import java.util.Set;

/** 농부 보관함 라이프사이클 + 1회 지급. */
public class CropStorageListener implements Listener {

    private final FarmingPlugin plugin;
    private final CropStorageManager manager;
    private final CropStorageGUI gui;
    private final NamespacedKey grantedKey;
    private final Set<Material> vanillaWhitelist;

    public CropStorageListener(FarmingPlugin plugin, CropStorageManager manager, CropStorageGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.grantedKey = new NamespacedKey(plugin, "crop_storage_granted");
        this.vanillaWhitelist = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("crop-storage.vanilla-whitelist")) {
            Material m = Material.matchMaterial(name);
            if (m != null) vanillaWhitelist.add(m);
        }
    }

    /** 작물 스택인지: Farming CropItem 의 fruit/seed 또는 vanilla whitelist. */
    private boolean isCrop(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        // Farming CropItem PDC 키 검사
        if (stack.hasItemMeta()) {
            var pdc = stack.getItemMeta().getPersistentDataContainer();
            if (pdc.has(CropItem.KEY_CROP_ID, PersistentDataType.STRING)) return true;
        }
        return vanillaWhitelist.contains(stack.getType());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (!CropStorageItem.isStorage(hand, plugin)) return;
        event.setCancelled(true);
        gui.open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CropStorageGUI.Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;

        int rs = event.getRawSlot();

        // 상단 UI (0~8) 또는 nav (45, 53) — cancel + 버튼 처리
        boolean isUi = rs >= 0 && rs < CropStorageGUI.STORAGE_OFFSET;
        boolean isNav = rs == CropStorageGUI.SLOT_PREV_PAGE || rs == CropStorageGUI.SLOT_NEXT_PAGE;
        if (!isUi && !isNav) return;

        event.setCancelled(true);
        if (isUi && rs != CropStorageGUI.SLOT_TOGGLE && rs != CropStorageGUI.SLOT_REPLANT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String tag = clicked.getItemMeta().getPersistentDataContainer()
                .get(CropStorageGUI.ACTION_KEY, PersistentDataType.STRING);
        if (tag == null) return;

        switch (tag) {
            case "TOGGLE_AUTO_COLLECT" -> {
                boolean newState = !manager.isAutoCollect(p.getUniqueId());
                manager.setAutoCollect(p.getUniqueId(), newState);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, newState ? 1.5f : 0.7f);
                // ★ 복사 버그 방지: GUI 재오픈 안 하고 토글 슬롯만 in-place 교체.
                top.setItem(CropStorageGUI.SLOT_TOGGLE, gui.buildToggleButton(newState, p.getUniqueId()));
            }
            case "TOGGLE_AUTO_REPLANT" -> {
                // Lv10 미만 게이트 — 잠김 상태에서 클릭은 안내 메시지만
                JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
                int level = (jobs == null) ? 0 : jobs.getLevel(p.getUniqueId(), "farmer");
                if (level < 10) {
                    p.sendMessage(Component.text("자동 재심기는 농부 Lv.10 부터 사용할 수 있습니다.",
                            NamedTextColor.YELLOW));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
                    return;
                }
                boolean newState = !manager.isAutoReplant(p.getUniqueId());
                manager.setAutoReplant(p.getUniqueId(), newState);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, newState ? 1.5f : 0.7f);
                top.setItem(CropStorageGUI.SLOT_REPLANT, gui.buildReplantButton(newState, p.getUniqueId()));
            }
            case "PAGE_PREV" -> changePage(p, holder, -1);
            case "PAGE_NEXT" -> changePage(p, holder, +1);
        }
    }

    private void changePage(Player player, CropStorageGUI.Holder holder, int delta) {
        int totalPages = manager.pagesFor(player.getUniqueId());
        int next = holder.getCurrentPage() + delta;
        if (next < 0 || next >= totalPages) return;
        // 현재 페이지 슬롯 → holder.data dump
        CropStorageGUI.dumpPageToData(holder, holder.getInventory());
        holder.setCurrentPage(next);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        gui.renderInto(holder, player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        if (!isCrop(stack)) return;
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
        if (!(top.getHolder() instanceof CropStorageGUI.Holder)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < CropStorageGUI.STORAGE_OFFSET) {
                event.setCancelled(true);
                return;
            }
            if (slot == CropStorageGUI.SLOT_PREV_PAGE || slot == CropStorageGUI.SLOT_NEXT_PAGE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof CropStorageGUI.Holder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 현재 페이지 슬롯 → holder.data dump
        CropStorageGUI.dumpPageToData(holder, top);

        ItemStack[] data = holder.getData();
        if (data == null) return;

        // non-crop 아이템 인벤 반환 + 작물만 보관함에 유지
        for (int i = 0; i < data.length; i++) {
            ItemStack s = data[i];
            if (s == null || s.getType().isAir()) { data[i] = null; continue; }
            if (isCrop(s)) continue;
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
        if (!"farmer".equals(event.getJobId())) return;
        if (event.getOldLevel() >= 2 || event.getNewLevel() < 2) return;
        grantIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onJoinBackup(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
            if (jobs == null) return;
            if (jobs.getLevel(p.getUniqueId(), "farmer") < 2) return;
            grantIfNeeded(p);
        }, 40L);
    }

    private void grantIfNeeded(Player player) {
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(grantedKey, PersistentDataType.BYTE)) return;
        var leftover = player.getInventory().addItem(CropStorageItem.create(plugin));
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> player.getWorld()
                    .dropItemNaturally(player.getLocation(), s));
        }
        pdc.set(grantedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(Component.text("농부 능력 해금: ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("농부의 보관함 지급").color(NamedTextColor.GREEN))
                .decoration(TextDecoration.BOLD, false));
    }

    // ─── 범위 자동수집 (Lv6 광역 수집 I / Lv10 광역 수집 II) ───
    private BukkitTask rangeCollectTask;

    /** FarmingPlugin.onEnable 에서 호출. 0.5초 간격 (10 tick) 으로 nearby 작물 흡수. */
    public void startRangeCollectTask() {
        if (rangeCollectTask != null) return;
        rangeCollectTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::rangeCollectTick, 20L, 10L);
    }

    public void stopRangeCollectTask() {
        if (rangeCollectTask != null) {
            rangeCollectTask.cancel();
            rangeCollectTask = null;
        }
    }

    private void rangeCollectTick() {
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!manager.isAutoCollect(p.getUniqueId())) continue;
            int level = jobs.getLevel(p.getUniqueId(), "farmer");
            if (level < 6) continue;  // 광역 수집 (Lv6) 미만 → vanilla pickup 으로만 동작
            double r = 5.0;

            for (var entity : p.getNearbyEntities(r, r, r)) {
                if (!(entity instanceof Item itemEntity)) continue;
                if (itemEntity.getPickupDelay() == Integer.MAX_VALUE) continue;  // 픽업 불가 마킹
                ItemStack stack = itemEntity.getItemStack();
                if (stack == null || stack.getType().isAir()) continue;
                if (!isCrop(stack)) continue;

                ItemStack leftover = manager.tryDeposit(p.getUniqueId(), stack);
                if (leftover == null) {
                    itemEntity.remove();
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25f, 1.9f);
                } else {
                    itemEntity.setItemStack(leftover);
                }
            }
        }
    }
}
