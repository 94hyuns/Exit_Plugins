package com.exit.farming.cooking;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cooking Pack 28종 요리에 테마별 포션효과 부여.
 *
 * <p>cooking pack item 은 모두 vanilla cooked_beef / milk_bucket 베이스 + CustomModelData
 * 1~26 (or 1~2 for milk) 로 구분. MM API 사용하지 않고 (Material, CMD) 직접 매칭.
 *
 * <p>테마 매핑:
 * <ul>
 *   <li>고기류 → Strength (전투)</li>
 *   <li>수산물 → Water Breathing + Strength</li>
 *   <li>베리/과일 → Haste (채굴/수확)</li>
 *   <li>빵/디저트 → Speed (이동)</li>
 *   <li>사탕류 → 특수 (Luck, Jump Boost)</li>
 *   <li>실패 음식 → 페널티 (Nausea, Hunger)</li>
 * </ul>
 */
public final class CookingFoodEffectsListener implements Listener {

    /**
     * 모든 정상 요리에 공통 부여되는 baseline.
     * 황금사과 (Regen II 5s + Absorption I 120s) 대비 약 +20% 효율.
     * 페널티 요리 (failed_food) 는 PENALTY_KEYS 에서 baseline 제외.
     */
    private static final List<PotionEffect> BASELINE = List.of(
            new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true, true),   // 6s Regen II (golden +20%)
            new PotionEffect(PotionEffectType.ABSORPTION,   2880, 0, false, true, true)   // 144s Abs I (golden +20%)
    );

    /** baseline 부여하지 않을 키 (페널티 요리). */
    private static final Set<MatKey> PENALTY_KEYS = Set.of(
            new MatKey(Material.COOKED_BEEF, 1)  // failed_food
    );

    /** (Material, CMD) → 테마 effects. cooking_pack.yml 의 Id/Model 과 일치. */
    private static final Map<MatKey, List<PotionEffect>> EFFECTS = new HashMap<>();
    static {
        // ── cooked_beef base (Model 1~26) ──
        put(Material.COOKED_BEEF, 1,  e(PotionEffectType.NAUSEA, 200, 0), e(PotionEffectType.HUNGER, 600, 0));                   // failed_food
        put(Material.COOKED_BEEF, 2,  e(PotionEffectType.STRENGTH, 600, 0));                                                      // glazed_ham
        put(Material.COOKED_BEEF, 3,  e(PotionEffectType.STRENGTH, 1200, 0));                                                     // beef_kebab
        put(Material.COOKED_BEEF, 4,  e(PotionEffectType.STRENGTH, 600, 0), e(PotionEffectType.SPEED, 600, 0));                  // chicken_kebab
        put(Material.COOKED_BEEF, 5,  e(PotionEffectType.REGENERATION, 200, 0), e(PotionEffectType.SATURATION, 100, 0));         // fruit_salad
        put(Material.COOKED_BEEF, 6,  e(PotionEffectType.WATER_BREATHING, 1200, 0), e(PotionEffectType.STRENGTH, 600, 0));       // cod_sushi
        put(Material.COOKED_BEEF, 7,  e(PotionEffectType.WATER_BREATHING, 1800, 0), e(PotionEffectType.DOLPHINS_GRACE, 600, 0)); // salmon_sushi
        put(Material.COOKED_BEEF, 8,  e(PotionEffectType.HASTE, 1200, 0));                                                        // apple_turnover
        put(Material.COOKED_BEEF, 9,  e(PotionEffectType.STRENGTH, 600, 1));                                                      // porkchop_applesauce
        put(Material.COOKED_BEEF, 10, e(PotionEffectType.HASTE, 600, 1));                                                         // candy_apple
        put(Material.COOKED_BEEF, 11, e(PotionEffectType.SPEED, 600, 1));                                                         // brownie
        put(Material.COOKED_BEEF, 12, e(PotionEffectType.WATER_BREATHING, 1200, 0));                                              // cod_sandwich
        put(Material.COOKED_BEEF, 13, e(PotionEffectType.WATER_BREATHING, 2400, 0));                                              // salmon_sandwich
        put(Material.COOKED_BEEF, 14, e(PotionEffectType.STRENGTH, 900, 1));                                                      // beef_sandwich (Burger)
        put(Material.COOKED_BEEF, 15, e(PotionEffectType.STRENGTH, 1200, 0));                                                     // chicken_sandwich
        put(Material.COOKED_BEEF, 16, e(PotionEffectType.REGENERATION, 100, 0), e(PotionEffectType.SATURATION, 100, 0));         // egg_basket
        put(Material.COOKED_BEEF, 17, e(PotionEffectType.STRENGTH, 1200, 0));                                                     // ham_sandwich
        put(Material.COOKED_BEEF, 18, e(PotionEffectType.SPEED, 1200, 0), e(PotionEffectType.JUMP_BOOST, 1200, 0));               // donut
        put(Material.COOKED_BEEF, 19, e(PotionEffectType.SPEED, 1800, 0));                                                        // pancakes
        put(Material.COOKED_BEEF, 20, e(PotionEffectType.SPEED, 1200, 0), e(PotionEffectType.HASTE, 600, 0));                     // chocolate_bar
        put(Material.COOKED_BEEF, 21, e(PotionEffectType.CONDUIT_POWER, 600, 0), e(PotionEffectType.SPEED, 600, 0));              // fish_chips
        put(Material.COOKED_BEEF, 22, e(PotionEffectType.STRENGTH, 1200, 0), e(PotionEffectType.RESISTANCE, 400, 0));             // milk_steak
        put(Material.COOKED_BEEF, 23, e(PotionEffectType.STRENGTH, 1800, 0));                                                     // steak_eggs
        put(Material.COOKED_BEEF, 24, e(PotionEffectType.LUCK, 3600, 0));                                                         // lollipop
        put(Material.COOKED_BEEF, 25, e(PotionEffectType.JUMP_BOOST, 1200, 1));                                                   // cotton_candy
        put(Material.COOKED_BEEF, 26, e(PotionEffectType.STRENGTH, 1200, 0), e(PotionEffectType.SATURATION, 100, 0));             // bacon_eggs

        // ── milk_bucket base (Model 1~2) ──
        put(Material.MILK_BUCKET, 1, e(PotionEffectType.SPEED, 600, 1), e(PotionEffectType.NIGHT_VISION, 2400, 0));               // chocolate_milk
        put(Material.MILK_BUCKET, 2, e(PotionEffectType.HASTE, 1800, 0), e(PotionEffectType.SPEED, 600, 0));                      // berry_milk
    }

    private final JavaPlugin plugin;

    public CookingFoodEffectsListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent ev) {
        ItemStack stack = ev.getItem();
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        Integer cmd = extractCustomModelData(meta);
        if (cmd == null) return;
        MatKey key = new MatKey(stack.getType(), cmd);
        List<PotionEffect> themeEffects = EFFECTS.get(key);
        if (themeEffects == null) return;  // cooking pack 음식 아님 → 무시

        Player p = ev.getPlayer();

        // 테마 효과 적용
        for (PotionEffect pe : themeEffects) {
            p.addPotionEffect(new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(),
                    false, true, true));
        }

        // baseline 적용 (페널티 요리는 제외 — failed_food 등)
        if (!PENALTY_KEYS.contains(key)) {
            for (PotionEffect pe : BASELINE) {
                p.addPotionEffect(new PotionEffect(pe.getType(), pe.getDuration(), pe.getAmplifier(),
                        false, true, true));
            }
        }
    }

    /** 1.21.4+ CustomModelDataComponent 우선, 옛 getCustomModelData() fallback. 둘 다 없으면 null. */
    private static Integer extractCustomModelData(ItemMeta meta) {
        try {
            var cmdc = meta.getCustomModelDataComponent();
            if (cmdc != null && !cmdc.getFloats().isEmpty()) {
                return (int) cmdc.getFloats().get(0).floatValue();
            }
        } catch (Throwable ignored) { }
        try {
            if (meta.hasCustomModelData()) return meta.getCustomModelData();
        } catch (Throwable ignored) { }
        return null;
    }

    private static void put(Material mat, int cmd, PotionEffect... effects) {
        EFFECTS.put(new MatKey(mat, cmd), List.of(effects));
    }

    private static PotionEffect e(PotionEffectType type, int durationTicks, int amplifier) {
        return new PotionEffect(type, durationTicks, amplifier, false, true, true);
    }

    private record MatKey(Material material, int cmd) {}
}
