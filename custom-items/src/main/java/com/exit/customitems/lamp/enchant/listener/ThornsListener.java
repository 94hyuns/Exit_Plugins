package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.ThornsEnchant;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.Plugin;

/**
 * 가시 인챈트 [SET] — 부위 카운트 × per_level 만큼 공격자에게 반사 데미지.
 */
public class ThornsListener implements Listener {

    private final EnchantStorage storage;
    private final EnchantConfig ec;
    private final NamespacedKey kThorns;

    public ThornsListener(Plugin plugin, EnchantStorage storage, EnchantConfig ec) {
        this.storage = storage;
        this.ec = ec;
        this.kThorns = ThornsEnchant.keyOf(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getFinalDamage() <= 0) return;

        int level = SetLevelCounter.countOnArmor(victim, kThorns, storage);
        if (level <= 0) return;

        double perLevel = ec.readDouble("thorns", "per_level", 1.0);
        double total = perLevel * level;
        if (total <= 0) return;

        LivingEntity target = resolveAttacker(event);
        if (target == null || target.equals(victim)) return;

        target.damage(total, victim);
    }

    private LivingEntity resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof LivingEntity le) return le;
        if (event.getDamager() instanceof Projectile p && p.getShooter() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }
}
