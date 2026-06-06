package com.exit.farming.water;

import com.exit.core.api.WaterToolProvider;
import org.bukkit.inventory.ItemStack;

/**
 * {@link WaterToolProvider} 구현체. {@link WateringCanItem}/{@link SprinklerItem} 를 typeId 로 라우팅.
 *
 * 지원 typeId:
 * <ul>
 *   <li>wateringcan_copper / wateringcan_iron / wateringcan_diamond</li>
 *   <li>sprinkler_copper / sprinkler_iron / sprinkler_diamond</li>
 * </ul>
 */
public class WaterToolProviderImpl implements WaterToolProvider {

    @Override
    public ItemStack createTool(String typeId, int amount) {
        if (typeId == null) return null;
        String t = typeId.toLowerCase();
        WaterTier tier = parseTier(t);
        if (tier == null) return null;
        if (t.startsWith("wateringcan_")) return WateringCanItem.create(tier, amount);
        if (t.startsWith("sprinkler_"))   return SprinklerItem.create(tier, amount);
        return null;
    }

    private static WaterTier parseTier(String t) {
        if (t.endsWith("_copper"))  return WaterTier.COPPER;
        if (t.endsWith("_iron"))    return WaterTier.IRON;
        if (t.endsWith("_diamond")) return WaterTier.DIAMOND;
        return null;
    }

    @Override
    public boolean isWateringCan(ItemStack item) {
        return WateringCanItem.getTier(item) != null;
    }

    @Override
    public boolean isSprinkler(ItemStack item) {
        return SprinklerItem.getTier(item) != null;
    }
}
