package com.exit.cosmetics.gacha;

import com.exit.core.data.PlayerDataManager;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;

import java.util.UUID;

/**
 * 가루(shard) 교환 서비스. 플레이어가 보유한 가루로 미보유 치장을 교환한다.
 */
public class ExchangeService {

    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    private final GachaConfig config;

    public ExchangeService(PlayerDataManager dataManager, CosmeticRegistry registry, GachaConfig config) {
        this.dataManager = dataManager;
        this.registry = registry;
        this.config = config;
    }

    public enum Result {
        SUCCESS,
        UNKNOWN_COSMETIC,
        ALREADY_OWNED,
        NOT_ENOUGH_SHARDS,
        NOT_EXCHANGEABLE,
        FAILED
    }

    /**
     * 가루로 치장 교환.
     * <ol>
     *   <li>cosmeticId 존재 확인</li>
     *   <li>미보유 확인</li>
     *   <li>가루 차감</li>
     *   <li>치장 지급</li>
     * </ol>
     * 실패하면 어느 단계에서 실패했는지 Result로 알림.
     */
    public Result exchange(UUID uuid, String cosmeticId) {
        CosmeticDefinition def = registry.get(cosmeticId);
        if (def == null) return Result.UNKNOWN_COSMETIC;

        // MOUNT 는 별도 시스템(탈것 뽑기권)으로만 획득. 가루 교환소에서 차단.
        if (def.getType() == CosmeticType.MOUNT) return Result.NOT_EXCHANGEABLE;

        if (dataManager.hasCosmetic(uuid, cosmeticId)) return Result.ALREADY_OWNED;

        long cost = config.getShardExchangeCost(def.getRarity());
        if (!dataManager.subtractShards(uuid, cost)) return Result.NOT_ENOUGH_SHARDS;

        if (!dataManager.grantCosmetic(uuid, cosmeticId)) {
            // 환불
            dataManager.addShards(uuid, cost);
            return Result.FAILED;
        }
        return Result.SUCCESS;
    }
}
