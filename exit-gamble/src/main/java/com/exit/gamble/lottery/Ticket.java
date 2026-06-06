package com.exit.gamble.lottery;

import java.util.UUID;

public record Ticket(
        UUID owner,
        String ownerName,   // 표시용 (UUID 만으로는 오프라인 lookup 비용)
        int number,         // 0 ~ (numberRange-1)
        long purchaseTime   // epoch millis
) {}
