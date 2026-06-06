package com.exit.fishing.storage;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.fishing.gui.FishCodexGUI;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.SeasonManager;
import com.exit.job.api.event.JobLevelUpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 어부 보관함 라이프사이클:
 *
 * <ul>
 *   <li>Fishing 측에서 사용 — 우클릭 → GUI 오픈, 클릭 제한, 닫을 때 저장</li>
 *   <li>JobLevelUpEvent fisher Lv2 → 1회 지급</li>
 *   <li>onJoin → fisher Lv2+ + PDC 마커 없으면 백업 지급 (setlevel 시나리오)</li>
 * </ul>
 *
 * <p>슬롯 제한 정책: 닫힐 때 storage 슬롯 (rawSlot 9~53) 의 non-fish 아이템은 플레이어 인벤으로
 * 반환 (인벤 가득이면 발 밑 드롭). 플레이 중 임시 배치는 허용 — 닫는 순간 정합성 보장.
 */
public class FishStorageListener implements Listener {

    private final JavaPlugin plugin;
    private final FishStorageManager manager;
    private final FishStorageGUI gui;
    private final SeasonManager seasons;
    private final NamespacedKey grantedKey;

    public FishStorageListener(JavaPlugin plugin, FishStorageManager manager,
                                FishStorageGUI gui, SeasonManager seasons) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.seasons = seasons;
        this.grantedKey = new NamespacedKey(plugin, "fish_storage_granted");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack hand = event.getItem();
        if (!FishStorageItem.isStorage(hand, plugin)) return;
        event.setCancelled(true);
        gui.open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof FishStorageGUI.Holder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int raw = event.getRawSlot();

        // 정보 영역 (rawSlot 0~8) — 모든 클릭 cancel + 버튼 처리
        if (raw >= 0 && raw < FishStorageGUI.STORAGE_OFFSET) {
            event.setCancelled(true);
            handleButton(event, player, holder);
            return;
        }
        // nav 버튼 (45, 53)
        if (raw == FishStorageGUI.SLOT_PREV_PAGE || raw == FishStorageGUI.SLOT_NEXT_PAGE) {
            event.setCancelled(true);
            handleButton(event, player, holder);
            return;
        }
        // storage 슬롯과 player inv: 자유 (닫을 때 검증)
    }

    private void handleButton(InventoryClickEvent event, Player player, FishStorageGUI.Holder holder) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String tag = clicked.getItemMeta().getPersistentDataContainer()
                .get(FishStorageGUI.ACTION_KEY_BUTTON, PersistentDataType.STRING);
        if (tag == null) return;
        switch (tag) {
            case "OPEN_CODEX" -> {
                player.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin,
                        () -> FishCodexGUI.open(player, seasons.current()), 1L);
            }
            case "TOGGLE_AUTO_COLLECT" -> {
                // ★ 복사 버그 방지: GUI 재오픈 안 하고 토글 슬롯만 in-place 교체.
                boolean newState = !manager.isAutoCollect(player.getUniqueId());
                manager.setAutoCollect(player.getUniqueId(), newState);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, newState ? 1.5f : 0.7f);
                holder.getInventory().setItem(FishStorageGUI.SLOT_TOGGLE, gui.buildToggleButton(newState));
            }
            case "PAGE_PREV" -> changePage(player, holder, -1);
            case "PAGE_NEXT" -> changePage(player, holder, +1);
        }
    }

    private void changePage(Player player, FishStorageGUI.Holder holder, int delta) {
        int totalPages = manager.pagesFor(player.getUniqueId());
        int next = holder.getCurrentPage() + delta;
        if (next < 0 || next >= totalPages) return;
        // 현재 페이지 슬롯 → holder.data dump
        FishStorageGUI.dumpPageToData(holder, holder.getInventory());
        holder.setCurrentPage(next);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        gui.renderInto(holder, player);
    }

    /** fish 인 경우만 auto-collect 로 redirect. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (!FishItem.isFish(stack)) return;
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
        if (!(top.getHolder() instanceof FishStorageGUI.Holder)) return;
        // 정보 영역 + nav 버튼에 drag 차단
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < FishStorageGUI.STORAGE_OFFSET) {
                event.setCancelled(true);
                return;
            }
            if (slot == FishStorageGUI.SLOT_PREV_PAGE || slot == FishStorageGUI.SLOT_NEXT_PAGE) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getInventory();
        if (!(top.getHolder() instanceof FishStorageGUI.Holder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 현재 페이지 슬롯 → holder.data dump
        FishStorageGUI.dumpPageToData(holder, top);

        ItemStack[] data = holder.getData();
        if (data == null) return;

        // non-fish 아이템 인벤 반환 + fish 만 보관함에 유지
        for (int i = 0; i < data.length; i++) {
            ItemStack s = data[i];
            if (s == null || s.getType().isAir()) { data[i] = null; continue; }
            if (FishItem.isFish(s)) continue;
            data[i] = null;
            var leftover = player.getInventory().addItem(s);
            if (!leftover.isEmpty()) {
                leftover.values().forEach(rest -> player.getWorld()
                        .dropItemNaturally(player.getLocation(), rest));
            }
        }
        manager.save(holder.getOwner(), data);
    }

    // ─── 어부 Lv2 도달 시 1회 지급 ───

    @EventHandler
    public void onLevelUp(JobLevelUpEvent event) {
        if (!"fisher".equals(event.getJobId())) return;
        if (event.getOldLevel() >= 2 || event.getNewLevel() < 2) return;
        grantIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onJoinBackup(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
            if (jobs == null) return;
            if (jobs.getLevel(p.getUniqueId(), "fisher") < 2) return;
            grantIfNeeded(p);
        }, 40L); // 2초 지연 (Job 의 onJoin 데이터 로드 대기)
    }

    private void grantIfNeeded(Player player) {
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(grantedKey, PersistentDataType.BYTE)) return;
        var leftover = player.getInventory().addItem(FishStorageItem.create(plugin));
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> player.getWorld()
                    .dropItemNaturally(player.getLocation(), s));
        }
        pdc.set(grantedKey, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(Component.text("어부 능력 해금: ")
                .color(NamedTextColor.GOLD)
                .append(Component.text("어부의 보관함 지급").color(NamedTextColor.AQUA))
                .decoration(TextDecoration.BOLD, false));
    }
}
