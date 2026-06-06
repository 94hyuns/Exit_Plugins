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
 * [SET] 행운 — 방어구 전용. 자체 효과는 없고 시너지 제공.
 *
 * <ul>
 *   <li>부착된 부위의 BASIC 인챈트(견고함/활력/신속) 효과를 2배로 받게 함.</li>
 *   <li>다른 SET 인챈트의 "부위 카운트"에 자동 포함됨. 예: 갑옷에 행운, 투구에 포만유지 → 포만유지 L2 효과.</li>
 *   <li>황금 심장은 BASIC 이지만 레벨 기반이라 2배 적용 제외.</li>
 * </ul>
 *
 * <p>일반 람프에서 뽑힐 확률은 다른 세트 대비 낮고 (가중치 8 vs 23),
 * 변성램프에서는 가장 높음 (24 vs 19). enchants.yml: armor_set_weights.lucky.
 */
public class LuckyEnchant implements CustomEnchant {

    private final NamespacedKey key;

    public LuckyEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "lucky");
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "행운"; }
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
