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
 * 빅맥 — 커스텀 음식.
 *
 * <p>Base material COOKED_BEEF + CustomModelData 10002 + PDC 식별.
 * 리소스팩(ExitItemPack 의 cooked_beef.json range_dispatch) 가 CMD=10002 일 때
 * bigmac_burger 모델/텍스처를 적용. 바닐라 식사 동작(식료 8 회복)은 그대로.
 */
public final class BigMacItem {

    /** Provider typeId. */
    public static final String TYPE_ID = "BIGMAC";
    /** 리소스팩 매칭용 CustomModelData. */
    public static final int CUSTOM_MODEL_DATA = 10002;

    private final ConsumableKeys keys;

    public BigMacItem(ConsumableKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.COOKED_BEEF, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("빅맥")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("황금빛 더블패티 빅맥.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // CustomModelData 10002 → 리소스팩의 bigmac_burger 모델 매핑
        var cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, TYPE_ID);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isBigMac(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_ID.equals(pdc.get(keys.type, PersistentDataType.STRING));
    }
}
