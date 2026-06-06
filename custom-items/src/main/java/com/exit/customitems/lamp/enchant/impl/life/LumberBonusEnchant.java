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
 * 도끼로 원목 채굴 시 확률로 원목 1개 추가 드랍.
 * <ul>
 *   <li>Lv.1: 20%</li>
 *   <li>Lv.2: 35%</li>
 *   <li>Lv.3: 50%</li>
 * </ul>
 * 도끼 전용.
 */
public class LumberBonusEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_lumber_bonus";

    private final NamespacedKey key;

    public LumberBonusEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    /** 레벨(1/2/3) → 추가 드랍 확률(%). */
    public static int levelToPercent(int level) {
        return switch (level) {
            case 1  -> 20;
            case 2  -> 35;
            case 3  -> 50;
            default -> 20;
        };
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "숙련된 벌목"; }
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
        return item != null && Tag.ITEMS_AXES.isTagged(item.getType());
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        return Component.text("숙련된 벌목 Lv." + level).color(NamedTextColor.AQUA);
    }
}
