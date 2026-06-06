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

/** [BASIC] 활 치명타 — N% 확률 ×2 데미지. 활 전용. enchants.yml: bow_critical.chance. */
public class BowCriticalEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public BowCriticalEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(ec.readSpec("bow_critical", "chance", 30.0, 50.0, 10.0));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "bow_critical");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "활 치명타"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.BASIC; }
    @Override public EnchantScope getScope()       { return EnchantScope.TOOL_SPECIFIC; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.BOW_SHOOT; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isBow(item);
    }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(getDisplayName() + " " + NumUtil.format1(values[0]) + "% 확률 (×2)")
            .color(NamedTextColor.AQUA);
    }
}
