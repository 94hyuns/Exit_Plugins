package com.exit.customitems.lamp.enchant.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.ArmorBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BigVitalityEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.SaturationKeeperEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.SwiftnessEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.VitalityEnchant;
import com.exit.customitems.util.NumUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 방어구 PASSIVE 인챈트 (방어도/최대체력) 의 AttributeModifier 관리.
 *
 * <p>적용 정책:
 * <ul>
 *   <li>견고함 → ARMOR 속성에 합산 modifier 1개 (key: {@code customitems:armor_boost})</li>
 *   <li>활력 + 큰 최대체력 → MAX_HEALTH 속성에 합산 modifier 1개 (key: {@code customitems:max_health_lamp})</li>
 * </ul>
 *
 * <p>갱신 시점: 입장 / 방어구 변경 / 리스폰. 퇴장 시 정리.
 */
public class ArmorAttributeManager implements Listener {

    private final Plugin plugin;
    private final EnchantStorage storage;
    private final EnchantConfig ec;

    private final NamespacedKey kArmorBoost;
    private final NamespacedKey kVitality;
    private final NamespacedKey kBigVitality;
    private final NamespacedKey kSwiftness;
    private final NamespacedKey kSaturationKeeper;

    /** 합산 modifier 식별용 키 (속성당 1개). */
    private final NamespacedKey modKeyArmor;
    private final NamespacedKey modKeyMaxHealth;
    private final NamespacedKey modKeySpeed;
    private final NamespacedKey modKeySprintComp;  // 포만유지 L3+ 스프린트 보상

    public ArmorAttributeManager(Plugin plugin, EnchantStorage storage, EnchantConfig ec) {
        this.plugin = plugin;
        this.storage = storage;
        this.ec = ec;

        this.kArmorBoost      = ArmorBoostEnchant.keyOf(plugin);
        this.kVitality        = VitalityEnchant.keyOf(plugin);
        this.kBigVitality     = BigVitalityEnchant.keyOf(plugin);
        this.kSwiftness       = SwiftnessEnchant.keyOf(plugin);
        this.kSaturationKeeper = SaturationKeeperEnchant.keyOf(plugin);

        this.modKeyArmor      = new NamespacedKey(plugin, "armor_boost");
        this.modKeyMaxHealth  = new NamespacedKey(plugin, "max_health_lamp");
        this.modKeySpeed      = new NamespacedKey(plugin, "swiftness");
        this.modKeySprintComp = new NamespacedKey(plugin, "sat_sprint_comp");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> recompute(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        recompute(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> recompute(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // clearAll 호출 제거 (2026-05-16) — Bukkit AttributeModifier 는 player NBT 와 함께
        // 자동 저장됨. 떠날 때 정리하면 maxHealth 가 vanilla 20 으로 환원되면서 currentHP 도
        // 20 으로 clamp 되어 저장됨 → 재접속 시 인챈트 분량만큼 빈 체력으로 시작하는 버그 발생.
        // modifier 는 그대로 두면 NBT 에 그대로 저장되고 재접속 시 자동 복원.
    }

    public void recompute(Player player) {
        double armorSum = 0.0;
        double maxHealthSum = 0.0;
        double speedPctSum = 0.0;

        // 견고함 / 활력 / 신속 (BASIC) — 부위별 값 합산. 행운 박힌 부위는 ×2.
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            double mult = SetLevelCounter.pieceHasLucky(armor, storage) ? 2.0 : 1.0;
            for (RolledEnchant r : storage.load(armor)) {
                NamespacedKey k = r.enchant().getKey();
                if (k.equals(kArmorBoost) && r.values().length > 0) {
                    armorSum += NumUtil.fromStored(r.values()[0]) * mult;
                } else if (k.equals(kVitality) && r.values().length > 0) {
                    maxHealthSum += NumUtil.fromStored(r.values()[0]) * mult;
                } else if (k.equals(kSwiftness) && r.values().length > 0) {
                    speedPctSum += NumUtil.fromStored(r.values()[0]) * mult;
                }
            }
        }

        // 체력은 국력 (구 큰 최대체력, SET) — 부위 카운트 = level. 효과 = base + per × level.
        // L1=4, L2=6, L3=8, L4=10, **L5=15칸** (행운 시너지로 5렙 도달 시 보너스 점프).
        int bigVitLevel = SetLevelCounter.countOnArmor(player, kBigVitality, storage);
        if (bigVitLevel > 0) {
            double bonus;
            if (bigVitLevel >= 5) {
                bonus = 15.0;
            } else {
                double base = ec.readDouble("big_vitality", "base", 2.0);
                double perLevel = ec.readDouble("big_vitality", "per_level", 2.0);
                bonus = base + perLevel * bigVitLevel;
            }
            maxHealthSum += bonus;
        }

        // maxHealthSum 은 "칸" 단위로 누적됨. 마크 내부 MAX_HEALTH 는 0.5칸 = 1.0 이므로 ×2.
        applyOrRemove(player, Attribute.ARMOR, modKeyArmor, armorSum, AttributeModifier.Operation.ADD_NUMBER);
        applyOrRemove(player, Attribute.MAX_HEALTH, modKeyMaxHealth, maxHealthSum * 2.0, AttributeModifier.Operation.ADD_NUMBER);
        // 신속: speedPctSum 은 %단위 누적 (예: 4부위 × 5% = 20). MULTIPLY_SCALAR_1 에는 비율(0.20)로 넘김.
        applyOrRemove(player, Attribute.MOVEMENT_SPEED, modKeySpeed, speedPctSum / 100.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);

        // 포만 유지 L3+ 에서는 허기 캡이 vanilla sprint 조건(food≥6) 미만 → sprint 불가.
        // 보상으로 MOVEMENT_SPEED 부여 — L3/L4=+30%, L5=+70% (스프린트 초월).
        int satLevel = SetLevelCounter.countOnArmor(player, kSaturationKeeper, storage);
        double sprintComp;
        if (satLevel >= 5) sprintComp = 0.70;
        else if (satLevel >= 3) sprintComp = 0.30;
        else sprintComp = 0.0;
        applyOrRemove(player, Attribute.MOVEMENT_SPEED, modKeySprintComp, sprintComp, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    public void clearAll(Player player) {
        applyOrRemove(player, Attribute.ARMOR, modKeyArmor, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        applyOrRemove(player, Attribute.MAX_HEALTH, modKeyMaxHealth, 0.0, AttributeModifier.Operation.ADD_NUMBER);
        applyOrRemove(player, Attribute.MOVEMENT_SPEED, modKeySpeed, 0.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        applyOrRemove(player, Attribute.MOVEMENT_SPEED, modKeySprintComp, 0.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
    }

    private void applyOrRemove(Player player, Attribute attr, NamespacedKey modKey, double value,
                               AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;

        // 기존 modifier 제거
        inst.getModifiers().stream()
                .filter(m -> modKey.equals(m.getKey()))
                .toList()  // 순회 중 변경 방지
                .forEach(inst::removeModifier);

        if (value > 0) {
            inst.addModifier(new AttributeModifier(
                    modKey, value, op, EquipmentSlotGroup.ANY));
        }
    }
}
