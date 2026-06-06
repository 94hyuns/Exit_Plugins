package com.exit.gamble.slot.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public class SlotActionKeys {

    public static final String ACTION_BET_0 = "BET_0";
    public static final String ACTION_BET_1 = "BET_1";
    public static final String ACTION_BET_2 = "BET_2";
    public static final String ACTION_SPIN = "SPIN";
    public static final String ACTION_CLOSE = "CLOSE";

    private final NamespacedKey actionKey;

    public SlotActionKeys(Plugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "slot_action");
    }

    public NamespacedKey actionKey() { return actionKey; }
}
