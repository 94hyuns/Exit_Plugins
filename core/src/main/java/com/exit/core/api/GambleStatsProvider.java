package com.exit.core.api;

import java.util.UUID;

/**
 * 도박 미니게임 누적 통계 조회 API.
 * ExitGamble 플러그인이 구현체를 ServiceRegistry 에 등록.
 * /관리자검사 등에서 플레이어별 누적 베팅·회수 금액 조회용.
 */
public interface GambleStatsProvider {

    /** 슬롯머신 누적 베팅 금액 (한 번도 안 했으면 0). */
    long getSlotBetTotal(UUID player);

    /** 슬롯머신 누적 지급 금액. */
    long getSlotPayoutTotal(UUID player);

    /** 복권 누적 베팅 금액 (티켓 구매 합계). */
    long getLotteryBetTotal(UUID player);

    /** 복권 누적 당첨 금액. */
    long getLotteryPayoutTotal(UUID player);

    /** 한 번에 모두 조회 (record). */
    default Stats getAll(UUID player) {
        return new Stats(
                getSlotBetTotal(player), getSlotPayoutTotal(player),
                getLotteryBetTotal(player), getLotteryPayoutTotal(player));
    }

    record Stats(long slotBet, long slotPayout, long lotteryBet, long lotteryPayout) {
        public long slotNet() { return slotPayout - slotBet; }
        public long lotteryNet() { return lotteryPayout - lotteryBet; }
        public long totalNet() { return slotNet() + lotteryNet(); }
    }
}
