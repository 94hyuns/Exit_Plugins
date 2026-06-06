package com.exit.customitems.lamp.mutation;

import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.CustomEnchant;
import com.exit.customitems.lamp.enchant.EnchantCategory;
import com.exit.customitems.lamp.enchant.EnchantRegistry;
import com.exit.customitems.lamp.enchant.EnchantTier;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.ValueSpec;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 무기용 변성 전략: 대상 무기에 적용 가능한 SET(유니크) 인챈트 풀에서
 * 이미 부여된 UNIQUE 와 중복되지 않는 항목을 무작위 1개 선택하여 **최대 레벨**로 추가.
 *
 * <p>L3 고정 (모든 무기 유니크는 1~3렙이며, L3 = 최상 효과). 다중 ValueSpec 의 경우 첫 값만 max 처리.
 */
public class WeaponMutationStrategy implements MutationStrategy {

    private final EnchantRegistry registry;

    public WeaponMutationStrategy(EnchantRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean canApply(ItemStack target) {
        return ToolCategory.isCombatWeapon(target);
    }

    /** 무기 변성: 50% 성공 / 20% 초대박 (UNIQUE 2줄) / 10% 파괴 / 20% 실패. */
    @Override
    public Probabilities probabilities() {
        return new Probabilities(0.50, 0.20, 0.10);
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

        CustomEnchant chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));

        // 모든 ValueSpec 의 max 값으로 세팅 → L3 보장
        List<ValueSpec> specs = chosen.getValueSpecs();
        int[] values = new int[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            values[i] = specs.get(i).max();
        }

        // ValueSpec 가 비어있는 인챈트(예: 일부 방어구 SET) 는 빈 값 배열로 저장
        return List.of(new RolledEnchant(chosen, values));
    }
}
