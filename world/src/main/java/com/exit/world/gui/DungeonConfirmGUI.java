package com.exit.world.gui;

import com.exit.world.manager.DungeonEntry;
import com.exit.world.manager.DungeonRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 던전 입장 확인 GUI. 9칸. [예][ ][ ][ ][정보][ ][ ][ ][아니오]
 *
 * GUI title 에 dungeon key 를 인코딩:
 *   "✦ 입장 확인 — <key> ✦"
 */
public class DungeonConfirmGUI {

    public static final String TITLE_PREFIX = "✦ 입장 확인 — ";
    public static final String TITLE_SUFFIX = " ✦";

    public static final int SLOT_YES = 0;
    public static final int SLOT_INFO = 4;
    public static final int SLOT_NO = 8;

    private final DungeonRegistry registry;

    public DungeonConfirmGUI(DungeonRegistry registry) {
        this.registry = registry;
    }

    public void open(Player player, DungeonEntry entry) {
        Inventory inv = Bukkit.createInventory(null, 9,
                Component.text(TITLE_PREFIX + entry.key() + TITLE_SUFFIX));

        ItemStack filler = makeFiller();
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_YES, makeYes(entry));
        inv.setItem(SLOT_INFO, makeInfo(entry));
        inv.setItem(SLOT_NO, makeNo());

        player.openInventory(inv);
    }

    /** title 에서 dungeon key 추출. 매치 안 되면 null. */
    public static String parseKey(String title) {
        if (title == null || !title.startsWith(TITLE_PREFIX) || !title.endsWith(TITLE_SUFFIX)) return null;
        return title.substring(TITLE_PREFIX.length(), title.length() - TITLE_SUFFIX.length());
    }

    private ItemStack makeYes(DungeonEntry e) {
        ItemStack item = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§a예 — 입장")
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format("%,d", e.cost()) + " w 가 차감됩니다")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeInfo(DungeonEntry e) {
        ItemStack item = new ItemStack(e.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(e.displayName())
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("입장 비용: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d", e.cost()) + " w").color(NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeNo() {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§c아니오 — 취소")
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
