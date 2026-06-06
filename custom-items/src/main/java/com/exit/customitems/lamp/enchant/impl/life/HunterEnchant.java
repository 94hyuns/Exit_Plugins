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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * 사냥꾼 — 동물/몹 처치 시 [20~50]% 확률로 해당 엔티티의 본연 드랍 [1~2]개씩 추가 복제.
 *
 * <p>주석 처리된 MobLoot 6종 (소/양/돼지/닭/토끼/몬스터) 을 통합 대체하는 단일 인챈트.
 * 모든 생활 도구에 부착 가능하며, 처치한 엔티티가 vanilla 또는 다른 플러그인이 만들어준
 * 드랍을 그대로 비례 증가시킨다 — 양→양털·양고기, 소→소가죽·소고기 등 자동 처리.
 *
 * <p>값 스펙: [확률 20~50% (step 5), 추가 세트 수 1~2 (step 1)]
 */
public class HunterEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_hunter";

    private final NamespacedKey key;

    public HunterEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "사냥꾼"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantScope getScope()       { return EnchantScope.COMMON; }
    @Override public boolean isStackable()         { return true; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        return List.of(
            new ValueSpec(NumUtil.toStored(20.0), NumUtil.toStored(50.0), NumUtil.toStored(5.0)),
            new ValueSpec(NumUtil.toStored(1.0),  NumUtil.toStored(2.0),  NumUtil.toStored(1.0))
        );
    }

    @Override public boolean canApplyTo(ItemStack item) { return ToolCategory.isLifeTool(item); }

    @Override
    public Component renderLore(int[] values) {
        return Component.text(
            "동물 처치 시 " + NumUtil.formatPercent1(values[0])
                + " 확률로 드랍 +" + NumUtil.formatInt(values[1])
        ).color(NamedTextColor.AQUA);
    }
}
