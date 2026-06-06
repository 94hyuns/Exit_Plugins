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

/** [BASIC] 흡혈. values[0]=회복 HP, values[1]=쿨다운 초. enchants.yml: lifesteal.heal / lifesteal.cooldown. */
public class LifestealEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public LifestealEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(
            ec.readSpec("lifesteal", "heal",     0.5, 2.0, 0.1),
            ec.readSpec("lifesteal", "cooldown", 1.0, 3.0, 1.0)
        );
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "lifesteal");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "흡혈"; }
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
        // heal 값은 raw HP(반칸 단위) 로 저장 — 표시는 칸(=HP/2) 단위로 통일.
        double hearts = NumUtil.fromStored(values[0]) / 2.0;
        return Component.text(getDisplayName() + " 체력 +" + String.format("%.2f", hearts)
                + "칸 / 쿨타임 " + NumUtil.formatInt(values[1]) + "초")
            .color(NamedTextColor.AQUA);
    }
}
