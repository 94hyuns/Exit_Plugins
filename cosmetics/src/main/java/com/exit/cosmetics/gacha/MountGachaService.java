package com.exit.cosmetics.gacha;

import com.exit.core.api.CosmeticProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.mount.MountDefinition;
import com.exit.cosmetics.mount.MountRegistry;
import com.exit.cosmetics.registry.CosmeticRegistry;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 탈것 뽑기. 등급 가중치는 GachaConfig를 그대로 재사용 (치장 풀과 분리).
 * 결과는 합성 cosmetic id ("mount/&lt;id&gt;") 로 CosmeticProvider에 grant.
 */
public class MountGachaService {

    private final PlayerDataManager dataManager;
    private final MountRegistry mountRegistry;
    private final CosmeticRegistry cosmeticRegistry;
    private final CosmeticProvider provider;
    private final GachaConfig config;
    private final Random random = new Random();

    public MountGachaService(PlayerDataManager dataManager, MountRegistry mountRegistry,
                             CosmeticRegistry cosmeticRegistry, CosmeticProvider provider,
                             GachaConfig config) {
        this.dataManager = dataManager;
        this.mountRegistry = mountRegistry;
        this.cosmeticRegistry = cosmeticRegistry;
        this.provider = provider;
        this.config = config;
    }

    /** 1회 추첨 + 지급/가루 환불. */
    public GachaResult drawAndApply(UUID uuid) {
        if (mountRegistry.getAll().isEmpty()) return null;

        MountDefinition drawn = draw();
        String key = drawn.getOwnershipKey();

        boolean alreadyOwned = dataManager.hasCosmetic(uuid, key);
        if (alreadyOwned) {
            long refund = config.getShardRefund(drawn.getRarity());
            if (refund > 0) dataManager.addShards(uuid, refund);
            return new GachaResult(cosmeticRegistry.get(key), true, refund);
        } else {
            provider.grantCosmetic(uuid, key);
            return new GachaResult(cosmeticRegistry.get(key), false, 0);
        }
    }

    private MountDefinition draw() {
        CosmeticRarity rarity = pickRarity();
        List<MountDefinition> pool = mountRegistry.getByRarity(rarity);
        if (pool.isEmpty()) pool = fallbackPool(rarity);
        return pickWeighted(pool);
    }

    /**
     * 풀 안에서 mount 의 drawWeight 비율로 무작위 선택.
     * 모든 weight가 0이거나 풀이 비어있을 때만 균등 fallback.
     */
    private MountDefinition pickWeighted(List<MountDefinition> pool) {
        int total = 0;
        for (MountDefinition d : pool) total += Math.max(0, d.getDrawWeight());
        if (total <= 0) return pool.get(random.nextInt(pool.size()));
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (MountDefinition d : pool) {
            cumulative += Math.max(0, d.getDrawWeight());
            if (roll < cumulative) return d;
        }
        return pool.get(pool.size() - 1);
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

    private List<MountDefinition> fallbackPool(CosmeticRarity missing) {
        CosmeticRarity[] order = { CosmeticRarity.LEGENDARY, CosmeticRarity.UNIQUE, CosmeticRarity.RARE, CosmeticRarity.COMMON };
        for (CosmeticRarity r : order) {
            if (r == missing) continue;
            List<MountDefinition> pool = mountRegistry.getByRarity(r);
            if (!pool.isEmpty()) return pool;
        }
        return new java.util.ArrayList<>(mountRegistry.getAll());
    }
}
