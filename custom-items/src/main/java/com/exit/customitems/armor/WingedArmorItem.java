package com.exit.customitems.armor;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 용의 날개 — 활공 가능한 커스텀 갑옷 (Elytra 기반).
 *
 * <p>Base ELYTRA + CMD 11003 + Equippable asset 변경 (등 뒤 dragon wings 외형).
 * 활공 / 내구도 소모는 vanilla 자동. 방어 10 / 강도 4 / 넉백저항 0.1 attribute.
 */
public final class WingedArmorItem {

    public static final String TYPE_ID = "DRAGON_WINGS";
    public static final int CUSTOM_MODEL_DATA = 11003;

    /** vanilla ELYTRA 는 armor 0 — modifier 로 풀 수치 명시. */
    private static final double ARMOR_VALUE = 10.0;
    private static final double TOUGHNESS_VALUE = 4.0;
    private static final double KNOCKBACK_RESISTANCE_VALUE = 0.1;

    private static final Key DRAGON_WINGS_ASSET = Key.key("customitems", "dragon_wings");
    private static final Key ELYTRA_EQUIP_SOUND = Key.key("minecraft", "item.armor.equip_elytra");

    private final ArmorKeys keys;

    public WingedArmorItem(ArmorKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.NETHERITE_CHESTPLATE, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("용의 날개")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("고대 용의 날개로 만들어진 갑옷.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("하늘을 가른다.")
                        .color(NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("+10 방어")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("+4 방어 강도")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("높은 곳에서 점프 + Space 키로 활공")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        var cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        EquipmentSlotGroup chest = EquipmentSlotGroup.CHEST;
        meta.addAttributeModifier(Attribute.ARMOR,
                new AttributeModifier(keys.wingedArmorArmorMod, ARMOR_VALUE,
                        AttributeModifier.Operation.ADD_NUMBER, chest));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(keys.wingedArmorToughnessMod, TOUGHNESS_VALUE,
                        AttributeModifier.Operation.ADD_NUMBER, chest));
        meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE,
                new AttributeModifier(keys.wingedArmorKnockbackMod, KNOCKBACK_RESISTANCE_VALUE,
                        AttributeModifier.Operation.ADD_NUMBER, chest));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, TYPE_ID);

        item.setItemMeta(meta);

        // Equippable: chest slot + dragon_wings asset (chestplate 외형 + 등 뒤 dragon wings)
        item.setData(DataComponentTypes.EQUIPPABLE,
                Equippable.equippable(EquipmentSlot.CHEST)
                        .assetId(DRAGON_WINGS_ASSET)
                        .equipSound(ELYTRA_EQUIP_SOUND)
                        .damageOnHurt(true)
                        .swappable(true)
                        .build());

        // Glider — 활공 가능 (chestplate 베이스라 명시 부여 필요. ELYTRA 는 vanilla 가 자동)
        item.setData(DataComponentTypes.GLIDER);

        return item;
    }

    public boolean isWingedArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_ID.equals(pdc.get(keys.type, PersistentDataType.STRING));
    }
}
