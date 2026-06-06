package com.exit.customitems.lamp.mutation;

import com.exit.customitems.lamp.enchant.EnchantStorage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * 기존 변성 아이템 (이전 빌드에서 변성-락만 걸린 것) 에 표식 (빨강 이름 / 보라 "(변성됨)") 을
 * 온디맨드로 추가하는 마이그레이터.
 *
 * <p>판정: 변성-락 PDC 있음 + 표식 없음 → 인챈트 줄 수로 SUCCESS/FAIL 추정
 * <ul>
 *   <li>3줄 이상 → SUCCESS (변성으로 한 줄 추가됨) → 빨강 이름</li>
 *   <li>2줄 이하 → FAIL → lore (변성됨) 추가</li>
 * </ul>
 *
 * <p>훅 시점:
 * <ul>
 *   <li>PlayerJoinEvent — 입장 시 player 인벤토리 + 엔더상자 전체 스캔</li>
 *   <li>InventoryOpenEvent — 인벤토리 열 때 그 안의 아이템 스캔</li>
 *   <li>InventoryClickEvent — 클릭한 슬롯 + 커서 단건 처리</li>
 *   <li>PlayerItemHeldEvent — 핫바 슬롯 변경 시 새 hand 아이템</li>
 * </ul>
 */
public class MutationDisplayMigrator implements Listener {

    private final MutationApplier applier;
    private final EnchantStorage storage;

    /** SUCCESS 추정 임계값. 인챈트 줄 수가 이 값 이상이면 변성 성공으로 간주. */
    private static final int SUCCESS_ENCHANT_COUNT_THRESHOLD = 3;

    public MutationDisplayMigrator(MutationApplier applier, EnchantStorage storage) {
        this.applier = applier;
        this.storage = storage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        scanInventory(p.getInventory());
        scanInventory(p.getEnderChest());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        scanInventory(event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        migrateIfNeeded(event.getCurrentItem());
        migrateIfNeeded(event.getCursor());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        PlayerInventory inv = event.getPlayer().getInventory();
        migrateIfNeeded(inv.getItem(event.getNewSlot()));
    }

    private void scanInventory(Inventory inv) {
        if (inv == null) return;
        for (ItemStack item : inv.getContents()) {
            migrateIfNeeded(item);
        }
    }

    private void migrateIfNeeded(ItemStack item) {
        if (item == null) return;
        if (!applier.isMutated(item)) return;          // 변성-락 PDC 없으면 skip
        if (applier.hasMutationDisplay(item)) return;  // 이미 표식 있으면 skip

        // 인챈트 줄 수로 SUCCESS/FAIL 추정
        int enchantCount = storage.load(item).size();
        boolean success = enchantCount >= SUCCESS_ENCHANT_COUNT_THRESHOLD;
        applier.applyMarker(item, success);
    }
}
