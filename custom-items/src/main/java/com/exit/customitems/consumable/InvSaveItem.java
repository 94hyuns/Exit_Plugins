package com.exit.customitems.consumable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 인벤세이브권.
 *
 * <p>Base material PAPER + CustomModelData 10001 + PDC 식별.
 * 리소스팩(InventorySaveAngel_SelectedPack) 의 paper.json override 가
 * CMD=10001 일 때 inventory_save_angel 모델을 적용.
 */
public final class InvSaveItem {

    /** Provider typeId. */
    public static final String TYPE_ID = "INV_SAVE";
    /** 리소스팩 매칭용 CustomModelData. */
    public static final int CUSTOM_MODEL_DATA = 10001;

    private final ConsumableKeys keys;

    public InvSaveItem(ConsumableKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("인벤세이브권")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("사망 시 인벤토리를 지켜주는 수호천사의 권능")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // CustomModelData 10001 → 리소스팩의 inventory_save_angel 모델 매핑
        var cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, TYPE_ID);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isInvSave(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_ID.equals(pdc.get(keys.type, PersistentDataType.STRING));
    }
}
