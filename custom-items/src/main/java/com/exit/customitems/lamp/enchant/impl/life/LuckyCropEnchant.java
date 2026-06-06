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
 * 행운의 작물 — 농작물 수확 시 Lv 별 확률로 작물 +2 드랍. 괭이 전용.
 *
 * <ul>
 *   <li>Lv 1: 30% 확률 (가중치 7)</li>
 *   <li>Lv 2: 35% 확률 (가중치 5)</li>
 *   <li>Lv 3: 40% 확률 (가중치 4)</li>
 *   <li>Lv 4: 45% 확률 (가중치 3)</li>
 *   <li>Lv 5: 50% 확률 (가중치 1)</li>
 * </ul>
 *
 * 명함(Lv1) 약 35%, 종결(Lv5) 약 5% 분포.
 * 괭이의 달인으로 수확된 부수 작물에도 확률 적용.
 */
public class LuckyCropEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_lucky_crop";

    /** 레벨별 추가 드랍 발동 확률 (%). */
    private static final int[] LEVEL_PERCENTS = {30, 35, 40, 45, 50};
    /** Lv 발동 시 추가 드랍 개수. */
    public static final int BONUS_AMOUNT = 2;

    private final NamespacedKey key;

    public LuckyCropEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    public static int levelToPercent(int level) {
        if (level < 1) return LEVEL_PERCENTS[0];
        if (level > LEVEL_PERCENTS.length) return LEVEL_PERCENTS[LEVEL_PERCENTS.length - 1];
        return LEVEL_PERCENTS[level - 1];
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "행운의 작물"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // Lv 1~5, 가중치 7:5:4:3:1 (Lv1 35% / Lv5 5%)
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(5.0),
            NumUtil.toStored(1.0),
            new double[]{7.0, 5.0, 4.0, 3.0, 1.0}
        ));
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return item != null && Tag.ITEMS_HOES.isTagged(item.getType());
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        return Component.text("행운의 작물 Lv." + level).color(NamedTextColor.AQUA);
    }
}
