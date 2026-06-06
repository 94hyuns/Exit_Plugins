package com.exit.core.admininspect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ReadOnlyStorageView implements InventoryHolder {

    public static final int PAGE_SIZE = 45;
    public static final int SLOT_PREV = 45;
    public static final int SLOT_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private final String targetName;
    private final String label;
    private final ItemStack[] data;
    private final int totalPages;
    private int page;
    private Inventory inventory;

    public ReadOnlyStorageView(String targetName, String label, ItemStack[] data) {
        this.targetName = targetName;
        this.label = label;
        this.data = data != null ? data : new ItemStack[0];
        this.totalPages = Math.max(1, (this.data.length + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer, int page) {
        this.page = Math.max(0, Math.min(page, totalPages - 1));
        Component title = AdminInspectHub.plain(targetName + " 의 " + label
                + (totalPages > 1 ? " (" + (this.page + 1) + "/" + totalPages + ")" : ""));
        inventory = Bukkit.createInventory(this, 54, title);

        int start = this.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, data.length);
        for (int i = start; i < end; i++) {
            ItemStack it = data[i];
            if (it == null || it.getType() == Material.AIR) continue;
            inventory.setItem(i - start, it.clone());
        }

        ItemStack filler = filler();
        for (int i = PAGE_SIZE; i < 54; i++) inventory.setItem(i, filler);

        if (totalPages > 1) {
            if (this.page > 0) inventory.setItem(SLOT_PREV, navButton(Material.ARROW, "이전 페이지"));
            if (this.page < totalPages - 1) inventory.setItem(SLOT_NEXT, navButton(Material.ARROW, "다음 페이지"));
        }
        inventory.setItem(SLOT_INFO, infoButton());

        viewer.openInventory(inventory);
    }

    public int getPage() { return page; }
    public int getTotalPages() { return totalPages; }
    public String getTargetName() { return targetName; }
    public String getLabel() { return label; }
    public ItemStack[] getData() { return data; }

    private ItemStack navButton(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(AdminInspectHub.plain(name).color(NamedTextColor.YELLOW));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack infoButton() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(AdminInspectHub.plain(label).color(NamedTextColor.AQUA));
            List<Component> lore = new ArrayList<>();
            lore.add(AdminInspectHub.plain("읽기 전용").color(NamedTextColor.GRAY));
            int count = 0;
            for (ItemStack s : data) if (s != null && s.getType() != Material.AIR) count += s.getAmount();
            lore.add(AdminInspectHub.plain("총 " + count + "개").color(NamedTextColor.GRAY));
            meta.lore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack filler() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(AdminInspectHub.plain(" "));
            it.setItemMeta(meta);
        }
        return it;
    }
}
