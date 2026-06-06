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

/** 괭이 전용 농작물 수확 추가 드랍 인챈트 2종 (작물 / 씨앗). */
public final class CropBonusEnchants {

    private CropBonusEnchants() {}

    private static abstract class Base implements CustomEnchant {
        private final NamespacedKey key;
        private final String label;

        Base(Plugin plugin, String keyName, String label) {
            this.key = new NamespacedKey(plugin, keyName);
            this.label = label;
        }

        @Override public NamespacedKey getKey()        { return key; }
        @Override public String getDisplayName()       { return "농작물 추가 " + label; }
        @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
        @Override public boolean isStackable()         { return true; }
        @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

        @Override
        public List<ValueSpec> getValueSpecs() {
            return List.of(
                // 확률: 30/35/40/45/50 — 30% 가 30% 강세, 나머지 4값이 17.5%씩 균등
                // 가중치 30 : 17.5 : 17.5 : 17.5 : 17.5 = 60 : 35 : 35 : 35 : 35
                new ValueSpec(NumUtil.toStored(30.0), NumUtil.toStored(50.0), NumUtil.toStored(5.0),
                        new double[]{60.0, 35.0, 35.0, 35.0, 35.0}),
                // 개수: 1/2/3 — 1=50%, 2,3=25%씩
                new ValueSpec(NumUtil.toStored(1.0),  NumUtil.toStored(3.0),  NumUtil.toStored(1.0),
                        new double[]{2.0, 1.0, 1.0})
            );
        }

        @Override
        public boolean canApplyTo(ItemStack item) {
            return item != null && Tag.ITEMS_HOES.isTagged(item.getType());
        }

        @Override
        public Component renderLore(int[] values) {
            return Component.text(
                "농작물 수확 시 " + NumUtil.formatPercent1(values[0])
                    + " 확률로 " + label + " +" + NumUtil.formatInt(values[1])
            ).color(NamedTextColor.AQUA);
        }
    }

    public static class BonusCrop extends Base {
        public static final String KEY_NAME = "life_crop_bonus_crop";
        public BonusCrop(Plugin plugin) { super(plugin, KEY_NAME, "작물"); }
        public static NamespacedKey keyOf(Plugin plugin) { return new NamespacedKey(plugin, KEY_NAME); }
    }

    public static class BonusSeed extends Base {
        public static final String KEY_NAME = "life_crop_bonus_seed";
        public BonusSeed(Plugin plugin) { super(plugin, KEY_NAME, "씨앗"); }
        public static NamespacedKey keyOf(Plugin plugin) { return new NamespacedKey(plugin, KEY_NAME); }
    }
}
