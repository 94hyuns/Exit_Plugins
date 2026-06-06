package com.exit.job.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * "광부의 보관함" 아이템. 우클릭으로 GUI 오픈. 데이터는 per-player .yml 저장이라
 * 이 ItemStack 은 키 역할 — 잃어버려도 재지급 시 데이터 그대로.
 *
 * <p>Material: BUNDLE (vanilla 동작은 비활성, PDC 마커로 식별).
 */
public final class MineralStorageItem {

    public static NamespacedKey markerKey(Plugin plugin) {
        return new NamespacedKey(plugin, "mineral_storage_item");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("광부의 보관함")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false));
        meta.lore(List.of(
                Component.text("우클릭으로 열기").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("광물만 보관 가능").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("광물 상인에서 일괄 판매").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(markerKey(plugin), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isStorage(ItemStack stack, Plugin plugin) {
        if (stack == null || stack.getType() != Material.BUNDLE || !stack.hasItemMeta()) return false;
        return stack.getItemMeta().getPersistentDataContainer()
                .has(markerKey(plugin), PersistentDataType.BYTE);
    }

    private MineralStorageItem() {}
}
