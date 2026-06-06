package com.exit.gamble.slot.symbol;

import org.bukkit.Material;

public record SlotSymbol(
        String id,
        String displayName,
        Material material,
        int weight,
        int payout3
) {}
