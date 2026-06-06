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
 * [SET/유니크] 낙뢰 — 철퇴 전용 1~5레벨. 근접 공격 시 확률(base + per_level × level)% 로 발동.
 * 발동 시 타겟 위치에 번개 시각효과 + 평타 데미지의 damage_ratio 만큼 추가 데미지.
 * enchants.yml: thunder_strike.base / per_level / damage_ratio.
 */
public class ThunderStrikeEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public ThunderStrikeEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(new ValueSpec(NumUtil.toStored(1), NumUtil.toStored(2), NumUtil.toStored(1)));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "thunder_strike");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "낙뢰"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.SET; }
    @Override public EnchantScope getScope()       { return EnchantScope.TOOL_SPECIFIC; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.ATTACK; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isMace(item);
    }

    @Override
    public Component renderLore(int[] values) {
        int level = values.length > 0 ? values[0] / NumUtil.SCALE : 1;
        return Component.text(getDisplayName() + " " + level)
            .color(NamedTextColor.GOLD);
    }
}
