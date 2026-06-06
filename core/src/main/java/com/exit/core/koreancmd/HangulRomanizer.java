package com.exit.core.koreancmd;

import java.util.HashMap;
import java.util.Map;

/**
 * 한글 음절 → 두벌식 키보드 romanization 변환.
 *
 * <p>예: "스킬정보" → "tmzlfwjdqh"
 *
 * <p>각 한글 음절을 초성/중성/종성으로 분해 후 표준 두벌식 매핑에 따라 영문자로 변환.
 * 복합 자모(예: ㅘ=ㅗ+ㅏ → "hk", ㄺ=ㄹ+ㄱ → "fr")는 다중 문자로 풀어쓴다.
 *
 * <p>비-한글 문자(영숫자/공백/기호)는 그대로 유지.
 */
public final class HangulRomanizer {

    // 초성 19개
    private static final String[] INITIAL = {
        "r","R","s","e","E","f","a","q","Q","t",
        "T","d","w","W","c","z","x","v","g"
    };

    // 중성 21개 (복합 모음은 다중 문자)
    private static final String[] MEDIAL = {
        "k","o","i","O","j","p","u","P",
        "h","hk","ho","hl","y",
        "n","nj","np","nl","b","m","ml","l"
    };

    // 종성 28개 (인덱스 0 = 종성 없음)
    private static final String[] FINAL = {
        "",  "r","R","rt","s","sw","sg","e","f",
        "fr","fa","fq","ft","fx","fv","fg",
        "a","q","qt","t","T","d","w","c","z","x","v","g"
    };

    private static final int SYLLABLE_BASE = 0xAC00;
    private static final int SYLLABLE_END  = 0xD7A3;
    private static final int MEDIAL_COUNT  = 21;
    private static final int FINAL_COUNT   = 28;

    private HangulRomanizer() {}

    /**
     * 입력 문자열의 한글 음절을 두벌식 romanization 으로 변환.
     * 한글이 아닌 문자는 그대로 유지.
     */
    public static String toEng(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder sb = new StringBuilder(input.length() * 2);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= SYLLABLE_BASE && c <= SYLLABLE_END) {
                int idx = c - SYLLABLE_BASE;
                int initialIdx = idx / (MEDIAL_COUNT * FINAL_COUNT);
                int medialIdx  = (idx / FINAL_COUNT) % MEDIAL_COUNT;
                int finalIdx   = idx % FINAL_COUNT;
                sb.append(INITIAL[initialIdx]);
                sb.append(MEDIAL[medialIdx]);
                sb.append(FINAL[finalIdx]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 입력에 한글 음절이 하나라도 포함되어 있는지. */
    public static boolean containsHangul(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= SYLLABLE_BASE && c <= SYLLABLE_END) return true;
        }
        return false;
    }

    // ─── 역방향 변환: 두벌식 영문 → 한글 ───

    private static final Map<String, Integer> INITIAL_MAP = new HashMap<>();
    private static final Map<String, Integer> MEDIAL_MAP = new HashMap<>();
    private static final Map<String, Integer> FINAL_MAP = new HashMap<>();
    static {
        for (int i = 0; i < INITIAL.length; i++) INITIAL_MAP.put(INITIAL[i], i);
        for (int i = 0; i < MEDIAL.length; i++)  MEDIAL_MAP.put(MEDIAL[i], i);
        for (int i = 1; i < FINAL.length; i++)   FINAL_MAP.put(FINAL[i], i);  // index 0 (no final) skipped
    }

    /**
     * 두벌식 영문 입력을 한글 음절로 역변환. 한글로 변환 불가능한 부분(공백, 기호, 영숫자)은 그대로 유지.
     *
     * <p>예: {@code "tmzlfwjdqh"} → {@code "스킬정보"}.
     *
     * <p>알고리즘: 위치마다 longest-match (초성 → 중성 → 종성) 음절 형성 시도.
     * 종성 채택 시 다음 글자가 모음(중성)이면 종성을 비워서 다음 음절의 초성으로 양보 (한글 결합 규칙).
     */
    public static String engToHangul(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            int consumed = trySyllable(input, i, out);
            if (consumed > 0) {
                i += consumed;
            } else {
                // 음절 형성 실패 — 한 글자 그대로 통과
                out.append(input.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** pos 부터 한 음절 시도. 성공 시 음절 추가하고 소비한 char 수 반환, 실패 시 0. */
    private static int trySyllable(String s, int pos, StringBuilder out) {
        int[] init = matchLongest(s, pos, INITIAL_MAP);
        if (init == null) return 0;
        int initIdx = init[0], initLen = init[1];

        int[] med = matchLongest(s, pos + initLen, MEDIAL_MAP);
        if (med == null) return 0;
        int medIdx = med[0], medLen = med[1];

        int afterMed = pos + initLen + medLen;

        // 종성 시도: 가장 긴 것부터 (2 → 1 → 0). "0" 은 항상 성공.
        for (int finCheck = 2; finCheck >= 0; finCheck--) {
            int finIdx, finLen;
            if (finCheck == 0) {
                finIdx = 0;
                finLen = 0;
            } else {
                if (afterMed + finCheck > s.length()) continue;
                String key = s.substring(afterMed, afterMed + finCheck);
                Integer fi = FINAL_MAP.get(key);
                if (fi == null) continue;
                finIdx = fi;
                finLen = finCheck;
            }

            int afterFinal = afterMed + finLen;

            // 종성 다음 글자가 모음이면 (자음 없이 모음 시작은 한글 규칙 위반) →
            // 현재 종성을 다음 음절 초성으로 양보. 더 짧은 종성으로 재시도.
            if (finLen > 0 && afterFinal < s.length()) {
                int[] peekInit = matchLongest(s, afterFinal, INITIAL_MAP);
                int[] peekMed = matchLongest(s, afterFinal, MEDIAL_MAP);
                if (peekInit == null && peekMed != null) {
                    continue;
                }
            }

            int code = SYLLABLE_BASE + initIdx * 588 + medIdx * 28 + finIdx;
            out.append((char) code);
            return initLen + medLen + finLen;
        }
        return 0;
    }

    /** pos 부터 map 의 키 중 가장 긴 매칭. {idx, len} 반환, 매칭 실패 시 null. */
    private static int[] matchLongest(String s, int pos, Map<String, Integer> map) {
        if (pos + 2 <= s.length()) {
            Integer v = map.get(s.substring(pos, pos + 2));
            if (v != null) return new int[]{v, 2};
        }
        if (pos + 1 <= s.length()) {
            Integer v = map.get(s.substring(pos, pos + 1));
            if (v != null) return new int[]{v, 1};
        }
        return null;
    }
}
