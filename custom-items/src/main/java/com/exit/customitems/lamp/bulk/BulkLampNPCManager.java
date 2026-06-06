package com.exit.customitems.lamp.bulk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

/** 램프 작업대 NPC (vanilla Villager + PDC 마커) 의 spawn/remove/identify. */
public final class BulkLampNPCManager {

    private final BulkLampKeys keys;

    public BulkLampNPCManager(BulkLampKeys keys) {
        this.keys = keys;
    }

    /**
     * 주어진 위치에 NPC 빌리저 생성. AI/이동/상호작용 다 잠그고 PDC 마커를 박는다.
     */
    public Villager spawn(Location loc) {
        Villager v = loc.getWorld().spawn(loc, Villager.class, villager -> {
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setPersistent(true);
            villager.setCollidable(false);
            villager.setRemoveWhenFarAway(false);
            try {
                villager.setProfession(Villager.Profession.LIBRARIAN);
            } catch (Throwable ignored) { /* 프로페션 enum 변동 대비 — 폴백은 무직 */ }
            villager.customName(Component.text("램프 작업대", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            villager.setCustomNameVisible(true);
            villager.getPersistentDataContainer().set(keys.npcMarker, PersistentDataType.BYTE, (byte) 1);
        });
        return v;
    }

    /** 이 엔티티가 램프 작업대 NPC 인지. */
    public boolean isNpc(Entity entity) {
        if (!(entity instanceof Villager v)) return false;
        Byte mark = v.getPersistentDataContainer().get(keys.npcMarker, PersistentDataType.BYTE);
        return mark != null && mark == 1;
    }

    /** NPC 제거. NPC 가 아니면 no-op, false 반환. */
    public boolean remove(Entity entity) {
        if (!isNpc(entity)) return false;
        entity.remove();
        return true;
    }
}
