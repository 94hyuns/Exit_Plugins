package com.exit.fishing.season;

/**
 * 4계절. 각 계절은 6종의 제철(in-season) 어종을 가진다.
 * 제철/비제철 분류는 FishRegistry에서 관리.
 */
public enum Season {
    SPRING("봄"),
    SUMMER("여름"),
    AUTUMN("가을"),
    WINTER("겨울");

    private final String korean;

    Season(String korean) {
        this.korean = korean;
    }

    public String korean() {
        return korean;
    }

    public Season next() {
        return switch (this) {
            case SPRING -> SUMMER;
            case SUMMER -> AUTUMN;
            case AUTUMN -> WINTER;
            case WINTER -> SPRING;
        };
    }

    /** 반대 계절 (봄-가을, 여름-겨울) */
    public Season opposite() {
        return switch (this) {
            case SPRING -> AUTUMN;
            case AUTUMN -> SPRING;
            case SUMMER -> WINTER;
            case WINTER -> SUMMER;
        };
    }

    public static Season fromKorean(String s) {
        for (Season v : values()) if (v.korean.equals(s)) return v;
        return null;
    }
}
