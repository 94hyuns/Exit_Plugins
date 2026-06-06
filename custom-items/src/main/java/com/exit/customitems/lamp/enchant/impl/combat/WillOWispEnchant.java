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
 * [SET] 도깨비불 — 방어구 전용.
 *
 * <ul>
 *   <li><b>시각</b>: 부위수만큼의 파란 영혼불 파티클이 플레이어 주위를 공전</li>
 *   <li><b>데미지</b>: 근접/활 공격 시 (부위수 × per_level) 만큼 깡뎀 추가</li>
 * </ul>
 *
 * <p>enchants.yml: will_o_wisp.per_level (기본 1.5 → L1=+1.5, L2=+3, L3=+4.5, L4=+6).
 * 행운 시너지 적용 (다른 SET 인챈트의 부위 카운트에 행운 부위 포함됨).
 */
public class WillOWispEnchant implements CustomEnchant {

    private final NamespacedKey key;

    public WillOWispEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "will_o_wisp");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "도깨비불"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.SET; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.PASSIVE; }

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
