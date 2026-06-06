package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ApexBreakerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.AttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.CriticalHitEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ExecutionerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.FeastFuryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.GiantHunterEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.HeavyStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.HungerFuryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LethalStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LifestealEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.RushStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ThunderStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.WillOWispEnchant;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.util.CooldownTracker;
import com.exit.customitems.util.NumUtil;
import com.exit.customitems.util.RollUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 근접/원거리 공격 시 무기 인챈트 적용.
 *
 * <p>유니크 인챈트는 무기에 1~3 레벨로 박힘 (values[0] = level).
 *
 * <p>적용 순서:
 * <ol>
 *   <li>깡 데미지: 공격력 BASIC (값 그대로)</li>
 *   <li>% 보너스: 거인사냥꾼/만찬분노/공복분노/처형/질주의일격 — 누적 합산</li>
 *   <li>합산 데미지: base + flat → ×(1 + pct)</li>
 *   <li>낙뢰 (확률): base × damage_ratio 만큼 추가</li>
 *   <li>치명타: 확률 발동 시 ×2</li>
 *   <li>흡혈 (BASIC): 쿨타임 체크 후 회복</li>
 * </ol>
 *
 * <p>활/석궁 공격 (Arrow damager) 도 처리. 화살에 사전 태그된 깡공격력/치명타 적용.
 */
public class WeaponAttackListener implements Listener {

    private final Plugin plugin;
    private final EnchantDispatcher dispatcher;
    private final CooldownTracker cooldowns;
    private final EnchantConfig ec;
    private final EnchantStorage storage;

    private final NamespacedKey kAttack;
    private final NamespacedKey kLifesteal;
    private final NamespacedKey kCrit;
    private final NamespacedKey kGiant;
    private final NamespacedKey kFeast;
    private final NamespacedKey kHunger;
    private final NamespacedKey kExec;
    private final NamespacedKey kRush;
    private final NamespacedKey kThunder;
    private final NamespacedKey kWisp;
    private final NamespacedKey kHeavy;
    private final NamespacedKey kApex;
    private final NamespacedKey kLethal;

    /** 화살 PDC 태그 (BowShootListener 가 쏠 때 부여). */
    private final NamespacedKey arrowFlatDamage;
    private final NamespacedKey arrowCritChance;
    private final NamespacedKey arrowHeavyStrike;
    private final NamespacedKey arrowApexBreaker;
    private final NamespacedKey arrowLethalStrike;

    public WeaponAttackListener(Plugin plugin, EnchantDispatcher dispatcher,
                                CooldownTracker cooldowns, EnchantConfig ec,
                                EnchantStorage storage) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.cooldowns = cooldowns;
        this.ec = ec;
        this.storage = storage;

        this.kAttack    = AttackPowerEnchant.keyOf(plugin);
        this.kLifesteal = LifestealEnchant.keyOf(plugin);
        this.kCrit      = CriticalHitEnchant.keyOf(plugin);
        this.kGiant     = GiantHunterEnchant.keyOf(plugin);
        this.kFeast     = FeastFuryEnchant.keyOf(plugin);
        this.kHunger    = HungerFuryEnchant.keyOf(plugin);
        this.kExec      = ExecutionerEnchant.keyOf(plugin);
        this.kRush      = RushStrikeEnchant.keyOf(plugin);
        this.kThunder   = ThunderStrikeEnchant.keyOf(plugin);
        this.kWisp      = WillOWispEnchant.keyOf(plugin);
        this.kHeavy     = HeavyStrikeEnchant.keyOf(plugin);
        this.kApex      = ApexBreakerEnchant.keyOf(plugin);
        this.kLethal    = LethalStrikeEnchant.keyOf(plugin);

        this.arrowFlatDamage    = new NamespacedKey(plugin, "arrow_bow_attack");
        this.arrowCritChance    = new NamespacedKey(plugin, "arrow_bow_crit");
        this.arrowHeavyStrike   = new NamespacedKey(plugin, "arrow_heavy_strike");
        this.arrowApexBreaker   = new NamespacedKey(plugin, "arrow_apex_breaker");
        this.arrowLethalStrike  = new NamespacedKey(plugin, "arrow_lethal_strike");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // === 화살 분기: 발사자 Player + Arrow + 태그가 있으면 깡공격력/치명타 적용 ===
        if (event.getDamager() instanceof Arrow arrow
                && arrow.getShooter() instanceof Player) {
            applyArrowBonuses(event, arrow);
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack tool = attacker.getInventory().getItemInMainHand();
        if (!ToolCategory.isMeleeWeapon(tool)) return;

        // ── 1. 깡 데미지 (공격력 BASIC + 도깨비불 SET) ──────
        Optional<RolledEnchant> attackPower = dispatcher.findInItem(tool, kAttack);
        double bonusFlat = attackPower
                .map(r -> NumUtil.fromStored(r.values()[0]))
                .orElse(0.0);

        // 도깨비불 (방어구 SET) — 부위 카운트 × per_level 깡뎀 추가 + 시각 이펙트
        // L1=+1.5, L2=+3, L3=+4.5, L4=+6, **L5=+9** (보너스 점프)
        int wispLevel = SetLevelCounter.countOnArmor(attacker, kWisp, storage);
        if (wispLevel > 0) {
            bonusFlat += wispFlatBonus(wispLevel);
            spawnWispBurst(victim, wispLevel);
        }

        // 묵직한 일격 — flat +(base + per × level). 항상 적용.
        Optional<RolledEnchant> heavy = dispatcher.findInItem(tool, kHeavy);
        if (heavy.isPresent()) {
            int level = levelOf(heavy.get());
            double hBase = ec.readDouble("heavy_strike", "base", 5.0);
            double hPer = ec.readDouble("heavy_strike", "per_level", 5.0);
            bonusFlat += hBase + hPer * level;
        }

        // ── 2. % 보너스 누적 ──────────────────
        double pctBonus = 0.0;

        // 거인 사냥꾼 — (base + per×L) × (effHp / reference_hp) %
        Optional<RolledEnchant> giant = dispatcher.findInItem(tool, kGiant);
        if (giant.isPresent() && attacker.getAttribute(Attribute.MAX_HEALTH) != null) {
            int level = levelOf(giant.get());
            double base = ec.readDouble("giant_hunter", "base", 180.0);
            double per = ec.readDouble("giant_hunter", "per_level", 120.0);
            double refHp = ec.readDouble("giant_hunter", "reference_hp", 56.0);
            double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            pctBonus += (base + per * level) * (maxHp / refHp) / 100.0;
        }

        // 만찬의 분노 — 허기 7칸 이상에서 (bars-6)/4 비례. (base + per×L)% × 비례
        Optional<RolledEnchant> feast = dispatcher.findInItem(tool, kFeast);
        if (feast.isPresent()) {
            int food = attacker.getFoodLevel();
            if (food >= 14) {
                int bars = food / 2;
                int level = levelOf(feast.get());
                double base = ec.readDouble("feast_fury", "base", 180.0);
                double per = ec.readDouble("feast_fury", "per_level", 120.0);
                double factor = (bars - 6) / 4.0;  // 1.0 at bars=10
                pctBonus += (base + per * level) * factor / 100.0;
            }
        }

        // 공복의 분노 — 허기 5칸 이하에서 (6-bars)/5 비례. 레벨별 %테이블 룩업.
        Optional<RolledEnchant> hungerFury = dispatcher.findInItem(tool, kHunger);
        if (hungerFury.isPresent()) {
            int food = attacker.getFoodLevel();
            if (food <= 10) {
                int bars = food / 2;
                int level = levelOf(hungerFury.get());
                List<Double> table = ec.readDoubleList("hunger_fury", "level_bonus_pct",
                        List.of(390.0, 560.0));
                double pct = pickLevel(table, level, 390.0);
                double factor = (6 - bars) / 5.0;  // 1.0 at bars=1
                pctBonus += pct * factor / 100.0;
            }
        }

        // 처형(약자멸시) — 적 체력 50% 미만 시 (base + per×L)%. 정면승부와 거울 대칭 (50% 정확치는 정면승부만).
        // 정면승부(apex_breaker) — 적 체력 50% 이상 시 (base + per×L)%. 함께 부여 시 전 구간 추가데미지.
        if (victim.getAttribute(Attribute.MAX_HEALTH) != null) {
            double victimMax = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            double victimHp = victim.getHealth();
            if (victimMax > 0) {
                double hpRatio = victimHp / victimMax;
                if (hpRatio < 0.50) {
                    Optional<RolledEnchant> exec = dispatcher.findInItem(tool, kExec);
                    if (exec.isPresent()) {
                        int level = levelOf(exec.get());
                        double base = ec.readDouble("executioner", "base", 180.0);
                        double per = ec.readDouble("executioner", "per_level", 120.0);
                        pctBonus += (base + per * level) / 100.0;
                    }
                } else {
                    Optional<RolledEnchant> apex = dispatcher.findInItem(tool, kApex);
                    if (apex.isPresent()) {
                        int level = levelOf(apex.get());
                        double base = ec.readDouble("apex_breaker", "base", 180.0);
                        double per = ec.readDouble("apex_breaker", "per_level", 120.0);
                        pctBonus += (base + per * level) / 100.0;
                    }
                }
            }
        }

        // 질주의 일격 (창) — (base + per×L) × min(1, vXZ/reference_speed) %
        Optional<RolledEnchant> rush = dispatcher.findInItem(tool, kRush);
        if (rush.isPresent() && ToolCategory.isSpear(tool)) {
            int level = levelOf(rush.get());
            double base = ec.readDouble("rush_strike", "base", 180.0);
            double per = ec.readDouble("rush_strike", "per_level", 120.0);
            double refSpeed = ec.readDouble("rush_strike", "reference_speed", 0.392);
            double vx = attacker.getVelocity().getX();
            double vz = attacker.getVelocity().getZ();
            double speedXZ = Math.sqrt(vx * vx + vz * vz);
            double factor = Math.min(1.0, speedXZ / refSpeed);
            pctBonus += (base + per * level) * factor / 100.0;
        }

        // 낙뢰 (철퇴) — 확률 발동 시 pctBonus 합산 (additive). 변성으로 거인사냥꾼이 같이 박혀도
        // 곱연산이 아닌 합연산이라 다른 무기 변성 풀세팅과 균형 유지.
        Optional<RolledEnchant> thunder = dispatcher.findInItem(tool, kThunder);
        boolean thunderFired = false;
        if (thunder.isPresent() && ToolCategory.isMace(tool)) {
            int level = levelOf(thunder.get());
            double chBase = ec.readDouble("thunder_strike", "chance_base", 30.0);
            double chPer = ec.readDouble("thunder_strike", "chance_per", 10.0);
            double chance = chBase + chPer * level;
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < chance) {
                double ratio = ec.readDouble("thunder_strike", "damage_ratio", 8.5);
                pctBonus += ratio;
                thunderFired = true;
            }
        }

        // 치명타 — 확률 발동 × 배율 (합연산: ×N 배 = +(N-1)×100% pctBonus 추가)
        // 무기 우클릭 스킬(그레이트소드/프로스트모운)도 le.damage(player) 호출로 동일 이벤트 발생하여 자동 적용.
        Optional<RolledEnchant> lethal = dispatcher.findInItem(tool, kLethal);
        boolean lethalFired = false;
        if (lethal.isPresent()) {
            int level = levelOf(lethal.get());
            List<Double> chances = ec.readDoubleList("lethal_strike", "chance_per_level",
                    List.of(25.0, 40.0));
            List<Double> mults = ec.readDoubleList("lethal_strike", "multiplier_per_level",
                    List.of(5.0, 6.0));
            double chance = pickLevel(chances, level, 25.0);
            double mult = pickLevel(mults, level, 5.0);
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < chance) {
                pctBonus += (mult - 1.0);
                lethalFired = true;
            }
        }

        // ── 3. 데미지 합산 ───────────────────
        double base = event.getDamage();
        double withFlat = base + bonusFlat;
        double withPct = withFlat * (1.0 + pctBonus);

        if (thunderFired) {
            victim.getWorld().strikeLightningEffect(victim.getLocation());
        }
        if (lethalFired) {
            spawnLethalEffect(victim);
        }

        // ── 4. 근접 치명타 (BASIC) — 풀에서 제외됨 (2026-05-12). 기존 아이템 호환용 dead code. ──
        Optional<RolledEnchant> crit = dispatcher.findInItem(tool, kCrit);
        if (crit.isPresent()) {
            if (RollUtil.percentRoll(crit.get().values()[0])) {
                withPct *= 2.0;
            }
        }

        if (withPct != base) {
            event.setDamage(withPct);
        }

        // ── 5. 흡혈 (BASIC) ──────────────────
        Optional<RolledEnchant> ls = dispatcher.findInItem(tool, kLifesteal);
        if (ls.isPresent() && cooldowns.isReady(attacker.getUniqueId(), "lifesteal")
                && attacker.getAttribute(Attribute.MAX_HEALTH) != null) {
            double heal = NumUtil.fromStored(ls.get().values()[0]);
            int cdSec = ls.get().values()[1] / NumUtil.SCALE;
            double maxHp = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(maxHp, attacker.getHealth() + heal));
            cooldowns.start(attacker.getUniqueId(), "lifesteal", cdSec * 1000L);
        }
    }

    /** 활/석궁 화살이 미리 태그한 깡공격력/치명타/공통 UNIQUE 적용. */
    private void applyArrowBonuses(EntityDamageByEntityEvent event, Arrow arrow) {
        var pdc = arrow.getPersistentDataContainer();
        Integer flatStored = pdc.get(arrowFlatDamage, PersistentDataType.INTEGER);
        Integer critStored = pdc.get(arrowCritChance, PersistentDataType.INTEGER);
        Integer heavyLevel = pdc.get(arrowHeavyStrike, PersistentDataType.INTEGER);
        Integer apexLevel = pdc.get(arrowApexBreaker, PersistentDataType.INTEGER);
        Integer lethalLevel = pdc.get(arrowLethalStrike, PersistentDataType.INTEGER);

        // 도깨비불은 shooter 의 현재 방어구를 hit 시점에 조회 (PDC 태그 X).
        double wispFlat = 0.0;
        if (arrow.getShooter() instanceof Player shooter) {
            int wispLevel = SetLevelCounter.countOnArmor(shooter, kWisp, storage);
            if (wispLevel > 0) {
                wispFlat = wispFlatBonus(wispLevel);
                if (event.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
                    spawnWispBurst(victim, wispLevel);
                }
            }
        }

        // 묵직한 일격 — flat 누적
        double heavyFlat = 0.0;
        if (heavyLevel != null && heavyLevel > 0) {
            double hBase = ec.readDouble("heavy_strike", "base", 5.0);
            double hPer = ec.readDouble("heavy_strike", "per_level", 5.0);
            heavyFlat = hBase + hPer * heavyLevel;
        }

        // 정면승부 — victim HP ≥ 50% pctBonus 합산
        double pctBonus = 0.0;
        if (apexLevel != null && apexLevel > 0
                && event.getEntity() instanceof LivingEntity victim
                && victim.getAttribute(Attribute.MAX_HEALTH) != null) {
            double vMax = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            double vHp = victim.getHealth();
            if (vMax > 0 && (vHp / vMax) >= 0.50) {
                double base = ec.readDouble("apex_breaker", "base", 180.0);
                double per = ec.readDouble("apex_breaker", "per_level", 120.0);
                pctBonus += (base + per * apexLevel) / 100.0;
            }
        }

        // 치명타 — 확률 발동, pctBonus 합연산
        boolean lethalFired = false;
        if (lethalLevel != null && lethalLevel > 0) {
            List<Double> chances = ec.readDoubleList("lethal_strike", "chance_per_level",
                    List.of(25.0, 40.0));
            List<Double> mults = ec.readDoubleList("lethal_strike", "multiplier_per_level",
                    List.of(5.0, 6.0));
            double chance = pickLevel(chances, lethalLevel, 25.0);
            double mult = pickLevel(mults, lethalLevel, 5.0);
            if (ThreadLocalRandom.current().nextDouble() * 100.0 < chance) {
                pctBonus += (mult - 1.0);
                lethalFired = true;
            }
        }

        if (flatStored == null && critStored == null && wispFlat == 0.0
                && heavyFlat == 0.0 && pctBonus == 0.0) return;

        double base = event.getDamage();
        double withFlat = base
                + (flatStored != null ? NumUtil.fromStored(flatStored) : 0.0)
                + wispFlat
                + heavyFlat;
        double withPct = withFlat * (1.0 + pctBonus);
        double finalDmg = withPct;
        if (critStored != null && RollUtil.percentRoll(critStored)) {
            finalDmg *= 2.0;
        }
        if (lethalFired && event.getEntity() instanceof LivingEntity victim) {
            spawnLethalEffect(victim);
        }
        if (finalDmg != base) event.setDamage(finalDmg);
    }

    /**
     * 도깨비불 폭발 이펙트 — 타겟 위치에 파란 영혼불 + 영혼 파티클 + 소리.
     * 부위 수에 따라 파티클 양이 증가.
     */
    /** 도깨비불 부위 수에 따른 flat 데미지 보너스. L5 보너스 점프 (+9). */
    private double wispFlatBonus(int wispLevel) {
        if (wispLevel >= 5) return 9.0;
        double perLevel = ec.readDouble("will_o_wisp", "per_level", 1.5);
        return perLevel * wispLevel;
    }

    /** 치명타 발동 시각/청각 피드백. 노란 크리티컬 파티클 + 강타 사운드. */
    private void spawnLethalEffect(org.bukkit.entity.LivingEntity victim) {
        org.bukkit.Location loc = victim.getLocation().add(0, victim.getHeight() / 2.0, 0);
        org.bukkit.World world = victim.getWorld();
        world.spawnParticle(org.bukkit.Particle.CRIT, loc, 25, 0.4, 0.4, 0.4, 0.3);
        world.playSound(loc, org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
    }

    private void spawnWispBurst(org.bukkit.entity.LivingEntity victim, int wispLevel) {
        org.bukkit.Location loc = victim.getLocation().add(0, victim.getHeight() / 2.0, 0);
        org.bukkit.World world = victim.getWorld();
        int particleCount = 10 + wispLevel * 5;  // L1=15, L4=30
        world.spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, loc, particleCount,
                0.3, 0.4, 0.3, 0.08);
        world.spawnParticle(org.bukkit.Particle.SOUL, loc, wispLevel * 2,
                0.2, 0.3, 0.2, 0.05);
        world.playSound(loc, org.bukkit.Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 1.5f);
    }

    /** 유니크 인챈트의 롤된 레벨 (1~3). values[0] 는 NumUtil.SCALE 단위로 저장됨. */
    private int levelOf(RolledEnchant r) {
        if (r.values().length == 0) return 1;
        return Math.max(1, r.values()[0] / NumUtil.SCALE);
    }

    private double pickLevel(List<Double> table, int level, double fallback) {
        if (table == null || table.isEmpty()) return fallback;
        int idx = Math.max(0, Math.min(table.size() - 1, level - 1));
        return table.get(idx);
    }
}
