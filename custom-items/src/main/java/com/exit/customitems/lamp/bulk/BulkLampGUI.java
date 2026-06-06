package com.exit.customitems.lamp.bulk;

import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 대량 램프 작업 GUI — Chest 27 슬롯.
 *
 * <ul>
 *   <li>슬롯 11: 램프 슬롯 (stack 가능)</li>
 *   <li>슬롯 13: 장비 슬롯 (단일)</li>
 *   <li>슬롯 15: 롤 버튼 (모루 아이콘)</li>
 *   <li>그 외 24슬롯: 회색 유리판 placeholder (클릭 차단)</li>
 * </ul>
 *
 * 인벤토리 holder 가 본 클래스 인스턴스라 BulkLampListener 가 빠르게 식별 가능.
 */
public class BulkLampGUI implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_LAMP   = 11;
    public static final int SLOT_TARGET = 13;
    public static final int SLOT_BUTTON = 15;

    public static final Component TITLE =
            Component.text("램프 작업대", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false);

    private final BulkLampKeys keys;
    private final EnchantStorage storage;
    private final Inventory inventory;

    public BulkLampGUI(BulkLampKeys keys, EnchantStorage storage) {
        this.keys = keys;
        this.storage = storage;
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE);
        fillPlaceholders();
        refreshButton();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** 플레이어에게 GUI 열기. */
    public void openFor(Player player) {
        player.openInventory(inventory);
    }

    /** 활성 슬롯(램프/장비/버튼) 이외 슬롯을 회색 유리판으로 채움. */
    private void fillPlaceholders() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(keys.guiPlaceholder, PersistentDataType.BYTE, (byte) 1);
        pane.setItemMeta(meta);

        for (int i = 0; i < SIZE; i++) {
            if (i == SLOT_LAMP || i == SLOT_TARGET || i == SLOT_BUTTON) continue;
            inventory.setItem(i, pane);
        }
    }

    /**
     * 버튼 슬롯에 모루 아이콘 배치. 동적으로 현재 장비의 인챈트 라인까지 lore 에 같이 넣어,
     * 사용자가 모루에 hover 한 상태에서 결과를 그 자리에서 확인할 수 있게 한다.
     * 슬롯이 바뀌거나 롤이 끝난 후 매번 다시 호출해서 새로고침.
     */
    public void refreshButton() {
        ItemStack btn = new ItemStack(Material.ANVIL);
        ItemMeta meta = btn.getItemMeta();
        meta.displayName(Component.text("롤 1회", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("클릭 시 램프 1개를 소비해서").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("장비에 새 인챈트를 부여합니다.").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("결과는 즉시 이 자리에 표시됩니다.").color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));

        // 현재 장비의 인챈트 라인 같이 표시
        ItemStack target = inventory.getItem(SLOT_TARGET);
        if (target != null && !target.getType().isAir()) {
            lore.add(Component.empty());
            lore.add(Component.text("─── 현재 인챈트 ───", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<RolledEnchant> rolled = storage.load(target);
            if (rolled.isEmpty()) {
                lore.add(Component.text("(없음)", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                for (int i = 0; i < rolled.size(); i++) {
                    RolledEnchant r = rolled.get(i);
                    Component line = r.enchant().renderLore(r.values())
                            .decoration(TextDecoration.ITALIC, false);
                    lore.add(Component.text((i + 1) + "줄  ", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
                            .append(line));
                }
            }
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(keys.guiRollButton, PersistentDataType.BYTE, (byte) 1);
        btn.setItemMeta(meta);
        inventory.setItem(SLOT_BUTTON, btn);
    }

    /** 현재 램프 슬롯의 ItemStack (null 또는 공기 가능). */
    public ItemStack getLamp() {
        return inventory.getItem(SLOT_LAMP);
    }

    /** 현재 장비 슬롯의 ItemStack (null 또는 공기 가능). */
    public ItemStack getTarget() {
        return inventory.getItem(SLOT_TARGET);
    }
}
