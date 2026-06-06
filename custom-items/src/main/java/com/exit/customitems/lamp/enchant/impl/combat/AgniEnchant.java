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
 * [SET] 아그니 — 방어구 전용. 착용자가 적을 발화시켰을 때 그 불 데미지를 증폭.
 *
 * <p>발동 조건: 칼의 발화(Fire Aspect) 또는 활의 화염(Flame) 등으로 적이 불 붙은 상태.
 * 추가 데미지: 부위 카운트 × per_level (%) — 4부위 = +(per×4)%.
 * 환경 발화(용암/태양/불 블록)는 적용 안 됨 — 플레이어가 직접 발화시킨 경우만.
 *
 * <p>enchants.yml: agni.per_level (기본 50.0 → L1=+50%, L2=+100%, L3=+150%, L4=+200%).
 */
public class AgniEnchant implements CustomEnchant {

    private final NamespacedKey key;

    public AgniEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "agni");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "아그니"; }
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
