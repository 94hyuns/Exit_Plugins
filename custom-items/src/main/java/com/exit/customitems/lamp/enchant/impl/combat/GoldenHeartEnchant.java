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
 * [BASIC] 황금 심장 — N초마다 황금하트(흡수) 1칸 생성. 부위 수에 따라 캡 증가.
 *
 * <ul>
 *   <li>Lv.1: 20초마다 1칸 생성</li>
 *   <li>Lv.2: 10초마다 1칸 생성</li>
 *   <li>Lv.3: 5초마다 1칸 생성</li>
 * </ul>
 *
 * <p>여러 부위에 붙어 있으면 가장 높은 레벨의 주기를 채택.
 * 캡(최대 황금하트 수) = 황금심장이 붙은 부위 수 + 1 (1부위=2칸, 4부위=5칸).
 *
 * <p>황금사과 등 외부 흡수 효과와 별개로 동작: 현재 absorption 이 본 인챈트의 캡 미만일 때만 +1칸 충전.
 * 황금사과 absorption 이 캡보다 크면 황금심장은 충전하지 않으므로 외부 효과를 침해하지 않는다.
 */
public class GoldenHeartEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final List<ValueSpec> specs;

    public GoldenHeartEnchant(Plugin plugin, EnchantConfig ec) {
        this.key = keyOf(plugin);
        // 레벨 1 ~ 3 (정수 롤)
        this.specs = List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(3.0),
            NumUtil.toStored(1.0)
        ));
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, "golden_heart");
    }

    /** 레벨(1/2/3) → 생성 주기(초). */
    public static int levelToInterval(int level) {
        return switch (level) {
            case 1  -> 20;
            case 2  -> 10;
            case 3  -> 5;
            default -> 20;
        };
    }

    /** 황금심장이 붙은 부위 수 → 캡(칸). 1부위=2칸, 부위당 +1칸. */
    public static int piecesToCap(int pieces) {
        return Math.max(0, pieces) + 1;
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "황금 심장"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.COMBAT; }
    @Override public EnchantTier getTier()         { return EnchantTier.BASIC; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.PASSIVE; }

    @Override public List<ValueSpec> getValueSpecs() { return specs; }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isArmor(item);
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        int interval = levelToInterval(level);
        return Component.text(
            "황금 심장 Lv." + level + " (" + interval + "초마다 흡수 체력 +1칸, 최대=착용 부위수+1)"
        ).color(NamedTextColor.AQUA);
    }
}
