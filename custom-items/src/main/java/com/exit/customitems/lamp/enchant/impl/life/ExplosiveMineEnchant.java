package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 시선 평면 3x3 × 깊이 3 = 27블록 큐브를 한 번에 부숨.
 * <ul>
 *   <li>Lv.1: 50% 확률</li>
 *   <li>Lv.2: 60% 확률</li>
 *   <li>Lv.3: 75% 확률</li>
 * </ul>
 * 곡괭이 전용. 야생 한정.
 */
public class ExplosiveMineEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_explosive_mine";

    private final NamespacedKey key;

    public ExplosiveMineEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    /** 레벨(1/2/3) → 발동 확률(%). */
    public static int levelToPercent(int level) {
        return switch (level) {
            case 1  -> 50;
            case 2  -> 60;
            case 3  -> 75;
            default -> 50;
        };
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "인간 굴착기"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // 레벨 1 ~ 3
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(3.0),
            NumUtil.toStored(1.0)
        ));
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return item != null && Tag.ITEMS_PICKAXES.isTagged(item.getType());
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        return Component.text("인간 굴착기 Lv." + level).color(NamedTextColor.AQUA);
    }
}
