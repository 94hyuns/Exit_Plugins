package com.exit.fishing.gui;

import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.Season;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryHolder;

/**
 * 어류 도감 GUI (4행, 36슬롯).
 *
 * - 4개 계절 탭 (슬롯 7, 16, 25, 34)
 * - 선택된 탭의 제철 어종 6종 디스플레이 (슬롯 9~14)
 * - 선택된 어종 상세: 슬롯 15 (물고기 모델), 슬롯 24 (설명 책)
 * - 슬롯 7 (우상단) : 닫기 버튼
 */
public class FishCodexGUI {

    public static final String TITLE_PREFIX = "어류 도감 - ";

    public static final int TAB_SPRING = 8;
    public static final int TAB_SUMMER = 17;
    public static final int TAB_AUTUMN = 26;
    public static final int TAB_WINTER = 35;
    public static final int SLOT_CLOSE = 7;
    public static final int SLOT_DETAIL_ITEM = 15;
    public static final int SLOT_DETAIL_BOOK = 24;

    private FishCodexGUI() {}

    public static void open(Player player, Season tab) {
        Inventory inv = Bukkit.createInventory(
                new FishCodexHolder(tab),
                36,
                Component.text(TITLE_PREFIX + tab.korean())
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)
        );

        ItemStack filler = filler();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // 제철 6종
        var list = FishRegistry.inSeason(tab);
        int[] fishSlots = {0, 1, 2, 3, 4, 5};
        for (int i = 0; i < list.size() && i < fishSlots.length; i++) {
            FishSpecies fs = list.get(i);
            inv.setItem(fishSlots[i], FishItem.displayOnly(fs));
        }

        // 탭 버튼
        inv.setItem(TAB_SPRING, tabButton(Season.SPRING, tab));
        inv.setItem(TAB_SUMMER, tabButton(Season.SUMMER, tab));
        inv.setItem(TAB_AUTUMN, tabButton(Season.AUTUMN, tab));
        inv.setItem(TAB_WINTER, tabButton(Season.WINTER, tab));

        // 닫기 버튼
        inv.setItem(SLOT_CLOSE, close());

        // 디테일 초기 상태 (빈 액자)
        inv.setItem(SLOT_DETAIL_ITEM, filler);
        inv.setItem(SLOT_DETAIL_BOOK, filler);

        player.openInventory(inv);
    }

    private static ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        it.editMeta(m -> m.displayName(Component.empty()));
        return it;
    }

    private static ItemStack close() {
        ItemStack it = new ItemStack(Material.BARRIER);
        it.editMeta(m -> m.displayName(Component.text("닫기", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)));
        return it;
    }

    private static ItemStack tabButton(Season s, Season active) {
        Material mat = switch (s) {
            case SPRING -> Material.PINK_DYE;
            case SUMMER -> Material.LIME_DYE;
            case AUTUMN -> Material.ORANGE_DYE;
            case WINTER -> Material.LIGHT_BLUE_DYE;
        };
        ItemStack it = new ItemStack(mat);
        it.editMeta(m -> {
            m.displayName(Component.text(s.korean() + (s == active ? " (선택됨)" : ""))
                    .color(s == active ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
        });
        return it;
    }

    public static void showDetail(Inventory inv, FishSpecies species) {
        inv.setItem(SLOT_DETAIL_ITEM, FishItem.displayOnly(species));
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        book.editMeta(m -> {
            m.displayName(Component.text(species.koreanName() + " 도감")
                    .color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            m.lore(java.util.List.of(
                    Component.text(species.description(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("제철 : ", NamedTextColor.WHITE)
                            .append(Component.text(species.season().korean(), NamedTextColor.AQUA))
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });
        inv.setItem(SLOT_DETAIL_BOOK, book);
    }

    public static Season tabOf(int slot) {
        return switch (slot) {
            case TAB_SPRING -> Season.SPRING;
            case TAB_SUMMER -> Season.SUMMER;
            case TAB_AUTUMN -> Season.AUTUMN;
            case TAB_WINTER -> Season.WINTER;
            default -> null;
        };
    }

    public static class FishCodexHolder implements InventoryHolder {
        private final Season tab;
        public FishCodexHolder(Season tab) { this.tab = tab; }
        public Season tab() { return tab; }
        @Override public Inventory getInventory() { return null; }
    }
}
