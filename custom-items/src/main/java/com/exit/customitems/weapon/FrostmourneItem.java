package com.exit.customitems.weapon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import io.papermc.paper.datacomponent.DataComponentTypes;
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
 * 프로스트모운 — 보스 드랍 무기 (NETHERITE_SWORD + CMD 11001 + PDC 식별).
 *
 * <p>능동 스킬 3종:
 * <ul>
 *   <li>우클릭 — 빙판 돌진 (8초 쿨, 10블럭, FROSTED_ICE 자국)</li>
 *   <li>Shift+우클릭 — 빙결의 슬램 (15초 쿨, 광역 ×10 데미지)</li>
 *   <li>Shift+좌클릭 — 빙결의 숨결 [궁극기] (60초 쿨, 5초 빔 ×10 데미지)</li>
 * </ul>
 *
 * <p>식별은 PDC {@code weapon_type=FROSTMOURNE} 만으로. 스킬 로직은 listener 가 동적
 * 처리하므로 기존 인스턴스에도 새 스킬이 자동 적용됨. lore 만 ItemStack 에 박혀 있어
 * {@link #migrateLore(ItemStack)} 으로 최신 버전 갱신.
 */
public final class FrostmourneItem {

    public static final String TYPE_ID = "FROSTMOURNE";
    public static final int CUSTOM_MODEL_DATA = 11001;

    /** lore 버전 — 스킬 확장 시 +1. PDC frostmourneLoreVersion 에 저장. */
    public static final int CURRENT_LORE_VERSION = 2;

    /** 공격피해 +20 modifier (최상위 보스 리워드). */
    private static final double ATTACK_DAMAGE_ADD = 20.0;
    /** 공격속도 표시 2.0 = vanilla base 4 + (-2.0) modifier. */
    private static final double ATTACK_SPEED_ADD = -2.0;

    private final WeaponKeys keys;

    public FrostmourneItem(WeaponKeys keys) {
        this.keys = keys;
    }

    public ItemStack create(int amount) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD, amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("프로스트모운")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        applyCurrentLore(meta);

        // vanilla attribute_modifier 자동 표시("+X 공격피해 / -Y 공격속도") 숨김 — lore 로 직접 표기
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        var cmd = meta.getCustomModelDataComponent();
        cmd.setFloats(List.of((float) CUSTOM_MODEL_DATA));
        meta.setCustomModelDataComponent(cmd);

        EquipmentSlotGroup hand = EquipmentSlotGroup.MAINHAND;
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(keys.frostmourneDamageMod, ATTACK_DAMAGE_ADD,
                        AttributeModifier.Operation.ADD_NUMBER, hand));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(keys.frostmourneSpeedMod, ATTACK_SPEED_ADD,
                        AttributeModifier.Operation.ADD_NUMBER, hand));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.type, PersistentDataType.STRING, TYPE_ID);
        pdc.set(keys.frostmourneLoreVersion, PersistentDataType.INTEGER, CURRENT_LORE_VERSION);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isFrostmourne(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return TYPE_ID.equals(pdc.get(keys.type, PersistentDataType.STRING));
    }

    /**
     * 옛 프로스트모운 인스턴스의 lore 를 현재 버전으로 갱신.
     * idempotent — 이미 최신 버전이면 no-op.
     */
    public void migrateLore(ItemStack item) {
        if (!isFrostmourne(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer ver = pdc.get(keys.frostmourneLoreVersion, PersistentDataType.INTEGER);
        if (ver != null && ver >= CURRENT_LORE_VERSION) return;
        applyCurrentLore(meta);
        pdc.set(keys.frostmourneLoreVersion, PersistentDataType.INTEGER, CURRENT_LORE_VERSION);
        item.setItemMeta(meta);
        // 옛 인스턴스는 우클릭 hold 빔용 Consumable 컴포넌트가 박혀 있을 수 있음.
        // 새 매핑(우클릭=돌진)에서는 consume 애니메이션이 방해되므로 제거.
        item.unsetData(DataComponentTypes.CONSUMABLE);
    }

    private static void applyCurrentLore(ItemMeta meta) {
        meta.lore(List.of(
                Component.text("얼어붙은 영혼의 검.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("칼날이 푸르게 빛난다.")
                        .color(NamedTextColor.DARK_AQUA)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("+20 공격피해")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("2.0 공격속도")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("우클릭: 빙판 돌진 — 10블럭 (8초)")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Shift+우클릭: 빙결의 슬램 — 광역 (15초)")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Shift+좌클릭: 빙결의 숨결 [궁극기] (60초)")
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false)
        ));
    }
}
