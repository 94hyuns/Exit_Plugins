package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantScope;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** 농작물 수확 시 [10~50]% 확률로 뼛가루 [1~2]개 드랍. 모든 생활도구 적용. */
public class CropBoneMealEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_crop_bonemeal";

    private final NamespacedKey key;

    public CropBoneMealEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "농작물 뼛가루"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public boolean isStackable()         { return true; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        return List.of(
            new ValueSpec(NumUtil.toStored(10.0), NumUtil.toStored(50.0), NumUtil.toStored(0.5)),
            new ValueSpec(NumUtil.toStored(1.0),  NumUtil.toStored(2.0),  NumUtil.toStored(1.0))
        );
    }

    @Override public boolean canApplyTo(ItemStack item) { return ToolCategory.isLifeTool(item); }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(
            "농작물 수확 시 " + NumUtil.formatPercent1(values[0])
                + " 확률로 뼛가루 +" + NumUtil.formatInt(values[1])
        ).color(NamedTextColor.AQUA);
    }
}
