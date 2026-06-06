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
 * 던전 마스터 NPC 우클릭 시 열리는 GUI.
 * 9×6. 상단 1줄 = 던전/보스 탭, 본문 4줄 = 항목, 하단 1줄 = 닫기.
 *
 * GUI title 에 현재 탭 키를 인코딩하여 inventory click 분기에 사용:
 *   "✦ 던전 마스터 — 던전 ✦" / "✦ 던전 마스터 — 보스 ✦"
 */
public class DungeonTabGUI {

    public static final String TITLE_BASE = "✦ 던전 마스터 — ";
    public static final String TITLE_SUFFIX = " ✦";

    private static final int SLOT_TAB_DUNGEON = 2;
    private static final int SLOT_TAB_BOSS = 6;
    private static final int SLOT_CLOSE = 49;

    // 본문 4줄 — 각 줄 가운데 1칸씩 (slot 13, 22, 31, 40)
    private static final int[] ENTRY_SLOTS = { 13, 22, 31, 40 };

    private static final Material ICON_EMPTY = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material ICON_TAB_DUNGEON = Material.WITHER_SKELETON_SKULL;
    private static final Material ICON_TAB_BOSS = Material.DRAGON_HEAD;
    private static final Material ICON_CLOSE = Material.BARRIER;

    private final DungeonRegistry registry;

    public DungeonTabGUI(DungeonRegistry registry) {
        this.registry = registry;
    }

    public void open(Player player, DungeonEntry.Tab tab) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text(TITLE_BASE + tabLabel(tab) + TITLE_SUFFIX));

        ItemStack filler = makeFiller();
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        inv.setItem(SLOT_TAB_DUNGEON, makeTabIcon(DungeonEntry.Tab.DUNGEON, tab));
        inv.setItem(SLOT_TAB_BOSS, makeTabIcon(DungeonEntry.Tab.BOSS, tab));
        inv.setItem(SLOT_CLOSE, makeClose());

        List<DungeonEntry> list = registry.getByTab(tab);
        int autoIdx = 0;
        for (DungeonEntry e : list) {
            int slot;
            if (e.guiSlot() >= 0) {
                slot = e.guiSlot();
            } else {
                if (autoIdx >= ENTRY_SLOTS.length) continue;
                slot = ENTRY_SLOTS[autoIdx++];
            }
            inv.setItem(slot, makeEntryIcon(e));
        }

        player.openInventory(inv);
    }

    public static DungeonEntry.Tab parseTabFromTitle(String title) {
        if (title == null || !title.startsWith(TITLE_BASE) || !title.endsWith(TITLE_SUFFIX)) return null;
        String label = title.substring(TITLE_BASE.length(), title.length() - TITLE_SUFFIX.length());
        return switch (label) {
            case "던전" -> DungeonEntry.Tab.DUNGEON;
            case "보스" -> DungeonEntry.Tab.BOSS;
            default     -> null;
        };
    }

    public static boolean isTabSlot(int slot, DungeonEntry.Tab[] out) {
        if (slot == SLOT_TAB_DUNGEON) { out[0] = DungeonEntry.Tab.DUNGEON; return true; }
        if (slot == SLOT_TAB_BOSS)    { out[0] = DungeonEntry.Tab.BOSS;    return true; }
        return false;
    }

    public static boolean isCloseSlot(int slot) { return slot == SLOT_CLOSE; }

    /** slot → list index (해당 탭의 entries 인덱스). 해당 슬롯이 entry 슬롯이 아니면 -1. */
    public static int entryIndexBySlot(int slot) {
        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            if (ENTRY_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    /**
     * 클릭한 slot 으로 어느 DungeonEntry 인지 직접 찾기.
     * entry.guiSlot 명시된 항목 우선, 없으면 ENTRY_SLOTS 순차 매핑.
     * 해당 slot 에 entry 없으면 null.
     */
    public static DungeonEntry entryAtSlot(int slot, List<DungeonEntry> entries) {
        int autoIdx = 0;
        for (DungeonEntry e : entries) {
            int s;
            if (e.guiSlot() >= 0) {
                s = e.guiSlot();
            } else {
                if (autoIdx >= ENTRY_SLOTS.length) continue;
                s = ENTRY_SLOTS[autoIdx++];
            }
            if (s == slot) return e;
        }
        return null;
    }

    // ── 아이템 생성 ────────────────────────────────────────────

    private String tabLabel(DungeonEntry.Tab tab) {
        return tab == DungeonEntry.Tab.DUNGEON ? "던전" : "보스";
    }

    private ItemStack makeTabIcon(DungeonEntry.Tab tab, DungeonEntry.Tab current) {
        Material mat = tab == DungeonEntry.Tab.DUNGEON ? ICON_TAB_DUNGEON : ICON_TAB_BOSS;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = (tab == current) ? NamedTextColor.GOLD : NamedTextColor.GRAY;
        meta.displayName(Component.text(tabLabel(tab) + (tab == current ? " (선택됨)" : ""))
                .color(color)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(tab == current ? "현재 탭" : "클릭하여 전환")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeEntryIcon(DungeonEntry e) {
        ItemStack item = new ItemStack(e.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(e.displayName())
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("입장 비용: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d", e.cost()) + " w").color(NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        if (e.description() != null && !e.description().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text(e.description()).color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("클릭하여 입장").color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeFiller() {
        ItemStack item = new ItemStack(ICON_EMPTY);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeClose() {
        ItemStack item = new ItemStack(ICON_CLOSE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("닫기").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
