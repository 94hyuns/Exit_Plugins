package com.exit.gamble.slot.engine;

import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.symbol.SlotSymbol;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ReelEngine {

    private final SlotConfig config;

    public ReelEngine(SlotConfig config) {
        this.config = config;
    }

    public SlotSymbol pickRandom() {
        List<SlotSymbol> symbols = config.symbols();
        int total = 0;
        for (SlotSymbol s : symbols) total += s.weight();
        int roll = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (SlotSymbol s : symbols) {
            acc += s.weight();
            if (roll < acc) return s;
        }
        return symbols.get(symbols.size() - 1);
    }

    public SpinResult spin(long bet) {
        SlotSymbol[] reels = new SlotSymbol[] { pickRandom(), pickRandom(), pickRandom() };
        applyTwoMatchCompletion(reels);
        applyNearMissBias(reels);
        return evaluate(reels, bet);
    }

    /**
     * 자연 발생 2매칭(AAB/ABA/BAA) 에 한해 일정 확률로 셋째 reel 까지 동일 심볼로 덮어써
     * 3매칭 승격. "거의 다 맞췄는데 가끔 진짜 터지네" 체감.
     *
     * <p>⚠ RTP 영향 있음. c × 2매칭_확률_합(≈57%) 만큼 RTP 가산.
     *   예: c=0.015 (1.5%) → RTP 약 +12%p.
     */
    private void applyTwoMatchCompletion(SlotSymbol[] reels) {
        double p = config.twoMatchCompletionProbability();
        if (p <= 0.0) return;

        boolean s01 = reels[0].id().equals(reels[1].id());
        boolean s12 = reels[1].id().equals(reels[2].id());
        boolean s02 = reels[0].id().equals(reels[2].id());

        // 이미 3매칭 또는 ABC 면 패스
        if (s01 && s12) return;     // AAA
        if (!s01 && !s12 && !s02) return; // ABC

        int oddIdx;
        SlotSymbol dup;
        if (s01) {            // AAB
            oddIdx = 2; dup = reels[0];
        } else if (s02) {     // ABA
            oddIdx = 1; dup = reels[0];
        } else {              // BAA  (s12 true, 나머지 false)
            oddIdx = 0; dup = reels[1];
        }

        if (ThreadLocalRandom.current().nextDouble() < p) {
            reels[oddIdx] = dup;
        }
    }

    /**
     * 세 심볼이 모두 다른 (ABC) 결과에 한해 일정 확률로
     * {@code reel2 ← reel1} 로 덮어써서 "두 개 맞췄는데 셋째 빗나감" 가시성을 ↑.
     *
     * <p>3매치/2-3매치/1-3매치 등 이미 어떤 매치가 있는 결과는 건드리지 않으므로
     * RTP, 3매치 확률, 잭팟 확률 모두 변하지 않는다.
     */
    private void applyNearMissBias(SlotSymbol[] reels) {
        double p = config.nearMissProbability();
        if (p <= 0.0) return;
        boolean allDistinct = !reels[0].id().equals(reels[1].id())
                && !reels[1].id().equals(reels[2].id())
                && !reels[0].id().equals(reels[2].id());
        if (!allDistinct) return;
        if (ThreadLocalRandom.current().nextDouble() < p) {
            reels[1] = reels[0];
        }
    }

    public SpinResult evaluate(SlotSymbol[] reels, long bet) {
        boolean allSame = reels[0].id().equals(reels[1].id())
                && reels[1].id().equals(reels[2].id());
        long payout = 0;
        boolean jackpot = false;
        if (allSame) {
            payout = (long) reels[0].payout3() * bet;
            jackpot = reels[0].id().equals(config.jackpotSymbolId());
        }
        return new SpinResult(reels, bet, payout, allSame, jackpot);
    }
}
