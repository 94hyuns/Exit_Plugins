package com.exit.job.storage;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 광부 보관함 GUI. 6행 chest, 페이지네이션 지원.
 *
 * <p>레이아웃:
 * <ul>
 *   <li>슬롯 4: 안내 (NAME_TAG)</li>
 *   <li>슬롯 7: 자동수집 토글</li>
 *   <li>그 외 0-8: GRAY_STAINED_GLASS_PANE 분리</li>
 *   <li>슬롯 9~44: 저장 슬롯 (페이지 슬롯 0~35) — 4 행</li>
 *   <li>슬롯 45: 이전 페이지 버튼 (PAPER + CMD 5)</li>
 *   <li>슬롯 46~52: 저장 슬롯 (페이지 슬롯 36~42) — 6 행 가운데 7칸</li>
 *   <li>슬롯 53: 다음 페이지 버튼 (PAPER + CMD 6)</li>
 * </ul>
 * 페이지당 43 슬롯. 광부 Lv6+ 부터 4 페이지로 확장.
 *
 * <p>Holder 가 currentPage + data 배열 (전체 페이지 통합) 보유 — 페이지 전환 시 in-memory 유지.
 */
public class MineralStorageGUI {

    public static final int STORAGE_OFFSET = 9;
    public static final int SLOT_TOGGLE = 7;
    public static final int SLOT_PREV_PAGE = 45;
    public static final int SLOT_NEXT_PAGE = 53;
    public static final NamespacedKey ACTION_KEY =
            NamespacedKey.fromString("job:mineral_storage_action");

    private final Plugin plugin;
    private final MineralStorageManager manager;

    public MineralStorageGUI(Plugin plugin, MineralStorageManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /** 페이지 슬롯 인덱스 (0..42) → GUI 슬롯. */
    public static int pageSlotToGuiSlot(int pageSlot) {
        if (pageSlot < 0) return -1;
        if (pageSlot < 36) return STORAGE_OFFSET + pageSlot;        // 9..44
        if (pageSlot < 43) return 46 + (pageSlot - 36);              // 46..52
        return -1;
    }

    /** GUI 슬롯 → 페이지 슬롯 인덱스. nav/UI 등 비저장 슬롯은 -1. */
    public static int guiSlotToPageSlot(int guiSlot) {
        if (guiSlot >= STORAGE_OFFSET && guiSlot <= 44) return guiSlot - STORAGE_OFFSET;  // 0..35
        if (guiSlot >= 46 && guiSlot <= 52) return 36 + (guiSlot - 46);                    // 36..42
        return -1;
    }

    public void open(Player player) {
        Holder holder = new Holder(player.getUniqueId());
        holder.setData(manager.load(player.getUniqueId()));
        holder.setCurrentPage(0);
        renderInto(holder, player);
    }

    /** Holder 의 현재 페이지 / data 를 새 Inventory 로 렌더링하고 플레이어에게 오픈. */
    public void renderInto(Holder holder, Player player) {
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("광부의 보관함")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, false));
        holder.bind(inv);

        int totalPages = manager.pagesFor(player.getUniqueId());
        int page = Math.max(0, Math.min(holder.getCurrentPage(), totalPages - 1));
        holder.setCurrentPage(page);

        // ─── 상단 UI (0~8) ───
        ItemStack info = new ItemStack(Material.NAME_TAG);
        ItemMeta im = info.getItemMeta();
        im.displayName(Component.text("사용 안내")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        im.lore(List.of(
                Component.text("광물만 보관 가능").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("닫으면 자동 저장").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("광물 상인에서 일괄 판매").color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("페이지 " + (page + 1) + " / " + totalPages)
                        .color(NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        info.setItemMeta(im);
        inv.setItem(4, info);

        for (int i = 0; i < 9; i++) {
            if (i == 4 || i == SLOT_TOGGLE) continue;
            inv.setItem(i, panel());
        }
        inv.setItem(SLOT_TOGGLE, toggleButton(manager.isAutoCollect(player.getUniqueId())));

        // ─── 페이지 저장 슬롯 (43개) ───
        ItemStack[] data = holder.getData();
        int pageStart = page * MineralStorageManager.PAGE_SIZE;
        for (int i = 0; i < MineralStorageManager.PAGE_SIZE; i++) {
            int guiSlot = pageSlotToGuiSlot(i);
            int dataIdx = pageStart + i;
            if (data != null && dataIdx < data.length) {
                inv.setItem(guiSlot, data[dataIdx]);
            }
        }

        // ─── nav 버튼 ───
        inv.setItem(SLOT_PREV_PAGE, navButton(page > 0, true, page, totalPages));
        inv.setItem(SLOT_NEXT_PAGE, navButton(page < totalPages - 1, false, page, totalPages));

        player.openInventory(inv);
    }

    /** 현재 GUI 의 페이지 슬롯들을 Holder.data 의 해당 구간으로 dump. */
    public static void dumpPageToData(Holder holder, Inventory inv) {
        if (inv == null) return;
        int page = holder.getCurrentPage();
        int pageStart = page * MineralStorageManager.PAGE_SIZE;
        ItemStack[] data = holder.getData();
        if (data == null) return;
        for (int i = 0; i < MineralStorageManager.PAGE_SIZE; i++) {
            int dataIdx = pageStart + i;
            if (dataIdx >= data.length) break;
            ItemStack s = inv.getItem(pageSlotToGuiSlot(i));
            data[dataIdx] = (s == null || s.getType().isAir()) ? null : s;
        }
    }

    private ItemStack navButton(boolean active, boolean prev, int curPage, int totalPages) {
        // 활성: PAPER + Shop 과 동일한 CMD (prev=5 / next=6) — 리소스팩 모델 공유.
        // 비활성: GRAY_STAINED_GLASS_PANE (CMD 없음).
        Material mat = active ? Material.PAPER : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (active) {
            meta.setCustomModelData(prev ? 5 : 6);
        }
        String label = prev ? "이전 페이지" : "다음 페이지";
        meta.displayName(Component.text(label)
                .color(active ? NamedTextColor.YELLOW : NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false));
        meta.lore(List.of(
                Component.text("(" + (curPage + 1) + " / " + totalPages + ")")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(active ? "클릭으로 이동" : "더 이상 페이지 없음")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY,
                PersistentDataType.STRING, prev ? "PAGE_PREV" : "PAGE_NEXT");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack panel() {
        ItemStack stack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(" "));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 토글 버튼 빌더. listener 가 in-place 갱신용으로 호출 (복사 버그 방지). */
    public ItemStack buildToggleButton(boolean on) {
        return toggleButton(on);
    }

    private ItemStack toggleButton(boolean on) {
        ItemStack stack = new ItemStack(on ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("자동 수집: ")
                .color(NamedTextColor.GOLD)
                .append(Component.text(on ? "켜짐" : "꺼짐")
                        .color(on ? NamedTextColor.GREEN : NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false));
        meta.lore(List.of(
                Component.text(on ? "캔 광물이 보관함으로 직접 들어감" : "클릭해서 켜기")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("EXP 는 정상 부여").color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY,
                PersistentDataType.STRING, "TOGGLE_AUTO_COLLECT");
        stack.setItemMeta(meta);
        return stack;
    }

    /** GUI 식별용 InventoryHolder. 페이지네이션 상태 (currentPage + data) 보유. */
    public static class Holder implements InventoryHolder {
        private final java.util.UUID owner;
        private Inventory inv;
        private int currentPage = 0;
        private ItemStack[] data;
        public Holder(java.util.UUID owner) { this.owner = owner; }
        public void bind(Inventory inv) { this.inv = inv; }
        public java.util.UUID getOwner() { return owner; }
        @Override public Inventory getInventory() { return inv; }
        public int getCurrentPage() { return currentPage; }
        public void setCurrentPage(int page) { this.currentPage = page; }
        public ItemStack[] getData() { return data; }
        public void setData(ItemStack[] data) { this.data = data; }
    }
}
