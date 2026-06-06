package com.exit.cosmetics.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * 치장 등급. 뽑기 가중치, 가루 환불량, 교환가가 등급별로 설정된다.
 */
public enum CosmeticRarity {
    COMMON("§f일반", NamedTextColor.WHITE),
    RARE("§9레어", NamedTextColor.BLUE),
    UNIQUE("§d유니크", NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("§6전설", NamedTextColor.GOLD);

    private final String displayName;
    private final TextColor color;

    CosmeticRarity(String displayName, TextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() { return displayName; }
    public TextColor getColor() { return color; }

    public static CosmeticRarity fromString(String s) {
        if (s == null) return null;
        try {
            return CosmeticRarity.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
