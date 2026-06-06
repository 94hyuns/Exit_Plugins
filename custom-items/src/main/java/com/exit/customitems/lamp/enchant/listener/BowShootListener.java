package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ApexBreakerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BowAttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BowCriticalEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.HeavyStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LethalStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.QuickShotEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.TripleShotEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.TwinShotEnchant;
import com.exit.customitems.util.NumUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 활 발사 시 활에 부여된 인챈트 처리.
 *
 * <ul>
 *   <li><b>신속 사격</b> — force ≥ threshold 면 화살을 풀드로우 위력으로 보정</li>
 *   <li><b>활 공격력 / 치명타</b> — 화살 PDC 에 태그, 명중 시 WeaponAttackListener 가 적용</li>
 *   <li><b>이연사 (L1=50% / L2=75%)</b> — 확률 발동 시 같은 궤도로 1발 추가 (총 2발)</li>
 *   <li><b>삼연사 (L1=35% / L2=55%)</b> — 확률 발동 시 같은 궤도로 2발 추가 (총 3발)</li>
 *   <li>이연사 + 삼연사 동시 발동 가능 (둘 다 proc 시 추가 화살 3발 = 총 4발)</li>
 * </ul>
 *
 * <p>추가 화살은 인벤토리에서 차감 (무한 활/크리에이티브 시 면제).
 * 화살 부족 시 가능한 만큼만 부분 발사.
 */
public class BowShootListener implements Listener {

    private final EnchantDispatcher dispatcher;
    private final EnchantConfig ec;
    private final NamespacedKey kTwinShot;
    private final NamespacedKey kTripleShot;
    private final NamespacedKey kQuickShot;
    private final NamespacedKey kBowAttack;
    private final NamespacedKey kBowCrit;
    private final NamespacedKey kHeavy;
    private final NamespacedKey kApex;
    private final NamespacedKey kLethal;
    private final NamespacedKey arrowFlatDamage;
    private final NamespacedKey arrowCritChance;
    private final NamespacedKey arrowHeavyStrike;
    private final NamespacedKey arrowApexBreaker;
    private final NamespacedKey arrowLethalStrike;

    public BowShootListener(Plugin plugin, EnchantDispatcher dispatcher, EnchantConfig ec) {
        this.dispatcher = dispatcher;
        this.ec = ec;
        this.kTwinShot = TwinShotEnchant.keyOf(plugin);
        this.kTripleShot = TripleShotEnchant.keyOf(plugin);
        this.kQuickShot = QuickShotEnchant.keyOf(plugin);
        this.kBowAttack = BowAttackPowerEnchant.keyOf(plugin);
        this.kBowCrit = BowCriticalEnchant.keyOf(plugin);
        this.kHeavy = HeavyStrikeEnchant.keyOf(plugin);
        this.kApex = ApexBreakerEnchant.keyOf(plugin);
        this.kLethal = LethalStrikeEnchant.keyOf(plugin);
        this.arrowFlatDamage = new NamespacedKey(plugin, "arrow_bow_attack");
        this.arrowCritChance = new NamespacedKey(plugin, "arrow_bow_crit");
        this.arrowHeavyStrike = new NamespacedKey(plugin, "arrow_heavy_strike");
        this.arrowApexBreaker = new NamespacedKey(plugin, "arrow_apex_breaker");
        this.arrowLethalStrike = new NamespacedKey(plugin, "arrow_lethal_strike");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;

        ItemStack bow = event.getBow();
        if (!ToolCategory.isBow(bow)) return;

        if (!(event.getProjectile() instanceof Arrow original)) return;

        // === 신속 사격 ===
        Optional<RolledEnchant> quick = dispatcher.findInItem(bow, kQuickShot);
        if (quick.isPresent()) {
            int level = levelOf(quick.get());
            List<Double> thresholds = ec.readDoubleList("quick_shot", "thresholds",
                    List.of(0.20, 0.10));
            double threshold = pickLevel(thresholds, level, 0.20);
            float force = event.getForce();
            if (force >= threshold && force < 1.0f) {
                double scale = 1.0 / force;
                original.setVelocity(original.getVelocity().multiply(scale));
                original.setDamage(original.getDamage() * scale);
            }
        }

        // === 활 공격력 / 치명타 — 화살 PDC 태그 ===
        Optional<RolledEnchant> atk = dispatcher.findInItem(bow, kBowAttack);
        Optional<RolledEnchant> crit = dispatcher.findInItem(bow, kBowCrit);
        if (atk.isPresent() && atk.get().values().length > 0) {
            original.getPersistentDataContainer().set(arrowFlatDamage,
                    PersistentDataType.INTEGER, atk.get().values()[0]);
        }
        if (crit.isPresent() && crit.get().values().length > 0) {
            original.getPersistentDataContainer().set(arrowCritChance,
                    PersistentDataType.INTEGER, crit.get().values()[0]);
        }

        // 공통 무기 UNIQUE (변성 풀 확장) — 활에도 적용. level INT 만 태그, 효과는 hit 시점에.
        Optional<RolledEnchant> heavy = dispatcher.findInItem(bow, kHeavy);
        if (heavy.isPresent()) {
            original.getPersistentDataContainer().set(arrowHeavyStrike,
                    PersistentDataType.INTEGER, levelOf(heavy.get()));
        }
        Optional<RolledEnchant> apex = dispatcher.findInItem(bow, kApex);
        if (apex.isPresent()) {
            original.getPersistentDataContainer().set(arrowApexBreaker,
                    PersistentDataType.INTEGER, levelOf(apex.get()));
        }
        Optional<RolledEnchant> lethal = dispatcher.findInItem(bow, kLethal);
        if (lethal.isPresent()) {
            original.getPersistentDataContainer().set(arrowLethalStrike,
                    PersistentDataType.INTEGER, levelOf(lethal.get()));
        }

        // === 이연사 / 삼연사 — 독립 확률 발동, 추가 화살 발사 ===
        int extrasNeeded = 0;

        Optional<RolledEnchant> twin = dispatcher.findInItem(bow, kTwinShot);
        if (twin.isPresent()) {
            int level = levelOf(twin.get());
            List<Double> chances = ec.readDoubleList("twin_shot", "chance_per_level",
                    List.of(50.0, 75.0));
            double chance = pickLevel(chances, level, 50.0);
            if (rollProc(chance)) extrasNeeded += 1;
        }

        Optional<RolledEnchant> triple = dispatcher.findInItem(bow, kTripleShot);
        if (triple.isPresent()) {
            int level = levelOf(triple.get());
            List<Double> chances = ec.readDoubleList("triple_shot", "chance_per_level",
                    List.of(35.0, 55.0));
            double chance = pickLevel(chances, level, 35.0);
            if (rollProc(chance)) extrasNeeded += 2;
        }

        if (extrasNeeded <= 0) return;

        // 인벤토리 화살 가용량 결정. 무한/크리에이티브 = 무제한.
        boolean unlimited = bow.containsEnchantment(Enchantment.INFINITY)
                         || p.getGameMode() == GameMode.CREATIVE;
        int availableExtras;
        if (unlimited) {
            availableExtras = extrasNeeded;
        } else {
            // vanilla 가 1발 차감하므로 추가 발사는 (총 화살수 - 1) 만큼만 가능.
            int totalArrows = countArrows(p);
            availableExtras = Math.min(extrasNeeded, Math.max(0, totalArrows - 1));
        }

        for (int i = 0; i < availableExtras; i++) {
            spawnArrowCopy(p, original);
        }
        if (!unlimited && availableExtras > 0) {
            p.getInventory().removeItem(new ItemStack(Material.ARROW, availableExtras));
        }
    }

    private void spawnArrowCopy(Player p, Arrow original) {
        // EntityShootBowEvent 시점엔 original.getLocation() 이 플레이어 발 위치일 수 있어
        // 추가 화살이 발 아래에서 떨어지는 버그가 있었음. 플레이어 eye location 기준으로 직접 발사.
        Vector velocity = original.getVelocity().clone();
        Arrow a = p.launchProjectile(Arrow.class, velocity);
        a.setShooter(p);
        a.setDamage(original.getDamage());
        a.setCritical(original.isCritical());
        a.setPierceLevel(original.getPierceLevel());
        a.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        // 원본 활 인챈트 태그 복사
        var origPdc = original.getPersistentDataContainer();
        Integer flat = origPdc.get(arrowFlatDamage, PersistentDataType.INTEGER);
        Integer cc = origPdc.get(arrowCritChance, PersistentDataType.INTEGER);
        Integer hv = origPdc.get(arrowHeavyStrike, PersistentDataType.INTEGER);
        Integer ax = origPdc.get(arrowApexBreaker, PersistentDataType.INTEGER);
        Integer lt = origPdc.get(arrowLethalStrike, PersistentDataType.INTEGER);
        if (flat != null) a.getPersistentDataContainer().set(arrowFlatDamage, PersistentDataType.INTEGER, flat);
        if (cc != null) a.getPersistentDataContainer().set(arrowCritChance, PersistentDataType.INTEGER, cc);
        if (hv != null) a.getPersistentDataContainer().set(arrowHeavyStrike, PersistentDataType.INTEGER, hv);
        if (ax != null) a.getPersistentDataContainer().set(arrowApexBreaker, PersistentDataType.INTEGER, ax);
        if (lt != null) a.getPersistentDataContainer().set(arrowLethalStrike, PersistentDataType.INTEGER, lt);
    }

    private int countArrows(Player p) {
        int count = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && s.getType() == Material.ARROW) count += s.getAmount();
        }
        // 오프핸드도 vanilla 발사에 쓰일 수 있어 포함
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.getType() == Material.ARROW) count += off.getAmount();
        return count;
    }

    private static boolean rollProc(double percentChance) {
        return ThreadLocalRandom.current().nextDouble() * 100.0 < percentChance;
    }

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
