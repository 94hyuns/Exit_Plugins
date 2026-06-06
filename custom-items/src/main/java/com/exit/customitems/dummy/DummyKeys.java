package com.exit.customitems.dummy;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** 허수아비 식별용 PDC 키. */
public final class DummyKeys {

    public final NamespacedKey marker;     // v1 (MM bv_dummy 기반, deprecated)
    public final NamespacedKey markerV2;   // v2 (vanilla COW 기반, 신규 /dummy)

    public DummyKeys(Plugin plugin) {
        this.marker = new NamespacedKey(plugin, "training_dummy");
        this.markerV2 = new NamespacedKey(plugin, "training_dummy_v2");
    }
}
