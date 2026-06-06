package com.exit.customitems.lamp.enchant.impl.combat;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantScope;
import com.exit.customitems.lamp.enchant.EnchantTier;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * [SET] 가시 — 방어구 부위마다 1레벨. 부위 합산으로 반사 데미지 증가.
 * 효과: reflect = per_level × level. enchants.yml: thorns.per_level.
 */
public class ThornsEnchant implements CustomEnchant {

    private final NamespacedKey key;

    public ThornsEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "thorns_lamp");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "가시"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.SET; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.DAMAGED; }

    @Override public List<ValueSpec> getValueSpecs() { return List.of(); }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isArmor(item);
    }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(getDisplayName())
            .color(NamedTextColor.GOLD);
    }
}
