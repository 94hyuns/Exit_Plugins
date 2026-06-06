package com.exit.customitems.lamp.bulk;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** BulkLamp 시스템 전용 PDC 키 모음. */
public final class BulkLampKeys {

    /** villager 가 램프 작업대 NPC 임을 표시 (값 1). */
    public final NamespacedKey npcMarker;

    /** GUI 내부 placeholder 아이템 (회색 유리판 등) 식별용. */
    public final NamespacedKey guiPlaceholder;

    /** GUI 의 "롤" 버튼 아이템 식별용. */
    public final NamespacedKey guiRollButton;

    public BulkLampKeys(Plugin plugin) {
        this.npcMarker      = new NamespacedKey(plugin, "bulk_lamp_npc");
        this.guiPlaceholder = new NamespacedKey(plugin, "bulk_lamp_placeholder");
        this.guiRollButton  = new NamespacedKey(plugin, "bulk_lamp_roll_button");
    }
}
