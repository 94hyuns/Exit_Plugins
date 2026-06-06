package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 모래/붉은모래 채굴 시 유리 블록으로 드랍. 값 스펙 없음 (항상 발동).
 * 삽 전용.
 */
public class SandToGlassEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_sand_to_glass";

    private final NamespacedKey key;

    public SandToGlassEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "모래 유리화"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override public List<ValueSpec> getValueSpecs() { return List.of(); }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return item != null && Tag.ITEMS_SHOVELS.isTagged(item.getType());
    }

    @Override
    public Component renderLore(int[] values) {
        return Component.text("모래 유리화").color(NamedTextColor.AQUA);
    }
}
