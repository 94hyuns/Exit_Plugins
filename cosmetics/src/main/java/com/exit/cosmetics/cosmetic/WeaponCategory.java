package com.exit.cosmetics.cosmetic;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * 무기 종류 카테고리 — WeaponHandler 의 다중 장착 분류 키.
 * 같은 카테고리 내에서는 한 cosmetic 만, 다른 카테고리는 동시 장착 가능.
 *
 * <p>DB slot 키 포맷: {@code WEAPON_<CATEGORY>} (예: "WEAPON_SWORD", "WEAPON_BOW")
 */
public enum WeaponCategory {
    SWORD,
    BOW,    // BOW + CROSSBOW 통합
    AXE,
    SPEAR,
    MACE,
    TRIDENT,
    OTHER;

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

    public static WeaponCategory fromMaterial(Material m) {
        if (m == null) return OTHER;
        if (Tag.ITEMS_SWORDS.isTagged(m)) return SWORD;
        if (m == Material.BOW || m == Material.CROSSBOW) return BOW;
        if (Tag.ITEMS_AXES.isTagged(m)) return AXE;
        if (SPEAR_MATERIALS.contains(m)) return SPEAR;
        if (m == Material.MACE) return MACE;
        if (m == Material.TRIDENT) return TRIDENT;
        return OTHER;
    }

    /** DB slot 키 → category. 매칭 실패 시 null. */
    public static WeaponCategory fromSlotKey(String slot) {
        if (slot == null || !slot.startsWith("WEAPON_")) return null;
        try {
            return WeaponCategory.valueOf(slot.substring("WEAPON_".length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String slotKey() {
        return "WEAPON_" + name();
    }
}
