package com.exit.customitems.lamp.enchant;

import com.exit.customitems.lamp.LampConfig;
import com.exit.customitems.util.RollUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 램프 롤의 핵심 로직.
 *
 * <p>LIFE 카테고리: 기존 평면 롤 (풀 전체에서 N줄 가중 선택).
 * <p>COMBAT 카테고리: 1줄 = BASIC 만, 2줄 = BASIC + SET. 무기/방어구 동일.
 */
public class EnchantRoller {

    private final EnchantRegistry registry;
    private final LampConfig config;
    private final EnchantConfig enchantConfig;

    public EnchantRoller(EnchantRegistry registry, LampConfig config, EnchantConfig enchantConfig) {
        this.registry = registry;
        this.config = config;
        this.enchantConfig = enchantConfig;
    }

    public List<RolledEnchant> roll(ItemStack tool, EnchantCategory category) {
        List<CustomEnchant> pool = registry.getApplicable(tool, category);
        if (pool.isEmpty()) return List.of();

        if (category == EnchantCategory.COMBAT) {
            return rollCombat(tool, pool);
        }
        return rollFlat(pool);
    }

    // ─── COMBAT: BASIC + (선택) SET ───
    //
    // 무기/방어구 동일 정책:
    //   - 1줄: BASIC
    //   - 2줄: BASIC + SET (line 2 는 SET only — BASIC 중복 불가)
    // 방어구는 SET 라인을 enchants.yml 의 armor_set_weights.normal 가중치로 가중 선택.
    // 무기는 기존 COMMON/TOOL_SPECIFIC weightOf 사용.
    private List<RolledEnchant> rollCombat(ItemStack tool, List<CustomEnchant> pool) {
        List<CustomEnchant> basic = filterByTier(pool, EnchantTier.BASIC);
        List<CustomEnchant> set = filterByTier(pool, EnchantTier.SET);
        boolean isArmor = com.exit.customitems.lamp.ToolCategory.isArmor(tool);

        int maxLines = set.isEmpty() ? 1 : 2;
        int[] weights = config.getLineCountWeightsCombat();
        int lineCount = rollLineCount(maxLines, weights);

        List<RolledEnchant> out = new ArrayList<>();
        if (!basic.isEmpty()) {
            out.add(rollValues(pickWeighted(basic)));
        }

        if (lineCount >= 2 && !set.isEmpty()) {
            CustomEnchant pick = isArmor ? pickArmorSet(set, false) : pickWeighted(set);
            out.add(rollValues(pick));
        }

        // BASIC 이 비고 SET 만 있는 비정상 케이스: SET 1개로 폴백.
        if (out.isEmpty() && !set.isEmpty()) {
            CustomEnchant pick = isArmor ? pickArmorSet(set, false) : pickWeighted(set);
            out.add(rollValues(pick));
        }
        return out;
    }

    /** 방어구 SET 풀에서 enchants.yml 가중치 기준 가중 선택. mutation=true 면 변성램프 가중치. */
    private CustomEnchant pickArmorSet(List<CustomEnchant> candidates, boolean mutation) {
        if (candidates.isEmpty()) return null;
        String column = mutation ? "mutation" : "normal";
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            String id = candidates.get(i).getKey().getKey();
            weights[i] = enchantConfig.readSetWeight(id, column, 10);
        }
        return candidates.get(RollUtil.weightedPick(weights));
    }

    private List<CustomEnchant> filterByTier(List<CustomEnchant> pool, EnchantTier tier) {
        List<CustomEnchant> out = new ArrayList<>();
        for (CustomEnchant e : pool) {
            if (e.getTier() == tier) out.add(e);
        }
        return out;
    }

    private CustomEnchant pickWeighted(List<CustomEnchant> candidates) {
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) weights[i] = weightOf(candidates.get(i));
        return candidates.get(RollUtil.weightedPick(weights));
    }

    private RolledEnchant rollValues(CustomEnchant e) {
        List<ValueSpec> specs = e.getValueSpecs();
        int[] values = new int[specs.size()];
        for (int i = 0; i < specs.size(); i++) values[i] = rollValue(specs.get(i));
        return new RolledEnchant(e, values);
    }

    // ─── LIFE: 기존 평면 롤 ───

    private List<RolledEnchant> rollFlat(List<CustomEnchant> pool) {
        int lineCount = rollLineCount(pool.size(), config.getLineCountWeights());
        List<CustomEnchant> picks = pickDistinct(pool, lineCount);

        List<RolledEnchant> out = new ArrayList<>(picks.size());
        for (CustomEnchant e : picks) {
            out.add(rollValues(e));
        }
        return out;
    }

    // --- line count ---

    private int rollLineCount(int poolSize, int[] weights) {
        int totalWeight = 0;
        for (int w : weights) totalWeight += Math.max(0, w);
        if (totalWeight <= 0) return 1;

        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        int acc = 0;
        int chosen = 1;
        for (int i = 0; i < weights.length; i++) {
            int w = Math.max(0, weights[i]);
            acc += w;
            if (r < acc) { chosen = i + 1; break; }
        }
        return Math.min(chosen, poolSize);
    }

    // --- enchant selection (LIFE 평면 롤용) ---

    private List<CustomEnchant> pickDistinct(List<CustomEnchant> pool, int n) {
        List<CustomEnchant> picked = new ArrayList<>();
        Set<NamespacedKey> usedNonStackable = new HashSet<>();

        while (picked.size() < n) {
            List<CustomEnchant> candidates = new ArrayList<>();
            for (CustomEnchant e : pool) {
                if (e.isStackable() || !usedNonStackable.contains(e.getKey())) {
                    candidates.add(e);
                }
            }
            if (candidates.isEmpty()) break;

            int[] weights = new int[candidates.size()];
            for (int i = 0; i < candidates.size(); i++) weights[i] = weightOf(candidates.get(i));
            int idx = RollUtil.weightedPick(weights);
            CustomEnchant chosen = candidates.get(idx);

            picked.add(chosen);
            if (!chosen.isStackable()) usedNonStackable.add(chosen.getKey());
        }
        return picked;
    }

    private int weightOf(CustomEnchant e) {
        return switch (e.getScope()) {
            case COMMON        -> config.getWeightCommon();
            case TOOL_SPECIFIC -> config.getWeightToolSpecific();
        };
    }

    // --- value roll ---

    private int rollValue(ValueSpec spec) {
        int count = spec.candidateCount();
        if (count == 1) return spec.min();

        double[] weights;
        if (spec.hasExplicitWeights()) {
            // 명시 가중치 우선 — 후보 개수와 길이 일치는 ValueSpec 생성자가 검증
            weights = spec.explicitWeights();
        } else {
            double bias = spec.hasExplicitBias()
                    ? Math.max(0.0, spec.bias())
                    : Math.max(0.0, config.getLevelBias());
            weights = new double[count];
            for (int i = 0; i < count; i++) {
                weights[i] = Math.pow(count - i, bias);
            }
        }

        double total = 0.0;
        for (double w : weights) total += Math.max(0.0, w);
        double r = ThreadLocalRandom.current().nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < count; i++) {
            acc += Math.max(0.0, weights[i]);
            if (r < acc) return spec.candidateAt(i);
        }
        return spec.candidateAt(count - 1);
    }
}
