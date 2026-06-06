package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 물고기 물림 시 자동 감기. 레벨에 따라 대기시간이 다름.
 * <ul>
 *   <li>Lv.1: 5초</li>
 *   <li>Lv.2: 3초</li>
 *   <li>Lv.3: 1초</li>
 * </ul>
 * 낚싯대 전용.
 */
public class AutoReelEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_auto_reel";

    private final NamespacedKey key;

    public AutoReelEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    /** 레벨(1/2/3) → 대기 틱(20 tick = 1초). */
    public static int levelToDelayTicks(int level) {
        return switch (level) {
            case 1  -> 20 * 5;  // 5초
            case 2  -> 20 * 3;  // 3초
            case 3  -> 20 * 1;  // 1초
            default -> 20 * 5;
        };
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "자동 회수"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // 레벨 1 ~ 3. 가중치 6:3:1 (Lv1=60%, Lv2=30%, Lv3=10%)
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(3.0),
            NumUtil.toStored(1.0),
            new double[]{6.0, 3.0, 1.0}
        ));
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD;
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        return Component.text("자동 회수 Lv." + level).color(NamedTextColor.AQUA);
    }
}
