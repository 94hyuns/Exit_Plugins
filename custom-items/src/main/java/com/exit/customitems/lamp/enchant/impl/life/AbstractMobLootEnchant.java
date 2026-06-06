package com.exit.customitems.lamp.enchant.impl.life;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantScope;
import com.exit.customitems.lamp.enchant.EnchantTrigger;
import com.exit.customitems.lamp.enchant.ValueSpec;
import com.exit.customitems.util.NumUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * "XX 처치 시 [10~50]% 확률로 전리품 [1~2]개 추가" 공통 구조.
 * 서브클래스는 매칭 엔티티와 표시 이름만 정의.
 *
 * <p>값 스펙: [확률 10~50%(0.5단위), 추가 세트 수 1~2(1단위)]
 */
public abstract class AbstractMobLootEnchant implements CustomEnchant {

    private final NamespacedKey key;
    private final String targetName;

    protected AbstractMobLootEnchant(Plugin plugin, String keyName, String targetName) {
        this.key = new NamespacedKey(plugin, keyName);
        this.targetName = targetName;
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return targetName + " 처치 전리품"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public boolean isStackable()         { return true; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        return List.of(
            new ValueSpec(NumUtil.toStored(10.0), NumUtil.toStored(50.0), NumUtil.toStored(0.5)),
            new ValueSpec(NumUtil.toStored(1.0),  NumUtil.toStored(2.0),  NumUtil.toStored(1.0))
        );
    }

    @Override
    public boolean canApplyTo(ItemStack item) {
        return ToolCategory.isLifeTool(item);
    }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(
            targetName + " 처치 시 " + NumUtil.formatPercent1(values[0])
                + " 확률로 전리품 +" + NumUtil.formatInt(values[1])
        ).color(NamedTextColor.AQUA);
    }

    /** 이 인챈트가 반응하는 엔티티 종류인지. 서브클래스가 정의. */
    public abstract boolean matches(EntityType type);
}
