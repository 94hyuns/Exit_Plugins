package com.exit.cosmetics.mount;

/**
 * 탈것 분류. GROUND는 지상 탈것(말 등), FLYING은 비행 탈것(팬텀 등).
 * 비행 여부에 따라 MountManager의 매 틱 비행 제어 적용 여부가 달라진다.
 */
public enum MountType {
    GROUND,
    FLYING;

    public static MountType fromString(String s) {
        if (s == null) return null;
        try {
            return MountType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
