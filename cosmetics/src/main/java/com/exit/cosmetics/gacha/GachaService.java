package com.exit.cosmetics.gacha;

import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * 뽑기 로직. 등급 가중치로 먼저 등급을 결정한 뒤 해당 등급 내 치장 중 균등 확률로 선택.
 *
 * <p>변경 사항 (뽑기권 시스템): 즉시 뽑기는 제거됨. 이 서비스는 "추첨 + 지급" 로직만 제공하며,
 * 결제는 TicketManager에서 뽑기권 구매 시에 이미 발생한 상태. 뽑기권 사용 시 {@link #drawAndApply} 호출.
 */
public class GachaService {

    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    private final GachaConfig config;
    private final Random random = new Random();

    public GachaService(PlayerDataManager dataManager, CosmeticRegistry registry, GachaConfig config) {
        this.dataManager = dataManager;
        this.registry = registry;
        this.config = config;
    }

    /**
     * 1회 추첨 + 지급/가루 환불. 뽑기권 사용 시 AnimationService 콜백에서 호출됨.
     * 카탈로그 비어있으면 null 반환.
     */
    public GachaResult drawAndApply(UUID uuid) {
        if (registry.getAll().isEmpty()) return null;

        CosmeticDefinition drawn = draw();
        boolean alreadyOwned = dataManager.hasCosmetic(uuid, drawn.getId());

        if (alreadyOwned) {
            long refund = config.getShardRefund(drawn.getRarity());
            if (refund > 0) dataManager.addShards(uuid, refund);
            return new GachaResult(drawn, true, refund);
        } else {
            dataManager.grantCosmetic(uuid, drawn.getId());
            return new GachaResult(drawn, false, 0);
        }
    }

    /**
     * 10회 연속 추첨 + 지급. 현재 NPC UI에는 미노출. 향후 콘텐츠 준비되면 연결.
     */
    public List<GachaResult> drawAndApplyTen(UUID uuid) {
        if (registry.getAll().isEmpty()) return null;
        List<GachaResult> results = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) results.add(drawAndApply(uuid));
        return results;
    }

    // ─── 내부 추첨 ───

    private CosmeticDefinition draw() {
        CosmeticRarity rarity = pickRarity();
        List<CosmeticDefinition> pool = poolFor(rarity);
        if (pool.isEmpty()) pool = fallbackPool(rarity);
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * 치장 뽑기 풀: 같은 등급 + WEAPON 만 (1차 출시).
     * 다른 타입을 풀면 이 필터를 완화하면 됨.
     */
    private List<CosmeticDefinition> poolFor(CosmeticRarity rarity) {
        return registry.getByRarity(rarity).stream()
                .filter(d -> d.getType() == CosmeticType.WEAPON)
                .collect(Collectors.toList());
    }

    private CosmeticRarity pickRarity() {
        int total = config.getTotalWeight();
        if (total <= 0) return CosmeticRarity.COMMON;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (CosmeticRarity r : CosmeticRarity.values()) {
            cumulative += config.getWeight(r);
            if (roll < cumulative) return r;
        }
        return CosmeticRarity.COMMON;
    }

    private List<CosmeticDefinition> fallbackPool(CosmeticRarity missing) {
        CosmeticRarity[] order = { CosmeticRarity.LEGENDARY, CosmeticRarity.UNIQUE, CosmeticRarity.RARE, CosmeticRarity.COMMON };
        for (CosmeticRarity r : order) {
            if (r == missing) continue;
            List<CosmeticDefinition> pool = poolFor(r);
            if (!pool.isEmpty()) return pool;
        }
        // 모든 등급에서 비-MOUNT 가 비어있다면 마지막 fallback (그래도 MOUNT 는 빼고)
        return registry.getAll().stream()
                .filter(d -> d.getType() != CosmeticType.MOUNT)
                .collect(Collectors.toList());
    }
}
