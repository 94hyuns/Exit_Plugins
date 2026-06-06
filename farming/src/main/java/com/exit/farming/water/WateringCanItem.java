package com.exit.farming.water;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 물뿌리개 아이템. 베이스: BUCKET. 등급별 CMD + PDC 태그.
 *
 * <p>우클릭으로 우리 farmland(Material.FARMLAND) 를 적신다. 범위는 등급에 따라:
 * <ul>
 *   <li>구리: 1×3 (타겟 + 정면 2칸)</li>
 *   <li>철:   2×3 (정면 3칸 × 좌우 2열)</li>
 *   <li>다이아: 3×3 (정면 3칸 × 좌우 3열, 타겟 정중앙 앞)</li>
 * </ul>
 *
 * <p>리소스팩은 미반영 — CMD 만 예약. 현재 외형은 BUCKET 그대로.
 */
public final class WateringCanItem {

    public static ItemStack create(WaterTier tier) {
        return create(tier, 1);
    }

    public static ItemStack create(WaterTier tier, int amount) {
        ItemStack item = new ItemStack(Material.BUCKET, Math.max(1, Math.min(amount, 16)));
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) tier.wateringCanCmd()));
            meta.setCustomModelDataComponent(cmd);

            meta.displayName(Component.text(tier.displayKor() + " 물뿌리개")
                    .color(tier.color())
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("우클릭으로 경작지를 적십니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("범위: " + rangeLabel(tier), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.getPersistentDataContainer().set(
                    WaterToolKeys.WATERING_CAN_TIER,
                    PersistentDataType.STRING,
                    tier.id()
            );
        });
        return item;
    }

    private static String rangeLabel(WaterTier t) {
        return switch (t) {
            case COPPER  -> "1×3 (정면 3칸)";
            case IRON    -> "2×3 (6칸)";
            case DIAMOND -> "3×3 (9칸)";
        };
    }

    /** 우리 물뿌리개? */
    public static WaterTier getTier(ItemStack item) {
        if (item == null || item.getType() != Material.BUCKET) return null;
        if (!item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(WaterToolKeys.WATERING_CAN_TIER, PersistentDataType.STRING);
        return WaterTier.byId(id);
    }

    private WateringCanItem() {}
}
