package com.exit.fishing.fish;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * 물고기 등급. 크기 + 질량 합산 점수로 결정.
 * 원본 스크립트의 등급 계산 버그 수정: rank score가 실제 cm+g 합계로 판정되게 처리.
 */
public enum FishRank {
    D_MINUS("D-", NamedTextColor.GRAY),
    D("D",        NamedTextColor.GRAY),
    D_PLUS("D+",  NamedTextColor.WHITE),
    C_MINUS("C-", NamedTextColor.WHITE),
    C("C",        NamedTextColor.GREEN),
    C_PLUS("C+",  NamedTextColor.GREEN),
    B_MINUS("B-", NamedTextColor.AQUA),
    B("B",        NamedTextColor.AQUA),
    B_PLUS("B+",  NamedTextColor.BLUE),
    A_MINUS("A-", NamedTextColor.LIGHT_PURPLE),
    A("A",        NamedTextColor.LIGHT_PURPLE),
    A_PLUS("A+",  NamedTextColor.GOLD),
    S("S",        NamedTextColor.YELLOW),
    S_PLUS("S+",  NamedTextColor.RED);

    private final String display;
    private final TextColor color;

    FishRank(String display, TextColor color) {
        this.display = display;
        this.color = color;
    }

    public String display() { return display; }
    public TextColor color() { return color; }

    /**
     * 점수 기반 등급 산정. score = cm + g.
     * 구간별 경계는 원본 스크립트를 단일 함수로 통합한 것.
     */
    public static FishRank ofScore(int score) {
        if (score >= 3250) return S_PLUS;
        if (score >= 3000) return S;
        if (score >= 2500) return A_PLUS;
        if (score >= 2000) return A;
        if (score >= 1850) return A_MINUS;
        if (score >= 1400) return B_PLUS;
        if (score >= 1000) return B;
        if (score >= 900)  return B_MINUS;
        if (score >= 700)  return C_PLUS;
        if (score >= 550)  return C;
        if (score >= 400)  return C_MINUS;
        if (score >= 300)  return D_PLUS;
        if (score >= 200)  return D;
        return D_MINUS;
    }
}
