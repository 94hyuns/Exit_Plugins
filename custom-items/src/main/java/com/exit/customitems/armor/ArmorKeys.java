package com.exit.customitems.armor;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class ArmorKeys {

    public final NamespacedKey type;
    public final NamespacedKey wingedArmorArmorMod;
    public final NamespacedKey wingedArmorToughnessMod;
    public final NamespacedKey wingedArmorKnockbackMod;

    // Anubis 세트 (보스2 드롭 — 투구/하의/신발)
    public final NamespacedKey anubisHelmetArmorMod;
    public final NamespacedKey anubisLeggingsArmorMod;
    public final NamespacedKey anubisBootsArmorMod;
    public final NamespacedKey anubisToughnessMod;
    public final NamespacedKey anubisKnockbackMod;
    /** 이 부위에 부여된 버프 종류 (SPEED / SATURATION / HASTE). 드롭/지급 시 랜덤. */
    public final NamespacedKey anubisBuff;

    public ArmorKeys(Plugin plugin) {
        this.type = new NamespacedKey(plugin, "armor_type");
        this.wingedArmorArmorMod = new NamespacedKey(plugin, "winged_armor_armor");
        this.wingedArmorToughnessMod = new NamespacedKey(plugin, "winged_armor_toughness");
        this.wingedArmorKnockbackMod = new NamespacedKey(plugin, "winged_armor_kb");

        this.anubisHelmetArmorMod = new NamespacedKey(plugin, "anubis_helmet_armor");
        this.anubisLeggingsArmorMod = new NamespacedKey(plugin, "anubis_leggings_armor");
        this.anubisBootsArmorMod = new NamespacedKey(plugin, "anubis_boots_armor");
        this.anubisToughnessMod = new NamespacedKey(plugin, "anubis_toughness");
        this.anubisKnockbackMod = new NamespacedKey(plugin, "anubis_kb");
        this.anubisBuff = new NamespacedKey(plugin, "anubis_buff");
    }
}
