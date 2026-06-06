package com.exit.farming.water;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * 물뿌리개 / 스프링쿨러 등급. 색상·이름·CMD·범위 메타.
 *
 * <p>아이템 CMD 예약대 (리소스팩은 별도 작업):
 * <ul>
 *   <li>5001~5003: 구리/철/다이아 물뿌리개</li>
 *   <li>5011~5013: 구리/철/다이아 스프링쿨러 (held item)</li>
 * </ul>
 */
public enum WaterTier {
    COPPER  ("구리",   "copper",   NamedTextColor.GOLD,   5001, 5011),
    IRON    ("철",     "iron",     NamedTextColor.WHITE,  5002, 5012),
    DIAMOND ("다이아", "diamond",  NamedTextColor.AQUA,   5003, 5013);

    private final String displayKor;
    private final String id;
    private final TextColor color;
    private final int wateringCanCmd;
    private final int sprinklerCmd;

    WaterTier(String displayKor, String id, TextColor color, int wateringCanCmd, int sprinklerCmd) {
        this.displayKor = displayKor;
        this.id = id;
        this.color = color;
        this.wateringCanCmd = wateringCanCmd;
        this.sprinklerCmd = sprinklerCmd;
    }

    public String displayKor() { return displayKor; }
    public String id() { return id; }
    public TextColor color() { return color; }
    public int wateringCanCmd() { return wateringCanCmd; }
    public int sprinklerCmd() { return sprinklerCmd; }

    public static WaterTier byId(String id) {
        if (id == null) return null;
        for (WaterTier t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
