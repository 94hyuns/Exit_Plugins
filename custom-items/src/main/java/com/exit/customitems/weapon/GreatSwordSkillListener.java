package com.exit.customitems.weapon;

import com.exit.customitems.util.CooldownTracker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GreatSword 우클릭 스킬 — 광역 슬램.
 *
 * <p>정면 3.5블록 반경 적에게 평타 데미지 × 7 + 넉백 + Slowness II 1.5초. 쿨다운 15초.
 * 인챈트(Sharpness/Smite/Bane) 자동 합산. lamp 인챈트는 EntityDamageByEntityEvent 통해 자동 적용.
 */
public final class GreatSwordSkillListener implements Listener {

    private static final String COOLDOWN_KEY = "greatsword_slam";
    private static final long COOLDOWN_MS = 15_000L;
    private static final double SLAM_RADIUS = 3.5;
    private static final double SLAM_REACH = 2.5;
    private static final double DAMAGE_MULTIPLIER = 10.0;
    private static final double BASE_DAMAGE = 14.0;

    private final GreatSwordItem greatSwordItem;
    private final CooldownTracker cooldowns;

    public GreatSwordSkillListener(GreatSwordItem greatSwordItem, CooldownTracker cooldowns) {
        this.greatSwordItem = greatSwordItem;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!greatSwordItem.isGreatSword(hand)) return;

        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);

        UUID uuid = player.getUniqueId();
        if (!cooldowns.isReady(uuid, COOLDOWN_KEY)) {
            long remaining = cooldowns.remainingMs(uuid, COOLDOWN_KEY);
            player.sendActionBar(Component.text(
                    "슬램 쿨다운 " + String.format("%.1f", remaining / 1000.0) + "초")
                    .color(NamedTextColor.GOLD));
            return;
        }

        doSlam(player, hand);
        cooldowns.start(uuid, COOLDOWN_KEY, COOLDOWN_MS);
    }

    private void doSlam(Player player, ItemStack weapon) {
        Vector look = player.getEyeLocation().getDirection();
        Vector horiz = new Vector(look.getX(), 0, look.getZ());
        if (horiz.lengthSquared() < 1e-6) horiz = new Vector(1, 0, 0);
        horiz = horiz.normalize();
        Location center = player.getLocation().add(horiz.clone().multiply(SLAM_REACH));
        center.setY(player.getLocation().getY() + 0.5);

        player.sendActionBar(Component.text("슬램!").color(NamedTextColor.GOLD));
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.2f, 0.55f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5f, 0.65f);
        center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 0.5f);

        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0, 0, 0, 0);
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0, 0, 0, 0);
        center.getWorld().spawnParticle(Particle.CLOUD, center, 35, SLAM_RADIUS * 0.5, 0.2, SLAM_RADIUS * 0.5, 0.05);
        center.getWorld().spawnParticle(Particle.CRIT, center, 30, 1.2, 0.5, 1.2, 0.4);

        double baseDamage = BASE_DAMAGE + sharpnessBonus(weapon);
        Set<UUID> processed = new HashSet<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, SLAM_RADIUS, SLAM_RADIUS, SLAM_RADIUS)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(player)) continue;
            if (!processed.add(e.getUniqueId())) continue;

            double dmg = (baseDamage + targetTypeBonus(weapon, le)) * DAMAGE_MULTIPLIER;
            le.damage(dmg, player);

            Vector kb = le.getLocation().toVector().subtract(player.getLocation().toVector());
            if (kb.lengthSquared() < 1e-6) kb = horiz.clone();
            else kb = kb.normalize();
            kb.setY(0.45);
            kb.multiply(0.8);
            le.setVelocity(le.getVelocity().add(kb));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, true, true));
        }
    }

    private static double sharpnessBonus(ItemStack weapon) {
        int sharp = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        return sharp > 0 ? 0.5 * sharp + 0.5 : 0.0;
    }

    private static double targetTypeBonus(ItemStack weapon, LivingEntity target) {
        double bonus = 0.0;
        int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
        if (smite > 0 && isUndead(target.getType())) bonus += 2.5 * smite;
        int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
        if (bane > 0 && isArthropod(target.getType())) bonus += 2.5 * bane;
        return bonus;
    }

    private static boolean isUndead(EntityType t) {
        return switch (t) {
            case ZOMBIE, ZOMBIE_VILLAGER, HUSK, DROWNED, ZOMBIFIED_PIGLIN, ZOGLIN,
                 SKELETON, STRAY, BOGGED, WITHER_SKELETON, ZOMBIE_HORSE, SKELETON_HORSE,
                 WITHER, PHANTOM -> true;
            default -> false;
        };
    }

    private static boolean isArthropod(EntityType t) {
        return switch (t) {
            case SPIDER, CAVE_SPIDER, SILVERFISH, ENDERMITE, BEE -> true;
            default -> false;
        };
    }
}
