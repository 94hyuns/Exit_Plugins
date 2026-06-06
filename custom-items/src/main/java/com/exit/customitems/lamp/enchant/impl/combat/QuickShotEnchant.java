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
 * [SET/유니크] 신속 사격 — 활 전용 1~3레벨. 부분 드로우도 풀 위력으로 발사.
 * 임계값 = enchants.yml: quick_shot.thresholds (level 별, force 비율). 기본 L1=0.30, L2=0.20, L3=0.10.
 * force >= threshold 시 화살 위력/속도/데미지를 풀드로우(force=1.0)로 보정.
 */
public class QuickShotEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public QuickShotEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        this.specs = List.of(new ValueSpec(NumUtil.toStored(1), NumUtil.toStored(2), NumUtil.toStored(1)));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "quick_shot");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "신속 사격"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.SET; }
    @Override public EnchantScope getScope()       { return EnchantScope.TOOL_SPECIFIC; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.BOW_SHOOT; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isBow(item);
    }

    @Override
    public Component renderLore(int[] values) {
        int level = values.length > 0 ? values[0] / NumUtil.SCALE : 1;
        return Component.text(getDisplayName() + " " + level)
            .color(NamedTextColor.GOLD);
    }
}
