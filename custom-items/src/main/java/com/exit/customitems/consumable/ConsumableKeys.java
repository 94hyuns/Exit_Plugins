package com.exit.customitems.consumable;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Consumable 식별용 PDC 키.
 * 단일 키 {@code consumable_type}에 typeId 문자열(INV_SAVE / DDUZZONKU 등)을 저장.
 */
public final class ConsumableKeys {

    public final NamespacedKey type;

    public ConsumableKeys(Plugin plugin) {
        this.type = new NamespacedKey(plugin, "consumable_type");
    }
}
