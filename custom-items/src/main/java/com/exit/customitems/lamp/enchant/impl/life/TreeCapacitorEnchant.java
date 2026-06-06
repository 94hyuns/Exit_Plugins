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
 * 도끼로 원목 채굴 시, 연결된 같은 종 원목의 "남은 양" (= 현재 클러스터 크기) 이 임계값 이하이면
 * 한 번에 통째 벌목. 큰 나무는 사용자가 N개 이하 남도록 줄이면 마지막에 한 방으로 마무리,
 * 작은 나무 (≤N개) 는 처음 캐는 즉시 통째 벌목된다.
 * <ul>
 *   <li>Lv.1: 남은 원목 ≤ 5개 일 때 발동</li>
 *   <li>Lv.2: 남은 원목 ≤ 10개 일 때 발동 (더 큰 클러스터 처리 — 더 강력)</li>
 * </ul>
 * 도끼 전용.
 */
public class TreeCapacitorEnchant implements CustomEnchant {

    public static final String KEY_NAME = "life_tree_capacitor";

    /** 클러스터 BFS 안전 캡 (오크 거대수/정글 거대수 대비). */
    public static final int CLUSTER_CAP = 2048;

    private final NamespacedKey key;

    public TreeCapacitorEnchant(Plugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public static NamespacedKey keyOf(Plugin plugin) {
        return new NamespacedKey(plugin, KEY_NAME);
    }

    /** 레벨(1/2) → 발동 임계값(같은 종 연결 원목 개수). Lv2 가 더 큰 클러스터 처리 — 강력. */
    public static int levelToThreshold(int level) {
        return switch (level) {
            case 1  -> 5;
            case 2  -> 10;
            default -> 5;
        };
    }

    @Override public NamespacedKey getKey()        { return key; }
    @Override public String getDisplayName()       { return "벌목의 왕"; }
    @Override public EnchantCategory getCategory() { return EnchantCategory.LIFE; }
    @Override public EnchantTrigger getTrigger()   { return EnchantTrigger.LIFE_ACTION; }

    @Override
    public List<ValueSpec> getValueSpecs() {
        // 레벨 1 ~ 2
        return List.of(new ValueSpec(
            NumUtil.toStored(1.0),
            NumUtil.toStored(2.0),
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
        return Component.text("벌목의 왕 Lv." + level).color(NamedTextColor.AQUA);
    }
}
