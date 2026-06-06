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
 * [SET] 치명타 — 모든 무기 1~2레벨. 일정 확률로 데미지 ×배율 (pctBonus 합연산).
 * 무기 스킬(EntityDamageByEntityEvent 통해 호출되는 그레이트소드/프로스트모운 등)에도 자동 적용.
 * enchants.yml: lethal_strike.chance_per_level / multiplier_per_level (List).
 */
public class LethalStrikeEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public LethalStrikeEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(new ValueSpec(NumUtil.toStored(1), NumUtil.toStored(2), NumUtil.toStored(1)));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "lethal_strike");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "치명타"; }
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
