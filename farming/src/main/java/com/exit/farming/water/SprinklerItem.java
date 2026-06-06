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
 * 스프링쿨러 아이템 (배치 전 — held form). 베이스: BARREL.
 *
 * <p>플레이어가 들고 우클릭 배치 → BlockPlaceEvent 에서 우리 BARREL 인지 확인하고
 * sprinklers.yml 에 위치+등급 등록. 이후 SprinklerTicker 가 주기적으로 주변 farmland 적심.
 *
 * <p>등급별 작동 범위:
 * <ul>
 *   <li>구리: 2×2</li>
 *   <li>철:   3×3</li>
 *   <li>다이아: 5×5</li>
 * </ul>
 */
public final class SprinklerItem {

    public static ItemStack create(WaterTier tier) {
        return create(tier, 1);
    }

    public static ItemStack create(WaterTier tier, int amount) {
        ItemStack item = new ItemStack(Material.BARREL, Math.max(1, Math.min(amount, 16)));
        item.editMeta(meta -> {
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(List.of((float) tier.sprinklerCmd()));
            meta.setCustomModelDataComponent(cmd);

            meta.displayName(Component.text(tier.displayKor() + " 스프링쿨러")
                    .color(tier.color())
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("배치하면 주기적으로 주변 경작지를 적십니다", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.empty(),
                    Component.text("범위: " + rangeLabel(tier), NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("부수면 회수 가능", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            meta.getPersistentDataContainer().set(
                    WaterToolKeys.SPRINKLER_TIER,
                    PersistentDataType.STRING,
                    tier.id()
            );
        });
        return item;
    }

    private static String rangeLabel(WaterTier t) {
        return switch (t) {
            case COPPER  -> "2×2";
            case IRON    -> "3×3";
            case DIAMOND -> "5×5";
        };
    }

    /** 우리 스프링쿨러 아이템? */
    public static WaterTier getTier(ItemStack item) {
        if (item == null || item.getType() != Material.BARREL) return null;
        if (!item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(WaterToolKeys.SPRINKLER_TIER, PersistentDataType.STRING);
        return WaterTier.byId(id);
    }

    private SprinklerItem() {}
}
