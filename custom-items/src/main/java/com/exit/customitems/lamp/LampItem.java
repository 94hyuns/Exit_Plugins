package com.exit.customitems.lamp;

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
 * 램프 아이템의 생성과 식별. 1단계에서는 책(BOOK) + CustomModelData + PDC 마커로 구분.
 * 리소스팩은 2단계에서 CustomModelData 1001/1002에 텍스처만 매핑하면 된다.
 */
public final class LampItem {

    private final LampKeys keys;

    public LampItem(LampKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(LampType type, int amount) {
        ItemStack item = new ItemStack(Material.BOOK, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(type.getDisplayName())
            .color(type.getColor())
            .decoration(TextDecoration.ITALIC, false));

        java.util.List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text(type.getDescription())
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("도구를 보조손에 들고 우클릭")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false));
        if (type == LampType.MUTATION) {
            lore.add(Component.text("⚠ 한 번 적용된 장비는 더 이상 램프 작업을 할 수 없습니다")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        meta.setCustomModelData(type.getCustomModelData());

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.lampType, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    /** 이 아이템이 어떤 램프인지. 램프가 아니면 null. */
    public LampType getTypeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(keys.lampType, PersistentDataType.STRING);
        return LampType.fromString(raw);
    }

    public boolean isLamp(ItemStack item) {
        return getTypeOf(item) != null;
    }
}
