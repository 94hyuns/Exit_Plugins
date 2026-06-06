package com.exit.customitems.lamp.bulk;

import com.exit.customitems.lamp.LampApplyResult;
import com.exit.customitems.lamp.LampHandler;
import com.exit.customitems.lamp.LampItem;
import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * BulkLamp GUI / NPC 의 모든 이벤트 처리.
 *
 * <ul>
 *   <li>{@link PlayerInteractEntityEvent}: 램프 작업대 NPC 우클릭 → GUI 열기 (vanilla trade UI 차단)</li>
 *   <li>{@link InventoryClickEvent}: 활성 슬롯 외 클릭 차단, 잘못된 아이템 슬롯 매칭 차단, 버튼 클릭 시 롤 실행</li>
 *   <li>{@link InventoryDragEvent}: GUI 안 드래그 차단</li>
 *   <li>{@link InventoryCloseEvent}: 활성 슬롯 아이템 플레이어에게 반환</li>
 * </ul>
 */
public class BulkLampListener implements Listener {

    private final BulkLampKeys keys;
    private final BulkLampNPCManager npcManager;
    private final LampHandler lampHandler;
    private final LampItem lampItem;
    private final EnchantStorage storage;

    public BulkLampListener(BulkLampKeys keys, BulkLampNPCManager npcManager,
                            LampHandler lampHandler, LampItem lampItem,
                            EnchantStorage storage) {
        this.keys = keys;
        this.npcManager = npcManager;
        this.lampHandler = lampHandler;
        this.lampItem = lampItem;
        this.storage = storage;
    }

    @EventHandler(ignoreCancelled = true)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!npcManager.isNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        // Villager 의 자동 trade UI 가 한 번 더 발동될 수 있어 이벤트 핸드 체크
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        new BulkLampGUI(keys, storage).openFor(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BulkLampGUI gui)) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean clickedTop = event.getRawSlot() < top.getSize();
        int slot = event.getSlot();

        // shift-클릭: 플레이어 인벤 → 상단으로 가는 흐름 막기 (활성 슬롯에 적절히 들어가도록 우리가 직접 처리)
        if (!clickedTop && event.isShiftClick()) {
            ItemStack moved = event.getCurrentItem();
            if (moved == null || moved.getType().isAir()) return;
            event.setCancelled(true);
            handleShiftIntoGui(gui, player, moved, event);
            return;
        }

        // 상단 인벤이 아닌 클릭(플레이어 인벤 일반 클릭)은 정상 허용
        if (!clickedTop) return;

        // 상단 인벤 클릭은 일단 cancel 한 뒤 슬롯별로 직접 처리.
        event.setCancelled(true);

        // 버튼 슬롯
        if (slot == BulkLampGUI.SLOT_BUTTON) {
            if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
                handleRoll(gui, player);
            }
            return;
        }

        // 램프 슬롯 / 장비 슬롯 → 커서 ↔ 슬롯 swap 허용 (호환 검사 포함)
        if (slot == BulkLampGUI.SLOT_LAMP || slot == BulkLampGUI.SLOT_TARGET) {
            handleSlotSwap(gui, slot, player, event);
            return;
        }

        // 그 외 (placeholder) 는 cancel 만 (이미 위에서 처리됨)
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BulkLampGUI)) return;
        int topSize = event.getView().getTopInventory().getSize();
        // 드래그가 상단 인벤 슬롯 중 하나라도 침범하면 차단 (간단하게 전체 차단)
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BulkLampGUI gui)) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // 활성 슬롯 아이템 반환
        returnItem(player, gui.getLamp());
        returnItem(player, gui.getTarget());

        // 슬롯 비워둠 (혹시 GUI 가 다시 열릴 일에 대비)
        gui.getInventory().setItem(BulkLampGUI.SLOT_LAMP, null);
        gui.getInventory().setItem(BulkLampGUI.SLOT_TARGET, null);
    }

    // ─────────────────────────────────────────────

    private void handleRoll(BulkLampGUI gui, Player player) {
        ItemStack lamp = gui.getLamp();
        ItemStack target = gui.getTarget();
        if (lamp == null || lamp.getType().isAir()) {
            sendActionBar(player, "&c램프를 슬롯에 넣어주세요.");
            return;
        }
        if (target == null || target.getType().isAir()) {
            sendActionBar(player, "&c장비를 슬롯에 넣어주세요.");
            return;
        }

        LampApplyResult result = lampHandler.applyLamp(player, lamp, target, true);
        if (!result.success()) {
            sendActionBar(player, "&c" + result.errorMessage());
            return;
        }

        // 인벤토리 시각 갱신 (target 의 lore 가 갱신됐고, lamp 의 amount 가 -1 됨)
        gui.getInventory().setItem(BulkLampGUI.SLOT_TARGET, target);
        if (lamp.getAmount() <= 0) {
            gui.getInventory().setItem(BulkLampGUI.SLOT_LAMP, null);
        } else {
            gui.getInventory().setItem(BulkLampGUI.SLOT_LAMP, lamp);
        }
        gui.refreshButton();
        player.updateInventory();
    }

    private void handleSlotSwap(BulkLampGUI gui, int slot, Player player, InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        ItemStack cursor  = event.getCursor();

        boolean cursorEmpty = cursor == null || cursor.getType().isAir();
        boolean currentEmpty = current == null || current.getType().isAir();

        // 커서가 비었으면 단순 회수
        if (cursorEmpty) {
            if (!currentEmpty) {
                event.getView().setCursor(current.clone());
                gui.getInventory().setItem(slot, null);
            }
            return;
        }

        // 커서에 아이템이 있으면 슬롯 호환 검사
        if (slot == BulkLampGUI.SLOT_LAMP) {
            if (!lampItem.isLamp(cursor)) {
                sendActionBar(player, "&c램프 슬롯에는 램프만 넣을 수 있습니다.");
                return;
            }
        } else if (slot == BulkLampGUI.SLOT_TARGET) {
            if (lampItem.isLamp(cursor)) {
                sendActionBar(player, "&c장비 슬롯에는 램프를 넣을 수 없습니다.");
                return;
            }
            if (!ToolCategory.isLifeApplicable(cursor) && !ToolCategory.isCombatApplicable(cursor)) {
                sendActionBar(player, "&c램프 적용 대상이 아닌 아이템입니다.");
                return;
            }
        }

        // 슬롯 ↔ 커서 swap (같은 종류 stack 이면 합쳐도 되지만 단순화)
        ItemStack toPutInSlot = cursor.clone();
        ItemStack toCursor    = currentEmpty ? null : current.clone();
        gui.getInventory().setItem(slot, toPutInSlot);
        event.getView().setCursor(toCursor);
        if (slot == BulkLampGUI.SLOT_TARGET) gui.refreshButton();
    }

    private void handleShiftIntoGui(BulkLampGUI gui, Player player, ItemStack moved, InventoryClickEvent event) {
        // shift-클릭: 적합한 슬롯에 자동 배치
        if (lampItem.isLamp(moved)) {
            ItemStack current = gui.getInventory().getItem(BulkLampGUI.SLOT_LAMP);
            if (current == null || current.getType().isAir()) {
                gui.getInventory().setItem(BulkLampGUI.SLOT_LAMP, moved.clone());
                event.setCurrentItem(null);
            } else if (current.isSimilar(moved)) {
                int space = current.getMaxStackSize() - current.getAmount();
                if (space <= 0) {
                    sendActionBar(player, "&c램프 슬롯이 가득 찼습니다.");
                    return;
                }
                int transfer = Math.min(space, moved.getAmount());
                current.setAmount(current.getAmount() + transfer);
                ItemStack remaining = moved.clone();
                remaining.setAmount(moved.getAmount() - transfer);
                event.setCurrentItem(remaining.getAmount() > 0 ? remaining : null);
            } else {
                sendActionBar(player, "&c다른 램프가 이미 들어 있습니다.");
            }
            return;
        }

        if (ToolCategory.isLifeApplicable(moved) || ToolCategory.isCombatApplicable(moved)) {
            ItemStack current = gui.getInventory().getItem(BulkLampGUI.SLOT_TARGET);
            if (current != null && !current.getType().isAir()) {
                sendActionBar(player, "&c장비 슬롯이 이미 차 있습니다.");
                return;
            }
            ItemStack one = moved.clone();
            one.setAmount(1);
            gui.getInventory().setItem(BulkLampGUI.SLOT_TARGET, one);
            int rest = moved.getAmount() - 1;
            if (rest <= 0) {
                event.setCurrentItem(null);
            } else {
                ItemStack remaining = moved.clone();
                remaining.setAmount(rest);
                event.setCurrentItem(remaining);
            }
            gui.refreshButton();
            return;
        }

        // 그 외 아이템은 무시 (이동 안 함)
    }

    private void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
    }

    private void sendActionBar(HumanEntity entity, String legacy) {
        if (!(entity instanceof Player p)) return;
        p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacy));
    }
}
