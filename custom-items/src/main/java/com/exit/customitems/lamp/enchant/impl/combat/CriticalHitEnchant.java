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

/** [BASIC] 치명타 — N% 확률로 ×2. enchants.yml: critical_hit.chance. */
public class CriticalHitEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public CriticalHitEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(ec.readSpec("critical_hit", "chance", 5.0, 25.0, 0.5));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "critical_hit");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "치명타"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.BASIC; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.ATTACK; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isMeleeWeapon(item);
    }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(getDisplayName() + " " + NumUtil.format1(values[0]) + "% 확률 (×2)")
            .color(NamedTextColor.AQUA);
    }
}
