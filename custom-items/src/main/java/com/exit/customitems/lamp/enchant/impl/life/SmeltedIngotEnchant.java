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
 * 광물 블록 채굴 시 주괴(구워진) 상태로 드랍. 섬세한 손길 있을 때는 적용 안 됨.
 * 곡괭이 전용.
 *
 * <ul>
 *   <li><b>Lv 1</b> — 광석 → 주괴 변환 (개수 동일)</li>
 *   <li><b>Lv 2</b> — Lv1 + 주괴 1개 추가 드랍. 다이아/에메랄드 등 주괴 없는 광석은 광석 +1 추가</li>
 * </ul>
 *
 * 폭발 채굴 / 주변 채굴로 캐지는 부수 블록 각각에도 동일 효과 적용.
 */
public class SmeltedIngotEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_smelted_ingot";

    private final NamespacedKey key;

    public SmeltedIngotEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "급속제련"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // 레벨 1~2. 기본 bias=2.0 → Lv1=80%, Lv2=20%
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
        return Component.text("급속제련 Lv." + level).color(NamedTextColor.AQUA);
    }
}
