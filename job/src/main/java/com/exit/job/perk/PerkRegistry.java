package com.exit.job.perk;

import com.exit.job.model.JobType;
import com.exit.job.perk.impl.MaxHealthPerk;
import com.exit.job.perk.impl.PermanentPotionPerk;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;

/**
 * (직업, perk id) → PerkApplier 매핑.
 *
 * <p>같은 perk id 가 여러 직업에 걸쳐 있을 때 (예: max_health_1 이 miner/fisher/farmer 모두에)
 * AttributeModifier 의 NamespacedKey 가 충돌하면 누적되지 않으므로,
 * <b>(직업, perk id) 조합마다 별도 PerkApplier + 별도 NamespacedKey</b> 로 등록한다.
 *
 * <p>이벤트 기반 능력 (확률 드롭, 광역 수확, 체장 클램프 등) 은 PerkApplier 가 필요 없어
 * 이 registry 에 등록되지 않고 별도 Listener 가 JobManager.getLevel() 직접 조회.
 */
public class PerkRegistry {

    /** key 포맷: "<jobId>:<perkId>". 예: "miner:max_health_1". */
    private final Map<String, PerkApplier> appliers = new HashMap<>();

    public PerkRegistry(Plugin plugin) {
        // ─── max_health_* ───  +1칸 = 2.0 health points / 1 perk
        // 직업별로 고유 키 할당해서 모디파이어가 누적되도록.
        // miner Lv2 는 mineral_storage (이벤트 기반 — Job 자체에서 처리). max_health_1 등록 안 함.
        // miner Lv6 의 max_health_2 는 2026-05-16 부로 비활성 — 광부 보관함 페이지 확장 perk 로 대체.
        // 어부 Lv6 와 동일 패턴. 추후 다른 레벨로 재배치 시 주석 해제.
        // registerMaxHealth(plugin, JobType.MINER,  "max_health_2");
        // fisher Lv2 는 fish_storage (이벤트 기반 — Fishing 측에서 처리). max_health_1 등록 안 함.
        // fisher Lv6 의 max_health_2 는 2026-05-14 부로 비활성 — 보관함 페이지 확장 perk 로 대체.
        // 추후 다른 레벨로 재배치 시 주석 해제.
        // registerMaxHealth(plugin, JobType.FISHER, "max_health_2");
        // farmer Lv2 는 crop_storage (이벤트 기반 — Farming 측에서 처리). max_health_1 등록 안 함.

        // ─── 영구 PotionEffect ───
        appliers.put(key(JobType.MINER, "lava_immunity"),
                new PermanentPotionPerk(PotionEffectType.FIRE_RESISTANCE));
        appliers.put(key(JobType.FISHER, "water_breathing"),
                new PermanentPotionPerk(PotionEffectType.WATER_BREATHING));

        // ore_drop_x2_chance / ore_to_block_chance / fish_min_length_10 / bite_time_minus_3s
        // / sprinkler_iron / sprinkler_diamond / hoe_harvest_1x2 / hoe_harvest_1x3
        // 위 능력들은 이벤트 기반이라 등록 없음.
    }

    private void registerMaxHealth(Plugin plugin, JobType type, String perkId) {
        NamespacedKey nsKey = new NamespacedKey(plugin, type.id() + "_" + perkId);
        // 2.0 health points = 1 칸 (마크는 1 heart = 2 health points). 사용자 표시는 "+1" 유지.
        appliers.put(key(type, perkId), new MaxHealthPerk(nsKey, 2.0));
    }

    private static String key(JobType type, String perkId) {
        return type.id() + ":" + perkId;
    }

    public PerkApplier get(JobType type, String perkId) {
        return appliers.get(key(type, perkId));
    }
}
