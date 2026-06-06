package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.SaturationKeeperEnchant;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.plugin.Plugin;

/**
 * 포만 유지 (SET) 의 즉시 반응 처리.
 *
 * <p>음식 섭취/허기 감소 등 vanilla 허기 변동 시점에 즉시 캡 적용.
 * {@code ArmorTickTask} 의 1초 주기 검사와 보완 관계 — 평상시엔 tick 으로 충분하나
 * 음식 먹는 순간 즉시 캡되어 saturation 도 제거됨.
 */
public class SaturationCapListener implements Listener {

    private final EnchantStorage storage;
    private final EnchantConfig ec;
    private final NamespacedKey kSatKeeper;

    public SaturationCapListener(Plugin plugin, EnchantStorage storage, EnchantConfig ec) {
        this.storage = storage;
        this.ec = ec;
        this.kSatKeeper = SaturationKeeperEnchant.keyOf(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        int level = SetLevelCounter.countOnArmor(p, kSatKeeper, storage);
        if (level <= 0) return;

        // L5: 허기 1칸 (food 2) 고정 — 모든 변화 차단, 항상 food=2 로 덮어씀
        if (level >= 5) {
            if (event.getFoodLevel() != 2) {
                event.setFoodLevel(2);
            }
            return;
        }

        // L1~L4: 캡 초과 시만 클램프
        double base = ec.readDouble("saturation_keeper", "base", 5.0);
        double per = ec.readDouble("saturation_keeper", "per_level", 1.0);
        int capBars = Math.max(0, (int) Math.round(base - per * level));
        int capFood = capBars * 2;

        if (event.getFoodLevel() > capFood) {
            event.setFoodLevel(capFood);
            // saturation 은 별도 이벤트 — tick 에서 처리됨. 여기선 food level 만 클램프.
        }
    }
}
