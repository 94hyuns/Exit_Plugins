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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 프로스트모운 능동 스킬 — 4갈래 라우팅:
 * <ul>
 *   <li>우클릭 (no sneak) → 빙판 돌진 (8초)</li>
 *   <li>Shift+우클릭 → 빙결의 슬램 (15초, 광역)</li>
 *   <li>Shift+좌클릭 → 빙결의 숨결 [궁극기] (60초, 5초 빔)</li>
 *   <li>좌클릭 단독 → 바닐라 attack (스킬 처리 X)</li>
 * </ul>
 *
 * <p>{@link PlayerItemHeldEvent} 에서 옛 인스턴스 lore 자동 마이그레이션.
 */
public final class FrostmourneSkillListener implements Listener {

    // ─── 궁극기 (빙결의 숨결) ───
    private static final String CD_ULT = "frostmourne_ultimate";
    private static final long CD_ULT_MS = 60_000L;
    private static final int ULT_DURATION_TICKS = 100; // 5초 (고정)
    private static final double ULT_RANGE = 12.0;
    private static final double ULT_HIT_RADIUS = 0.7;
    private static final double ULT_DAMAGE_PER_HIT = 450.0; // 기존 45 × 10
    private static final long ULT_PER_ENTITY_CD_MS = 500;
    private static final int ULT_SLOWNESS_TICKS = 60;
    private static final int ULT_FREEZE_TICKS = 80;

    // ─── 슬램 ───
    private static final String CD_SLAM = "frostmourne_slam";
    private static final long CD_SLAM_MS = 15_000L;
    private static final double SLAM_RADIUS = 3.5;
    private static final double SLAM_REACH = 2.5;
    private static final double SLAM_DMG_MULT = 10.0;
    private static final double SLAM_BASE_DAMAGE = 20.0; // FrostmourneItem.ATTACK_DAMAGE_ADD 동일

    // ─── 돌진 ───
    private static final String CD_CHARGE = "frostmourne_charge";
    private static final long CD_CHARGE_MS = 8_000L;
    private static final double CHARGE_MAX_DISTANCE = 10.0;
    private static final int CHARGE_MAX_TICKS = 20; // 1초 안전망
    private static final double CHARGE_VELOCITY = 1.2;
    private static final double CHARGE_STALL_THRESHOLD = 0.1; // 1 tick 이동량 미만 → 충돌

    private final JavaPlugin plugin;
    private final FrostmourneItem frostmourneItem;
    private final CooldownTracker cooldowns;

    /** 활성 궁극기 빔 — 중복 시작 방지. */
    private final Map<UUID, BukkitRunnable> activeUlts = new HashMap<>();
    /** 활성 돌진 — 중복 시작 방지. */
    private final Map<UUID, BukkitRunnable> activeCharges = new HashMap<>();

    public FrostmourneSkillListener(JavaPlugin plugin, FrostmourneItem frostmourneItem,
                                    CooldownTracker cooldowns) {
        this.plugin = plugin;
        this.frostmourneItem = frostmourneItem;
        this.cooldowns = cooldowns;
    }

    // ─── 입력 라우팅 ───

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!frostmourneItem.isFrostmourne(hand)) return;

        // 들고 있는 동안 옛 인스턴스도 새 lore 로 갱신 (held event 가 못 잡는 케이스 보완)
        frostmourneItem.migrateLore(hand);

        Action action = event.getAction();
        boolean sneaking = player.isSneaking();
        boolean isRight = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean isLeft  = action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK;

        if (isRight) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (sneaking) tryStartSlam(player, hand);
            else          tryStartCharge(player);
        } else if (isLeft && sneaking) {
            event.setCancelled(true);
            tryStartUltimate(player);
        }
    }

    /** 옛 인스턴스의 잔여 Consumable 컴포넌트가 도달 → 소비 차단. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (frostmourneItem.isFrostmourne(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** 슬롯 전환 시 들고 있는 프로스트모운 lore 자동 갱신. */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null) frostmourneItem.migrateLore(newItem);
    }

    // ─── 궁극기 (빙결의 숨결) ───

    private void tryStartUltimate(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeUlts.containsKey(uuid)) return;
        if (!cooldowns.isReady(uuid, CD_ULT)) {
            long remaining = cooldowns.remainingMs(uuid, CD_ULT);
            player.sendActionBar(Component.text(
                    "빙결의 숨결 쿨다운 " + String.format("%.1f", remaining / 1000.0) + "초")
                    .color(NamedTextColor.LIGHT_PURPLE));
            return;
        }
        startUltBeam(player);
        cooldowns.start(uuid, CD_ULT, CD_ULT_MS);
    }

    private void startUltBeam(Player player) {
        UUID uuid = player.getUniqueId();
        Location origin0 = player.getEyeLocation();
        origin0.getWorld().playSound(origin0, "lichking.ice_spike", SoundCategory.PLAYERS, 1.6f, 0.9f);
        player.sendActionBar(Component.text("❄ 빙결의 숨결 ❄").color(NamedTextColor.LIGHT_PURPLE));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            final Map<UUID, Long> lastHit = new HashMap<>();

            @Override
            public void run() {
                if (ticks >= ULT_DURATION_TICKS || !player.isOnline()) { stop(); return; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!frostmourneItem.isFrostmourne(hand)) { stop(); return; }
                fireUltBeamTick(player, lastHit, ticks);
                ticks++;
            }

            private void stop() {
                cancel();
                activeUlts.remove(uuid);
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        activeUlts.put(uuid, task);
    }

    private void fireUltBeamTick(Player player, Map<UUID, Long> lastHit, int tick) {
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();
        long now = System.currentTimeMillis();

        if (tick % 10 == 0) {
            origin.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.4f, 1.6f);
        }

        double step = 0.5;
        for (double d = 0.6; d <= ULT_RANGE; d += step) {
            Location p = origin.clone().add(direction.clone().multiply(d));
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 1, 0.05, 0.05, 0.05, 0.0);
            if (tick % 2 == 0) {
                p.getWorld().spawnParticle(Particle.END_ROD, p, 1, 0.0, 0.0, 0.0, 0.0);
            }

            ItemStack weapon = player.getInventory().getItemInMainHand();
            for (Entity e : p.getWorld().getNearbyEntities(p, ULT_HIT_RADIUS, ULT_HIT_RADIUS, ULT_HIT_RADIUS)) {
                if (!(e instanceof LivingEntity le)) continue;
                if (e.equals(player)) continue;
                Long last = lastHit.get(le.getUniqueId());
                if (last != null && now - last < ULT_PER_ENTITY_CD_MS) continue;
                lastHit.put(le.getUniqueId(), now);
                double damage = ULT_DAMAGE_PER_HIT + vanillaEnchantBonus(weapon, le);
                le.damage(damage, player);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        ULT_SLOWNESS_TICKS, 1, false, true, true));
                try { le.setFreezeTicks(ULT_FREEZE_TICKS); } catch (Throwable ignored) {}
            }
        }
    }

    // ─── 슬램 (빙결의 슬램) ───

    private void tryStartSlam(Player player, ItemStack weapon) {
        UUID uuid = player.getUniqueId();
        if (!cooldowns.isReady(uuid, CD_SLAM)) {
            long remaining = cooldowns.remainingMs(uuid, CD_SLAM);
            player.sendActionBar(Component.text(
                    "빙결의 슬램 쿨다운 " + String.format("%.1f", remaining / 1000.0) + "초")
                    .color(NamedTextColor.AQUA));
            return;
        }
        doSlam(player, weapon);
        cooldowns.start(uuid, CD_SLAM, CD_SLAM_MS);
    }

    private void doSlam(Player player, ItemStack weapon) {
        Vector look = player.getEyeLocation().getDirection();
        Vector horiz = new Vector(look.getX(), 0, look.getZ());
        if (horiz.lengthSquared() < 1e-6) horiz = new Vector(1, 0, 0);
        horiz = horiz.normalize();
        Location center = player.getLocation().add(horiz.clone().multiply(SLAM_REACH));
        center.setY(player.getLocation().getY() + 0.5);

        player.sendActionBar(Component.text("❄ 빙결의 슬램 ❄").color(NamedTextColor.AQUA));
        // "obey me" 컨셉 — wither spawn + 얼음 깨짐 조합
        center.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.3f);
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.4f, 0.6f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2f, 0.8f);

        // 광역 이펙트
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center, 1, 0, 0, 0, 0);
        center.getWorld().spawnParticle(Particle.SNOWFLAKE, center, 60, SLAM_RADIUS * 0.6, 0.3, SLAM_RADIUS * 0.6, 0.05);
        // SOUL ring (반경 원형)
        int ringPoints = 24;
        for (int i = 0; i < ringPoints; i++) {
            double theta = (Math.PI * 2.0 * i) / ringPoints;
            double x = Math.cos(theta) * SLAM_RADIUS;
            double z = Math.sin(theta) * SLAM_RADIUS;
            Location p = center.clone().add(x, 0.1, z);
            p.getWorld().spawnParticle(Particle.SOUL, p, 1, 0, 0, 0, 0);
        }

        double baseDamage = SLAM_BASE_DAMAGE + sharpnessBonus(weapon);
        Set<UUID> processed = new HashSet<>();
        for (Entity e : center.getWorld().getNearbyEntities(center, SLAM_RADIUS, SLAM_RADIUS, SLAM_RADIUS)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(player)) continue;
            if (!processed.add(e.getUniqueId())) continue;

            double dmg = (baseDamage + targetTypeBonus(weapon, le)) * SLAM_DMG_MULT;
            le.damage(dmg, player);

            Vector kb = le.getLocation().toVector().subtract(player.getLocation().toVector());
            if (kb.lengthSquared() < 1e-6) kb = horiz.clone();
            else kb = kb.normalize();
            kb.setY(0.45);
            kb.multiply(0.8);
            le.setVelocity(le.getVelocity().add(kb));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, true, true));
            try { le.setFreezeTicks(60); } catch (Throwable ignored) {}
        }
    }

    // ─── 돌진 (빙판 돌진) ───

    private void tryStartCharge(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeCharges.containsKey(uuid)) return;
        if (!cooldowns.isReady(uuid, CD_CHARGE)) {
            long remaining = cooldowns.remainingMs(uuid, CD_CHARGE);
            player.sendActionBar(Component.text(
                    "돌진 쿨다운 " + String.format("%.1f", remaining / 1000.0) + "초")
                    .color(NamedTextColor.AQUA));
            return;
        }
        startCharge(player);
        cooldowns.start(uuid, CD_CHARGE, CD_CHARGE_MS);
    }

    private void startCharge(Player player) {
        UUID uuid = player.getUniqueId();
        Location start = player.getLocation().clone();
        Vector look = player.getEyeLocation().getDirection();
        Vector horiz = new Vector(look.getX(), 0, look.getZ());
        if (horiz.lengthSquared() < 1e-6) horiz = new Vector(1, 0, 0);
        final Vector direction = horiz.normalize();

        start.getWorld().playSound(start, Sound.ENTITY_HORSE_GALLOP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        start.getWorld().playSound(start, Sound.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.6f, 1.8f);
        player.sendActionBar(Component.text("❄ 빙판 돌진 ❄").color(NamedTextColor.AQUA));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            Location prev = start.clone();

            @Override
            public void run() {
                if (ticks >= CHARGE_MAX_TICKS || !player.isOnline()) { stop(); return; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (!frostmourneItem.isFrostmourne(hand)) { stop(); return; }

                Location now = player.getLocation();
                double distFromStart = horizontalDistance(start, now);
                if (distFromStart >= CHARGE_MAX_DISTANCE) { stop(); return; }

                // 충돌 감지: 직전 tick 대비 수평 이동량
                if (ticks > 0 && horizontalDistance(prev, now) < CHARGE_STALL_THRESHOLD) {
                    stop();
                    return;
                }
                prev = now.clone();

                // 추진력 부여
                Vector v = direction.clone().multiply(CHARGE_VELOCITY);
                v.setY(Math.max(player.getVelocity().getY(), -0.1)); // 떨어지지 않게 약간 보정
                player.setVelocity(v);

                // 얼음 자국 — 파티클만 (블록 설치 X, 물 잔존 X)
                layIceParticleTrail(now, direction);

                // 몸 주변 눈송이
                now.getWorld().spawnParticle(Particle.SNOWFLAKE, now.clone().add(0, 1.0, 0),
                        6, 0.3, 0.5, 0.3, 0.02);

                ticks++;
            }

            private void stop() {
                cancel();
                activeCharges.remove(uuid);
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        activeCharges.put(uuid, task);
    }

    /**
     * 발자국 얼음 자국 — 파티클로만 시각화. 블록은 절대 설치 안 함
     * (작물 월드에서 물 잔존 / 시야 방해 / 영구 자국 모두 회피).
     * 좌/우 ±1 폭으로 지면 위에 ITEM_SNOWBALL + SNOWFLAKE 살짝 뿌림.
     */
    private void layIceParticleTrail(Location loc, Vector forward) {
        Vector perp = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        for (int offset = -1; offset <= 1; offset++) {
            Location p = loc.clone().add(perp.clone().multiply(offset)).add(0, 0.05, 0);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p, 4, 0.25, 0.05, 0.25, 0.0);
            p.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, p, 3, 0.2, 0.02, 0.2, 0.05);
        }
    }

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ─── 공통 인챈트 보너스 ───

    private static double vanillaEnchantBonus(ItemStack weapon, LivingEntity target) {
        if (weapon == null) return 0.0;
        double bonus = sharpnessBonus(weapon);
        bonus += targetTypeBonus(weapon, target);
        return bonus;
    }

    private static double sharpnessBonus(ItemStack weapon) {
        if (weapon == null) return 0.0;
        int sharp = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        return sharp > 0 ? 0.5 * sharp + 0.5 : 0.0;
    }

    private static double targetTypeBonus(ItemStack weapon, LivingEntity target) {
        if (weapon == null) return 0.0;
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
