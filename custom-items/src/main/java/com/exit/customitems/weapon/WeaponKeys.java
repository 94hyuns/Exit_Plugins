package com.exit.customitems.weapon;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class WeaponKeys {

    public final NamespacedKey type;
    public final NamespacedKey frostmourneDamageMod;
    public final NamespacedKey frostmourneSpeedMod;
    public final NamespacedKey greatswordDamageMod;
    public final NamespacedKey greatswordSpeedMod;
    /** Frostmourne lore 버전 — 스킬 확장 시 옛 인스턴스의 lore 자동 마이그레이션에 사용. */
    public final NamespacedKey frostmourneLoreVersion;

    public WeaponKeys(Plugin plugin) {
        this.type = new NamespacedKey(plugin, "weapon_type");
        this.frostmourneDamageMod = new NamespacedKey(plugin, "frostmourne_damage");
        this.frostmourneSpeedMod = new NamespacedKey(plugin, "frostmourne_speed");
        this.greatswordDamageMod = new NamespacedKey(plugin, "greatsword_damage");
        this.greatswordSpeedMod = new NamespacedKey(plugin, "greatsword_speed");
        this.frostmourneLoreVersion = new NamespacedKey(plugin, "frostmourne_lore_version");
    }
}
