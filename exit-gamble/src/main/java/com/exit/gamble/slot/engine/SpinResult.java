package com.exit.gamble.slot.engine;

import com.exit.gamble.slot.symbol.SlotSymbol;

public record SpinResult(
        SlotSymbol[] reels,
        long bet,
        long payout,
        boolean isWin,
        boolean isJackpot
) {
    public SlotSymbol reel(int idx) {
        return reels[idx];
    }
}
