package com.exit.customitems.weapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * GreatSword — 중간단계 커스텀 검 (보스 진행 보상 후보).
 *
 * <p>Base NETHERITE_SWORD + CMD 11002 + PDC 식별. 우클릭 스킬 없음, 순수 스탯 강화.
 */
public final class GreatSwordItem {

    public static final String TYPE_ID = "GREATSWORD";
    public static final int CUSTOM_MODEL_DATA = 11002;

    /** 공격피해 +14 modifier (중간단계). */
    private static final double ATTACK_DAMAGE_ADD = 14.0;
    /** 공격속도 표시 1.6 = vanilla base 4 + (-2.4) modifier. */
    private static final double ATTACK_SPEED_ADD = -2.4;

    private final WeaponKeys keys;

    public GreatSwordItem(WeaponKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("GreatSword")
                .color(NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("거대한 흑철 양손검.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("묵직한 한 방을 자랑한다.")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("+14 공격피해")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("1.6 공격속도")
                        .color(NamedTextColor.DARK_GREEN)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("우클릭: 슬램 — 평타 데미지의 10배 광역")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("쿨다운 15초")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        var cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(keys.greatswordDamageMod, ATTACK_DAMAGE_ADD,
                        AttributeModifier.Operation.ADD_NUMBER, hand));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(keys.greatswordSpeedMod, ATTACK_SPEED_ADD,
                        AttributeModifier.Operation.ADD_NUMBER, hand));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, TYPE_ID);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isGreatSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_ID.equals(pdc.get(keys.type, PersistentDataType.STRING));
    }
}
