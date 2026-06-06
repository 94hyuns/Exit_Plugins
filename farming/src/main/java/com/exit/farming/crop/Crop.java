package com.exit.farming.crop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * 13작물 정의. 각 작물은 seedling → mature 두 단계의 (Material, age) 매핑을 가진다.
 * 원본 스크립트의 바닐라 작물 블록 하이재킹 구조를 그대로 계승.
 *
 *   작물        CMD  Seedling          Mature          Repeatable(우클릭 수확)
 *   ---        ---  ---------         ---------       ---
 *   밀          1    WHEAT[1]          CARROTS[6]      -
 *   감자        2    WHEAT[2]          CARROTS[7]      -
 *   비트        3    WHEAT[3]          POTATOES[1]     -
 *   당근        4    WHEAT[4]          POTATOES[2]     -
 *   포도        5    WHEAT[5]          POTATOES[3]     ✓
 *   블루베리    6    WHEAT[6]          POTATOES[4]     ✓
 *   콜리플라워  7    POTATOES[5]       WHEAT[7]        -
 *   옥수수      8    CARROTS[1]        POTATOES[6]     -
 *   크랜베리    9    CARROTS[2]        POTATOES[7]     ✓
 *   마늘        10   CARROTS[3]        BEETROOTS[1]    -
 *   대파        11   CARROTS[4]        BEETROOTS[2]    -
 *   완두콩      12   CARROTS[5]        BEETROOTS[3]    ✓
 *   토마토      13   CARROTS[0]        POTATOES[0]     ✓
 *
 * 주의: CARROTS[0] = 토마토 seedling이므로 바닐라 당근의 신선 상태와 충돌 → vanilla-suppress로 차단.
 */
public enum Crop {
    WHEAT        ("wheat",         "밀",         1,  Material.WHEAT,     1,  Material.CARROTS,   6, false),
    POTATO       ("potato",        "감자",       2,  Material.WHEAT,     2,  Material.CARROTS,   7, false),
    BEETROOT     ("beetroot",      "비트",       3,  Material.WHEAT,     3,  Material.POTATOES,  1, false),
    CARROT       ("carrot",        "당근",       4,  Material.WHEAT,     4,  Material.POTATOES,  2, false),
    GRAPE        ("grape",         "포도",       5,  Material.WHEAT,     5,  Material.POTATOES,  3, true),
    BLUEBERRY    ("blueberry",     "블루베리",   6,  Material.WHEAT,     6,  Material.POTATOES,  4, true),
    CAULIFLOWER  ("cauliflower",   "콜리플라워", 7,  Material.POTATOES,  5,  Material.WHEAT,     7, false),
    CORN         ("corn",          "옥수수",     8,  Material.CARROTS,   1,  Material.POTATOES,  6, false),
    CRANBERRY    ("cranberry",     "크랜베리",   9,  Material.CARROTS,   2,  Material.POTATOES,  7, true),
    GARLIC       ("garlic",        "마늘",      10,  Material.CARROTS,   3,  Material.BEETROOTS, 1, false),
    LEEK         ("leek",          "대파",      11,  Material.CARROTS,   4,  Material.BEETROOTS, 2, false),
    PEA          ("pea",           "완두콩",    12,  Material.CARROTS,   5,  Material.BEETROOTS, 3, true),
    TOMATO       ("tomato",        "토마토",    13,  Material.CARROTS,   0,  Material.POTATOES,  0, true);

    private final String id;
    private final String koreanName;
    private final int customModelData;
    private final Material seedlingMaterial;
    private final int seedlingAge;
    private final Material matureMaterial;
    private final int matureAge;
    private final boolean repeatable;

    Crop(String id, String koreanName, int cmd,
         Material sMat, int sAge, Material mMat, int mAge, boolean repeatable) {
        this.id = id;
        this.koreanName = koreanName;
        this.customModelData = cmd;
        this.seedlingMaterial = sMat;
        this.seedlingAge = sAge;
        this.matureMaterial = mMat;
        this.matureAge = mAge;
        this.repeatable = repeatable;
    }

    public String id() { return id; }
    public String koreanName() { return koreanName; }
    public int customModelData() { return customModelData; }
    public Material seedlingMaterial() { return seedlingMaterial; }
    public int seedlingAge() { return seedlingAge; }
    public Material matureMaterial() { return matureMaterial; }
    public int matureAge() { return matureAge; }
    public boolean repeatable() { return repeatable; }

    // ==== 블록 상태 조회/설정 ====

    public boolean matchesSeedling(Block block) {
        if (block.getType() != seedlingMaterial) return false;
        BlockData data = block.getBlockData();
        return data instanceof Ageable ageable && ageable.getAge() == seedlingAge;
    }

    public boolean matchesMature(Block block) {
        if (block.getType() != matureMaterial) return false;
        BlockData data = block.getBlockData();
        return data instanceof Ageable ageable && ageable.getAge() == matureAge;
    }

    public void applySeedling(Block block) {
        apply(block, seedlingMaterial, seedlingAge);
    }

    public void applyMature(Block block) {
        apply(block, matureMaterial, matureAge);
    }

    private static void apply(Block block, Material mat, int age) {
        block.setType(mat, false);
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(age);
            block.setBlockData(ageable, false);
        }
    }

    // ==== 전역 조회 ====

    /** 모든 작물 seedling 매핑 (Material, age) → Crop */
    private static final Map<Material, Map<Integer, Crop>> SEEDLING_INDEX = new EnumMap<>(Material.class);
    /** 모든 작물 mature 매핑 */
    private static final Map<Material, Map<Integer, Crop>> MATURE_INDEX = new EnumMap<>(Material.class);
    private static final Map<String, Crop> BY_ID = new HashMap<>();
    private static final Map<String, Crop> BY_KOREAN = new HashMap<>();

    static {
        for (Crop c : values()) {
            SEEDLING_INDEX.computeIfAbsent(c.seedlingMaterial, k -> new HashMap<>())
                    .put(c.seedlingAge, c);
            MATURE_INDEX.computeIfAbsent(c.matureMaterial, k -> new HashMap<>())
                    .put(c.matureAge, c);
            BY_ID.put(c.id, c);
            BY_KOREAN.put(c.koreanName, c);
        }
    }

    public static Crop matchingSeedling(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return null;
        var map = SEEDLING_INDEX.get(block.getType());
        return map == null ? null : map.get(ageable.getAge());
    }

    public static Crop matchingMature(Block block) {
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) return null;
        var map = MATURE_INDEX.get(block.getType());
        return map == null ? null : map.get(ageable.getAge());
    }

    public static Crop byId(String id) { return BY_ID.get(id); }
    public static Crop byKorean(String ko) { return BY_KOREAN.get(ko); }

    public static boolean isCropMaterial(Material m) {
        return m == Material.WHEAT || m == Material.CARROTS
                || m == Material.POTATOES || m == Material.BEETROOTS;
    }
}
