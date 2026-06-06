package com.exit.cosmetics.gacha;

import com.exit.cosmetics.model.CosmeticRarity;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * 뽑기 + 가루 설정 값 캡슐화. config.yml의 gacha:, shard: 섹션 로드.
 */
public class GachaConfig {

    private final long singlePrice;
    private final long tenPrice;
    private final long mountSinglePrice;
    private final long mountTenPrice;
    private final Map<CosmeticRarity, Integer> weights;
    private final Map<CosmeticRarity, Long> shardRefund;
    private final Map<CosmeticRarity, Long> shardExchangeCost;
    private final int totalWeight;

    public GachaConfig(FileConfiguration config) {
        this.singlePrice = config.getLong("gacha.single_price", 1000);
        this.tenPrice = config.getLong("gacha.ten_price", 9000);
        this.mountSinglePrice = config.getLong("mount_ticket.single_price", 2000);
        this.mountTenPrice = config.getLong("mount_ticket.ten_price", 18000);

        this.weights = new EnumMap<>(CosmeticRarity.class);
        this.shardRefund = new EnumMap<>(CosmeticRarity.class);
        this.shardExchangeCost = new EnumMap<>(CosmeticRarity.class);

        for (CosmeticRarity r : CosmeticRarity.values()) {
            weights.put(r, config.getInt("gacha.weights." + r.name(), defaultWeight(r)));
            shardRefund.put(r, config.getLong("shard.refund." + r.name(), defaultRefund(r)));
            shardExchangeCost.put(r, config.getLong("shard.exchange_cost." + r.name(), defaultCost(r)));
        }

        this.totalWeight = weights.values().stream().mapToInt(Integer::intValue).sum();
    }

    public long getSinglePrice() { return singlePrice; }
    public long getTenPrice() { return tenPrice; }
    public long getMountSinglePrice() { return mountSinglePrice; }
    public long getMountTenPrice() { return mountTenPrice; }
    public int getWeight(CosmeticRarity r) { return weights.getOrDefault(r, 0); }
    public int getTotalWeight() { return totalWeight; }
    public long getShardRefund(CosmeticRarity r) { return shardRefund.getOrDefault(r, 0L); }
    public long getShardExchangeCost(CosmeticRarity r) { return shardExchangeCost.getOrDefault(r, 0L); }

    private static int defaultWeight(CosmeticRarity r) {
        return switch (r) {
            case COMMON -> 0;
            case RARE -> 85;
            case UNIQUE -> 10;
            case LEGENDARY -> 5;
        };
    }

    private static long defaultRefund(CosmeticRarity r) {
        return switch (r) {
            case COMMON -> 10L;
            case RARE -> 40L;
            case UNIQUE -> 80L;
            case LEGENDARY -> 150L;
        };
    }

    private static long defaultCost(CosmeticRarity r) {
        return switch (r) {
            case COMMON -> 100L;
            case RARE -> 500L;
            case UNIQUE -> 1000L;
            case LEGENDARY -> 2000L;
        };
    }
}
