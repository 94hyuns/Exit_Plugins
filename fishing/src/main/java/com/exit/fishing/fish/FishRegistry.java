package com.exit.fishing.fish;

import com.exit.fishing.season.Season;

import java.util.*;

/**
 * 24어종 카탈로그. CMD 인덱스는 리소스팩의 items/cod.json의 threshold와 일치해야 한다.
 *
 * 월별 제철 분류 (원본 스크립트의 month 주석 기준):
 *   봄 (3,4,5월) : 숭어, 임연수, 참치, 감성돔, 우럭, 농어
 *   여름 (6,7,8월): 장어, 붕어, 전복, 메기, 민어, 잉어
 *   가을 (9,10,11월): 연어, 오징어, 광어, 전어, 참돔, 꽁치
 *   겨울 (12,1,2월): 방어, 홍어, 청어, 대구, 도미, 삼치
 */
public final class FishRegistry {

    private static final List<FishSpecies> ALL = new ArrayList<>();
    private static final Map<String, FishSpecies> BY_ID = new HashMap<>();
    private static final Map<String, FishSpecies> BY_KOREAN = new HashMap<>();
    private static final Map<Integer, FishSpecies> BY_CMD = new HashMap<>();
    private static final EnumMap<Season, List<FishSpecies>> BY_SEASON = new EnumMap<>(Season.class);

    static {
        register("herring",       "청어",     1,  Season.WINTER, "작은 몸집에 긴 등지느러미를 가진 청어는 군집 생활을 하며, 바다를 떠다니며 기름지고 담백한 맛이 특징이다.");
        register("cod",           "대구",     2,  Season.WINTER, "부드럽고 길쭉한 몸에 두꺼운 비늘을 가진 대구는 깊은 바다에서 자주 발견되며, 담백하고 부드러운 맛을 자랑한다.");
        register("seabream",      "도미",     3,  Season.WINTER, "둥글고 두꺼운 몸을 가진 도미는 강한 등지느러미를 지니며, 고급 어류로 유명하고 부드럽고 달콤한 맛이 난다.");
        register("mackerel",      "삼치",     4,  Season.WINTER, "날씬하고 긴 몸체를 가진 삼치는 빠르고 유연하게 수영하며, 따뜻한 바다에서 자주 발견되며 기름지고 고소한 맛이 특징이다.");
        register("mullet",        "숭어",     5,  Season.SPRING, "큰 등지느러미와 강한 체력을 지닌 숭어는 연안에서 자주 발견되며, 단단하고 담백한 맛이 특징인 어류이다.");
        register("yeonsoolim",    "임연수",   6,  Season.SPRING, "길고 튼튼한 몸체를 가진 임연수는 활동적이며, 깊은 바다에서 자주 발견되며 고소하고 부드러운 맛을 지닌다.");
        register("tuna",          "참치",     7,  Season.SPRING, "큰 체구에 날렵한 몸을 가진 참치는 빠른 속도로 바다를 헤엄치며, 기름지고 풍부한 맛이 나며 인기 있는 어류다.");
        register("blackseabream", "감성돔",   8,  Season.SPRING, "둥글고 작은 지느러미를 가진 감성돔은 민첩하게 바다를 헤엄치며, 담백하고 부드러운 맛을 지닌다.");
        register("rockfish",      "우럭",     9,  Season.SPRING, "강한 체력과 큰 입을 가진 우럭은 연안에서 자주 잡히며, 단단하고 담백한 맛이 특징이다.");
        register("bass",          "농어",    10,  Season.SPRING, "길고 탄탄한 몸과 튼튼한 지느러미를 가진 농어는 강한 활동성을 자랑하며, 담백하고 부드러운 맛을 지닌다.");
        register("eel",           "장어",    11,  Season.SUMMER, "부드럽고 유연한 몸을 가진 장어는 뱀처럼 길고 유연하게 이동하며, 고소하고 기름진 맛을 자랑한다.");
        register("cruciancarp",   "붕어",    12,  Season.SUMMER, "작은 몸집에 둥글고 넓은 형태를 가진 붕어는 민물에서 잘 자주 발견되며, 담백하고 부드러운 맛을 지닌다.");
        register("abalone",       "전복",    13,  Season.SUMMER, "평평한 몸과 단단한 껍질을 가진 전복은 바위 틈에서 자주 발견되며, 고소하고 부드러운 맛을 자랑한다.");
        register("catfish",       "메기",    14,  Season.SUMMER, "긴 수염과 길고 무거운 몸체를 가진 메기는 민물에서 자주 발견되며, 기름지고 부드러운 맛을 지닌다.");
        register("croaker",       "민어",    15,  Season.SUMMER, "큰 크기와 유선형의 몸체를 가진 민어는 빠르고 유연하게 헤엄치며, 담백하고 부드러운 맛이 특징이다.");
        register("carp",          "잉어",    16,  Season.SUMMER, "둥글고 넓은 몸을 가진 잉어는 큰 지느러미로 물속을 유영하며, 다양한 환경에서 자주 발견되며 부드러운 맛을 지닌다.");
        register("salmon",        "연어",    17,  Season.AUTUMN, "유선형의 길고 슬림한 몸을 가진 연어는 큰 지느러미로 빠르게 수영하며, 고소하고 기름진 맛이 특징이다.");
        register("calamari",      "오징어",  18,  Season.AUTUMN, "원뿔형 몸체와 여러 개의 팔을 가진 오징어는 바다에서 활발히 움직이며, 부드럽고 고소한 맛을 지닌다.");
        register("flatfish",      "광어",    19,  Season.AUTUMN, "납작한 몸과 양쪽 눈이 한쪽에 모여 있는 광어는 바닥에서 주로 생활하며, 담백하고 부드러운 맛이 특징이다.");
        register("shad",          "전어",    20,  Season.AUTUMN, "작은 몸과 뾰족한 형태를 가진 전어는 빠르게 헤엄쳐 연안에서 자주 보이며, 담백하면서도 고소한 맛을 지닌다.");
        register("redseabream",   "참돔",    21,  Season.AUTUMN, "둥글고 두꺼운 몸을 가진 참돔은 고급스러운 어류로 잘 알려져 있으며, 부드럽고 달콤한 맛이 특징이다.");
        register("saury",         "꽁치",    22,  Season.AUTUMN, "길고 날씬한 몸을 가진 꽁치는 유선형으로 빠르게 헤엄치며, 기름진 맛이 나며 주로 추운 바다에서 자주 발견된다.");
        register("amberjack",     "방어",    23,  Season.WINTER, "큰 몸과 넓은 지느러미를 가진 방어는 빠르게 헤엄쳐 강한 체력을 자랑하며, 기름지고 풍부한 맛이 특징이다.");
        register("skates",        "홍어",    24,  Season.WINTER, "평평하고 넓은 몸에 두꺼운 피부를 가진 홍어는 바닥에서 생활하며, 독특한 향과 기름진 맛을 지닌다.");
    }

    private static void register(String id, String ko, int cmd, Season season, String desc) {
        FishSpecies fs = new FishSpecies(id, ko, cmd, season, desc);
        ALL.add(fs);
        BY_ID.put(id, fs);
        BY_KOREAN.put(ko, fs);
        BY_CMD.put(cmd, fs);
        BY_SEASON.computeIfAbsent(season, k -> new ArrayList<>()).add(fs);
    }

    private FishRegistry() {}

    public static List<FishSpecies> all() { return Collections.unmodifiableList(ALL); }
    public static FishSpecies byId(String id) { return BY_ID.get(id); }
    public static FishSpecies byKorean(String ko) { return BY_KOREAN.get(ko); }
    public static FishSpecies byCmd(int cmd) { return BY_CMD.get(cmd); }

    /** 해당 계절의 제철 어종 6종 */
    public static List<FishSpecies> inSeason(Season s) {
        return BY_SEASON.getOrDefault(s, List.of());
    }

    /** 해당 계절에서 반대 계절 어종 (출현 금지) */
    public static List<FishSpecies> offSeason(Season s) {
        return BY_SEASON.getOrDefault(s.opposite(), List.of());
    }

    /**
     * 해당 계절에 "낚을 수 있는" 어종 목록.
     * = 제철(6) + 중간계절 2개(봄이면 여름+겨울, 총 12) = 18종.
     * 반대 계절 6종은 제외.
     */
    public static List<FishSpecies> catchableIn(Season s) {
        List<FishSpecies> list = new ArrayList<>();
        for (FishSpecies f : ALL) {
            if (f.season() != s.opposite()) list.add(f);
        }
        return list;
    }
}
