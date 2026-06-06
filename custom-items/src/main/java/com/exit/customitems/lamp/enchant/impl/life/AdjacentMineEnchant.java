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
 * 블록 채굴 시 주변 1칸 블록도 동시 채굴.
 * <ul>
 *   <li>Lv.1: 상·하 (총 +2 블록)</li>
 *   <li>Lv.2: 상·하·좌·우 (총 +4 블록)</li>
 * </ul>
 * 곡괭이 전용. 야생 한정.
 */
public class AdjacentMineEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_adjacent_mine";

    private final NamespacedKey key;

    public AdjacentMineEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "채굴의 달인"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // Level 1 (상하) / Level 2 (상하좌우) — 정수 레벨 값으로 저장
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(2.0),
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
        return Component.text("채굴의 달인 Lv." + level).color(NamedTextColor.AQUA);
    }
}
