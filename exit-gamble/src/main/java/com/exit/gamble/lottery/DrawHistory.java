package com.exit.gamble.lottery;

import java.util.List;

public record DrawHistory(
        long drawTime,
        int winningNumber,
        long potBeforeDraw,
        List<String> winnerNames,   // 분할 시 여러명
        long perPersonPayout         // 0 = 당첨자 없음 (이월)
) {
    public boolean hasWinner() { return !winnerNames.isEmpty(); }
}
