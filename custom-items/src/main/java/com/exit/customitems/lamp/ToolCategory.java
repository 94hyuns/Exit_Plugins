package com.exit.customitems.lamp;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

/**
 * 도구/방어구의 카테고리 판정 유틸. 램프 적용 가능 여부와 인챈트 canApplyTo 구현에 사용.
 *
 * <p><b>분류 기준 (확정):</b>
 * <ul>
 *   <li>생활 도구: 곡괭이, 괭이, 삽, 낚싯대, 도끼</li>
 *   <li>전투 무기: 검, 활, 쇠뇌, 삼지창, 철퇴</li>
 *   <li>방어구: 헬멧, 흉갑, 레깅스, 부츠 (전투 램프 적용)</li>
 *   <li>제외: 방패</li>
 * </ul>
 */
public final class ToolCategory {

    private ToolCategory() {}

    private static final Set<Material> COMBAT_WEAPONS = EnumSet.of(
        Material.BOW,
        Material.CROSSBOW,
        Material.TRIDENT,
        Material.MACE
    );

    // 1.21.11+ 의 바닐라 창 7종. paper-api 1.21.4 빌드 대상에선 상수가 없을 수 있어 런타임 lookup.
    private static final Set<Material> SPEAR_MATERIALS;
    static {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : new String[]{
                "wooden_spear", "stone_spear", "iron_spear",
                "golden_spear", "diamond_spear", "netherite_spear", "copper_spear"}) {
            Material m = Material.matchMaterial(name);
            if (m != null) set.add(m);
        }
        SPEAR_MATERIALS = set;
    }

    public static boolean isLifeTool(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return Tag.ITEMS_PICKAXES.isTagged(m)
            || Tag.ITEMS_HOES.isTagged(m)
            || Tag.ITEMS_SHOVELS.isTagged(m)
            || Tag.ITEMS_AXES.isTagged(m)
            || m == Material.FISHING_ROD;
    }

    public static boolean isCombatWeapon(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return Tag.ITEMS_SWORDS.isTagged(m)
            || COMBAT_WEAPONS.contains(m)
            || SPEAR_MATERIALS.contains(m);
    }

    /**
     * 근접 무기 — 공격력/흡혈/치명타 등 ATTACK 트리거 인챈트가 실제로 발동하는 범위.
     * 검 / 창 / 삼지창 / 철퇴. 활/쇠뇌/도끼는 제외 (도끼는 생활 카테고리).
     */
    public static boolean isMeleeWeapon(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return Tag.ITEMS_SWORDS.isTagged(m)
            || SPEAR_MATERIALS.contains(m)
            || m == Material.TRIDENT
            || m == Material.MACE;
    }

    public static boolean isSword(ItemStack item) {
        return item != null && Tag.ITEMS_SWORDS.isTagged(item.getType());
    }

    public static boolean isSpear(ItemStack item) {
        return item != null && SPEAR_MATERIALS.contains(item.getType());
    }

    public static boolean isTrident(ItemStack item) {
        return item != null && item.getType() == Material.TRIDENT;
    }

    public static boolean isMace(ItemStack item) {
        return item != null && item.getType() == Material.MACE;
    }

    public static boolean isBow(ItemStack item) {
        return item != null && item.getType() == Material.BOW;
    }

    private static final Set<Material> ARMOR = EnumSet.of(
        // 헬멧
        Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET, Material.IRON_HELMET,
        Material.GOLDEN_HELMET, Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        Material.TURTLE_HELMET,
        // 흉갑
        Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE, Material.IRON_CHESTPLATE,
        Material.GOLDEN_CHESTPLATE, Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        // 레깅스
        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS, Material.IRON_LEGGINGS,
        Material.GOLDEN_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        // 부츠
        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
        Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
    );

    public static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        return ARMOR.contains(item.getType());
    }

    /** 검/도끼 등 무기 or 방어구 → 전투 램프 적용 가능. */
    public static boolean isCombatApplicable(ItemStack item) {
        return isCombatWeapon(item) || isArmor(item);
    }

    /** 곡괭이/괭이/삽/낚싯대 → 생활 램프 적용 가능. */
    public static boolean isLifeApplicable(ItemStack item) {
        return isLifeTool(item);
    }
}
