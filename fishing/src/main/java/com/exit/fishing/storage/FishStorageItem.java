package com.exit.fishing.storage;

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
 * "어부의 보관함" 아이템. 우클릭으로 GUI 오픈. 데이터는 per-player .yml 에 저장되므로
 * 이 ItemStack 자체는 키 역할 — 잃어버려도 재지급 시 데이터 그대로.
 *
 * <p>Material: BUNDLE (시각적 깔끔함, vanilla bundle 기능과 충돌 회피용 PDC 마커).
 */
public final class FishStorageItem {

    public static NamespacedKey markerKey(Plugin plugin) {
        return new NamespacedKey(plugin, "fish_storage_item");
    }

    public static ItemStack create(Plugin plugin) {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("어부의 보관함")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false));
        meta.lore(List.of(
                Component.text("우클릭으로 열기").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("물고기만 보관 가능").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("판매 불가").color(NamedTextColor.DARK_GRAY)
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

    private FishStorageItem() {}
}
