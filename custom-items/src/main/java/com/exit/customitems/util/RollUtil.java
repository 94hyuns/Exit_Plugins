package com.exit.customitems.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 인챈트 효과 발동 시 공용으로 쓰는 랜덤 유틸.
 */
public final class RollUtil {

    private RollUtil() {}

    /**
     * 저장된 퍼센트 값(×100 스케일)으로 확률 체크.
     * 예: storedPercent = 3500 → 35% 발동 확률.
     */
    public static boolean percentRoll(int storedPercent) {
        double pct = NumUtil.fromStored(storedPercent);
        return ThreadLocalRandom.current().nextDouble() * 100.0 < pct;
    }

    /**
     * 저장된 정수값(×100)을 "실제 사용 정수"로 변환.
     * 예: 200 → 2. 개수 계산 등에 사용.
     */
    public static int asCount(int storedValue) {
        return (int) NumUtil.fromStored(storedValue);
    }

    /** [min, max] 정수 균등 랜덤. */
    public static int range(int minInclusive, int maxInclusive) {
        if (minInclusive >= maxInclusive) return minInclusive;
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    /** 가중치 기반 인덱스 선택. 모든 가중치가 0이면 0 반환. */
    public static int weightedPick(int... weights) {
        int total = 0;
        for (int w : weights) total += Math.max(0, w);
        if (total <= 0) return 0;
        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += Math.max(0, weights[i]);
            if (r < acc) return i;
        }
        return weights.length - 1;
    }
}
