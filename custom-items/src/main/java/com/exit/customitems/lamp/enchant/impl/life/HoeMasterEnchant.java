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
 * 괭이의 달인 — 농작물 수확 시 플레이어 시선 방향으로 앞쪽 N-1칸의 작물도 동시 수확.
 * 괭이 전용. 기본 bias=2.0 → Lv1 80%, Lv2 20%.
 *
 * <ul>
 *   <li>Lv 1: 1x2 (본체 + 앞 1칸)</li>
 *   <li>Lv 2: 1x3 (본체 + 앞 2칸)</li>
 * </ul>
 *
 * 부수 작물에도 행운의 작물(LuckyCropEnchant) 보너스가 적용됨 — 폭발채굴/트리캐퍼와 동일 패턴.
 */
public class HoeMasterEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_hoe_master";

    private final NamespacedKey key;

    public HoeMasterEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "괭이의 달인"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(2.0),
            NumUtil.toStored(1.0)
        ));
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return item != null && Tag.ITEMS_HOES.isTagged(item.getType());
    }

    @Override
    public Component renderLore(int[] values) {
        int level = (int) NumUtil.fromStored(values[0]);
        return Component.text("괭이의 달인 Lv." + level).color(NamedTextColor.AQUA);
    }
}
