package com.exit.customitems.util;

/**
 * 소수 수치를 정수(×100)로 통일 저장하기 위한 헬퍼.
 * 모든 PDC 저장값과 롤 계산은 정수(×100) 단위로 수행하고,
 * 표시/이펙트 계산 시에만 double로 변환한다.
 */
public final class NumUtil {

    /** 저장 스케일. 1.00 = 100, 0.10 = 10, 0.01 = 1. */
    public static final int SCALE = 100;

    private NumUtil() {}

    /** double → 저장용 정수 (반올림). */
    public static int toStored(double value) {
        return (int) Math.round(value * SCALE);
    }

    /** 저장용 정수 → double. */
    public static double fromStored(int stored) {
        return stored / (double) SCALE;
    }

    /** 소수점 1자리 숫자 문자열. 예: 35 → "0.4" (반올림), 150 → "1.5". */
    public static String format1(int stored) {
        return String.format("%.1f", fromStored(stored));
    }

    /** 소수점 1자리 % 문자열. 예: 350 → "3.5%". */
    public static String formatPercent1(int stored) {
        return String.format("%.1f%%", fromStored(stored));
    }

    /** 정수 표시 (× 100을 풀되 소수점 없는 정수로 표시). 예: 300 → "3". */
    public static String formatInt(int stored) {
        return String.valueOf(stored / SCALE);
    }
}
