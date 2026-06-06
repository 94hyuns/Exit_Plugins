package com.exit.core.admininspect;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public class ReadOnlyInventoryView implements InventoryHolder {

    private Inventory inventory;

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public void open(Player viewer, Player target) {
        Component title = AdminInspectHub.plain(target.getName() + " 의 인벤토리");
        inventory = Bukkit.createInventory(this, 45, title);

        PlayerInventory pi = target.getInventory();

        // 0-26: main storage (target slots 9-35)
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, cloneOrNull(pi.getItem(9 + i)));
        }
        // 27-35: hotbar (target slots 0-8)
        for (int i = 0; i < 9; i++) {
            inventory.setItem(27 + i, cloneOrNull(pi.getItem(i)));
        }
        // 36-39: armor (boots, leggings, chestplate, helmet → 정렬: 헬멧→부츠 순)
        ItemStack[] armor = pi.getArmorContents();
        // armor[0]=boots, [1]=leggings, [2]=chestplate, [3]=helmet
        inventory.setItem(36, cloneOrNull(armor[3])); // helmet
        inventory.setItem(37, cloneOrNull(armor[2])); // chestplate
        inventory.setItem(38, cloneOrNull(armor[1])); // leggings
        inventory.setItem(39, cloneOrNull(armor[0])); // boots
        // 40: offhand
        inventory.setItem(40, cloneOrNull(pi.getItemInOffHand()));
        // 41-44: filler
        ItemStack filler = filler();
        for (int i = 41; i < 45; i++) inventory.setItem(i, filler);

        viewer.openInventory(inventory);
    }

    private ItemStack cloneOrNull(ItemStack src) {
        if (src == null || src.getType() == Material.AIR) return null;
        return src.clone();
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
