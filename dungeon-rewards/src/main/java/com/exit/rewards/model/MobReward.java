package com.exit.rewards.model;

import java.util.List;
import java.util.Random;

/**
 * 한 종류의 몹에 대한 전체 보상 정의.
 */
public record MobReward(
        String mobId,
        int minMoney,
        int maxMoney,
        String messageOnKill,  // nullable, {money} 플레이스홀더 지원
        List<RewardItem> drops
) {

    private static final Random RANDOM = new Random();

    /** minMoney~maxMoney 사이 랜덤 정수 반환 */
    public long rollMoney() {
        if (minMoney >= maxMoney) return minMoney;
        return minMoney + RANDOM.nextInt(maxMoney - minMoney + 1);
    }
}
