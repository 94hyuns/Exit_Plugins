package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.AgniEnchant;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * 아그니 (방어구 SET) — 착용자가 발화시킨 적의 FIRE_TICK 데미지 증폭.
 *
 * <p>동작:
 * <ol>
 *   <li>{@link EntityCombustByEntityEvent} 에서 발화자(플레이어 직접 또는 플레이어 발사 화살)를 metadata 로 기록.</li>
 *   <li>{@link EntityDamageEvent} FIRE_TICK / FIRE 발생 시 metadata 의 발화자 확인.</li>
 *   <li>그 플레이어가 현재 아그니 SET 을 N부위 착용 중이면 데미지 × (1 + per_level × N / 100).</li>
 * </ol>
 *
 * <p>환경 발화(용암/햇빛/불 블록)는 EntityCombustByEntityEvent 가 발생하지 않으므로 자동 제외.
 */
public class AgniFireListener implements Listener {

    private static final String META_KEY = "agni_combustion_owner";

    private final Plugin plugin;
    private final EnchantStorage storage;
    private final EnchantConfig ec;
    private final NamespacedKey kAgni;

    public AgniFireListener(Plugin plugin, EnchantStorage storage, EnchantConfig ec) {
        this.plugin = plugin;
        this.storage = storage;
        this.ec = ec;
        this.kAgni = AgniEnchant.keyOf(plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        Entity combuster = event.getCombuster();
        Player owner = null;
        if (combuster instanceof Player p) {
            owner = p;  // 검 발화 (Fire Aspect) 직접 타격
        } else if (combuster instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            owner = shooter;  // 활 화염(Flame) 화살
        }
        if (owner == null) return;

        // 발화 시점에 발화자 UUID 를 victim 메타데이터로 기록.
        event.getEntity().setMetadata(META_KEY,
                new FixedMetadataValue(plugin, owner.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireTickDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.FIRE) return;

        Entity victim = event.getEntity();
        if (!victim.hasMetadata(META_KEY)) return;

        Player owner = resolveOwner(victim);
        if (owner == null) return;

        int level = SetLevelCounter.countOnArmor(owner, kAgni, storage);
        if (level <= 0) return;

        // L1=+50%, L2=+100%, L3=+150%, L4=+200%, **L5=+300%** (보너스 점프)
        double pctBonus;
        if (level >= 5) {
            pctBonus = 300.0;
        } else {
            double perLevel = ec.readDouble("agni", "per_level", 50.0);
            pctBonus = perLevel * level;
        }
        double multiplier = 1.0 + pctBonus / 100.0;
        event.setDamage(event.getDamage() * multiplier);
    }

    private Player resolveOwner(Entity victim) {
        for (MetadataValue mv : victim.getMetadata(META_KEY)) {
            if (mv.getOwningPlugin() != plugin) continue;
            String uuidStr = mv.asString();
            if (uuidStr == null || uuidStr.isEmpty()) continue;
            try {
                return Bukkit.getPlayer(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }
}
