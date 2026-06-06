package com.exit.world.gui;

import com.exit.world.manager.WorldConfig;
import com.exit.world.manager.WorldManager;
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
 * /포탈 명령 시 열리는 월드 이동 GUI.
 * 인벤토리 9칸 — 마을(3번), 야생(5번) 슬롯. 던전은 던전 마스터 NPC 로 입장.
 *
 * 슬롯 배치 (0~8):
 * [ ][ ][ ][마을][ ][야생][ ][ ][ ]
 */
public class PortalGUI {

    // GUI 제목 — 이 문자열로 클릭 이벤트에서 GUI 식별
    public static final String GUI_TITLE = "✦ 포탈 — 이동할 월드를 선택하세요 ✦";

    // 각 월드의 슬롯 번호
    private static final int SLOT_VILLAGE = 3;
    private static final int SLOT_WILD    = 5;

    // 텍스처팩 전 임시 아이템
    private static final Material ICON_VILLAGE = Material.GRASS_BLOCK;
    private static final Material ICON_WILD    = Material.OAK_SAPLING;
    private static final Material ICON_EMPTY   = Material.GRAY_STAINED_GLASS_PANE;

    private final WorldManager worldManager;

    public PortalGUI(WorldManager worldManager) {
        this.worldManager = worldManager;
    }

    /**
     * 플레이어에게 포탈 GUI를 열어준다.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(
            null,
            9,
            Component.text(GUI_TITLE)
        );

        // 빈 슬롯 채우기
        ItemStack empty = makeEmpty();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, empty);
        }

        // 월드 아이콘 배치
        WorldConfig village = worldManager.getConfig("village");
        WorldConfig wild    = worldManager.getConfig("wild");

        if (village != null) inv.setItem(SLOT_VILLAGE, makeWorldIcon(ICON_VILLAGE, village, NamedTextColor.GREEN));
        if (wild    != null) inv.setItem(SLOT_WILD,    makeWorldIcon(ICON_WILD,    wild,    NamedTextColor.AQUA));

        player.openInventory(inv);
    }

    /**
     * 슬롯 번호로 어느 월드인지 반환. GUI 클릭 이벤트에서 사용.
     */
    public static String getWorldKeyBySlot(int slot) {
        return switch (slot) {
            case SLOT_VILLAGE -> "village";
            case SLOT_WILD    -> "wild";
            default           -> null;
        };
    }

    // ── 내부 아이템 생성 ────────────────────────────────────────

    private ItemStack makeWorldIcon(Material material, WorldConfig wc, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 이름
        meta.displayName(
            Component.text(wc.getDisplayName())
                .color(color)
                .decoration(TextDecoration.ITALIC, false)
        );

        // 설명 (lore)
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text(wc.getDescription())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));

        if (!wc.getWarning().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("⚠ " + wc.getWarning())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("클릭하여 이동")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeEmpty() {
        ItemStack item = new ItemStack(ICON_EMPTY);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
