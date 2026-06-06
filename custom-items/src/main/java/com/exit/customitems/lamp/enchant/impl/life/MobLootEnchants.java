package com.exit.customitems.lamp.enchant.impl.life;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.plugin.Plugin;

/**
 * 동물/몬스터 처치 전리품 인챈트 6종.
 * 각각 AbstractMobLootEnchant 의 경량 서브클래스.
 */
public final class MobLootEnchants {

    private MobLootEnchants() {}

    public static class ChickenLoot extends AbstractMobLootEnchant {
        public ChickenLoot(Plugin plugin) { super(plugin, "life_loot_chicken", "닭"); }
        @Override public boolean matches(EntityType t) { return t == EntityType.CHICKEN; }
    }

    public static class RabbitLoot extends AbstractMobLootEnchant {
        public RabbitLoot(Plugin plugin) { super(plugin, "life_loot_rabbit", "토끼"); }
        @Override public boolean matches(EntityType t) { return t == EntityType.RABBIT; }
    }

    public static class PigLoot extends AbstractMobLootEnchant {
        public PigLoot(Plugin plugin) { super(plugin, "life_loot_pig", "돼지"); }
        @Override public boolean matches(EntityType t) { return t == EntityType.PIG; }
    }

    public static class SheepLoot extends AbstractMobLootEnchant {
        public SheepLoot(Plugin plugin) { super(plugin, "life_loot_sheep", "양"); }
        @Override public boolean matches(EntityType t) { return t == EntityType.SHEEP; }
    }

    public static class CowLoot extends AbstractMobLootEnchant {
        public CowLoot(Plugin plugin) { super(plugin, "life_loot_cow", "소"); }
        @Override
        public boolean matches(EntityType t) {
            // MOOSHROOM 도 소로 간주
            return t == EntityType.COW || t == EntityType.MOOSHROOM;
        }
    }

    public static class MonsterLoot extends AbstractMobLootEnchant {
        public MonsterLoot(Plugin plugin) { super(plugin, "life_loot_monster", "몬스터"); }
        @Override
        public boolean matches(EntityType t) {
            Class<?> cls = t.getEntityClass();
            return cls != null && Monster.class.isAssignableFrom(cls);
        }
    }
}
