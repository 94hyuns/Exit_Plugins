package com.exit.economy.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class CashNoteFactory {

    private static final NamespacedKey KEY_CASH_NOTE = new NamespacedKey("economy", "cash_note");
    private static final NamespacedKey KEY_CASH_AMOUNT = new NamespacedKey("economy", "cash_amount");

    public static ItemStack createNote(JavaPlugin plugin, long amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(
            Component.text(amount + "w")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(List.of(
            Component.text("울캐쉬 " + amount + "w").color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("우클릭으로 자동 입금됩니다").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_CASH_NOTE, PersistentDataType.BOOLEAN, true);
        pdc.set(KEY_CASH_AMOUNT, PersistentDataType.LONG, amount);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCashNote(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(KEY_CASH_NOTE, PersistentDataType.BOOLEAN);
    }

    public static long getAmount(ItemStack item) {
        if (!isCashNote(item)) return 0L;
        Long amount = item.getItemMeta().getPersistentDataContainer()
            .get(KEY_CASH_AMOUNT, PersistentDataType.LONG);
        return amount != null ? amount : 0L;
    }
}
