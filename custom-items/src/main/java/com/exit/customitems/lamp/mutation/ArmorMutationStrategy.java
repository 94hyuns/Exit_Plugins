package com.exit.customitems.lamp.mutation;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantRegistry;
import com.exit.customitems.lamp.enchant.EnchantTier;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.util.RollUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 방어구용 변성 전략: 적용 가능한 SET 풀에서 변성램프 가중치 기준으로 선택.
 *
 * <p>방어구 변성은 SET 라인만 추가 (BASIC 추가 X — 무기와 달리 BASIC 슬롯 갈등 없음).
 * 변성 가중치는 enchants.yml: armor_set_weights.&lt;enchantId&gt;.mutation 에서.
 * 기본 카테고리는 행운(lucky) 비중이 높음 (24/100).
 */
public class ArmorMutationStrategy implements MutationStrategy {

    private final EnchantRegistry registry;
    private final EnchantConfig ec;

    public ArmorMutationStrategy(EnchantRegistry registry, EnchantConfig ec) {
        this.registry = registry;
        this.ec = ec;
    }

    @Override
    public boolean canApply(ItemStack target) {
        return ToolCategory.isArmor(target);
    }

    @Override
    public List<RolledEnchant> rollBonus(ItemStack target, List<RolledEnchant> existing) {
        Set<NamespacedKey> existingKeys = new HashSet<>();
        for (RolledEnchant r : existing) existingKeys.add(r.enchant().getKey());

        List<CustomEnchant> applicable = registry.getApplicable(target, EnchantCategory.COMBAT);
        List<CustomEnchant> candidates = new ArrayList<>();
        for (CustomEnchant e : applicable) {
            if (e.getTier() != EnchantTier.SET) continue;
            if (existingKeys.contains(e.getKey())) continue;
            candidates.add(e);
        }
        if (candidates.isEmpty()) return List.of();

        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = ec.readSetWeight(candidates.get(i).getKey().getKey(), "mutation", 10);
        }
        CustomEnchant chosen = candidates.get(RollUtil.weightedPick(weights));

        // 방어구 SET 인챈트는 값 없음 (부위 수가 레벨). 빈 int[] 로 저장.
        return List.of(new RolledEnchant(chosen, new int[0]));
    }
}
