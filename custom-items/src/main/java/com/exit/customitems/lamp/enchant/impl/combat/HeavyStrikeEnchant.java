package com.exit.customitems.lamp.enchant.impl.combat;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantScope;
import com.exit.customitems.lamp.enchant.EnchantTier;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * [SET] 묵직한 일격 — 모든 무기 1~2레벨. flat 데미지 +(base + per_level × level).
 * 데미지 계산은 WeaponAttackListener / 화살은 BowShootListener 의 flat 누적 구간에서.
 * enchants.yml: heavy_strike.base / per_level.
 */
public class HeavyStrikeEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public HeavyStrikeEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(new ValueSpec(NumUtil.toStored(1), NumUtil.toStored(2), NumUtil.toStored(1)));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "heavy_strike");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "묵직한 일격"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.SET; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.ATTACK; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isCombatWeapon(item);
    }

    @Override
    public Component renderLore(int[] values) {
        int level = values.length > 0 ? values[0] / NumUtil.SCALE : 1;
        return Component.text(getDisplayName() + " " + level)
            .color(NamedTextColor.GOLD);
    }
}
