package com.exit.farming.water;

import com.exit.farming.FarmingPlugin;
import org.bukkit.NamespacedKey;

/**
 * 물 도구 식별용 NamespacedKey 모음.
 *
 * <ul>
 *   <li>{@link #WATERING_CAN_TIER}: 물뿌리개 ItemStack 의 PDC 에 저장되는 등급(STRING).</li>
 *   <li>{@link #SPRINKLER_TIER}: 스프링쿨러 아이템 ItemStack 의 PDC 에 저장되는 등급.</li>
 *   <li>스프링쿨러 블록은 별도 YAML(sprinklers.yml) 로 위치+등급을 추적.</li>
 * </ul>
 */
public final class WaterToolKeys {

    public static final NamespacedKey WATERING_CAN_TIER;
    public static final NamespacedKey SPRINKLER_TIER;
    /** ItemDisplay 엔티티(배치된 스프링클러 외형) 식별용 마커. value=tier id. */
    public static final NamespacedKey SPRINKLER_DISPLAY;
    /** Interaction 엔티티(클릭 핸들러) 식별용 마커. value=tier id. */
    public static final NamespacedKey SPRINKLER_INTERACTION;

    static {
        FarmingPlugin plugin = FarmingPlugin.getInstance();
        WATERING_CAN_TIER      = new NamespacedKey(plugin, "watering_can_tier");
        SPRINKLER_TIER         = new NamespacedKey(plugin, "sprinkler_tier");
        SPRINKLER_DISPLAY      = new NamespacedKey(plugin, "sprinkler_display");
        SPRINKLER_INTERACTION  = new NamespacedKey(plugin, "sprinkler_interaction");
    }

    private WaterToolKeys() {}
}
