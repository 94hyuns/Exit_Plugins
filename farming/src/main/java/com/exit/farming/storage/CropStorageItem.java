package com.exit.farming.storage;

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
 * "농부의 보관함" 아이템. Job 의 mineral/fish storage 와 동일 패턴.
 */
public final class CropStorageItem {

    public static NamespacedKey markerKey(Plugin plugin) {
        return new NamespacedKey(plugin, "crop_storage_item");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("농부의 보관함")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false));
        meta.lore(List.of(
                Component.text("우클릭으로 열기").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("작물만 보관 가능").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("작물 상인에서 일괄 판매").color(NamedTextColor.DARK_GRAY)
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

    private CropStorageItem() {}
}
