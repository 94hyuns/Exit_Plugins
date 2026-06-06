package com.exit.customitems.consumable;

import com.exit.core.api.CustomConsumableProvider;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Core CustomConsumableProvider 구현. typeId로 분기해 각 아이템 빌더에 위임.
 * 등록 가능한 typeId 추가 시 switch에 case만 늘리면 됨.
 */
public final class CustomConsumableProviderImpl implements CustomConsumableProvider {

    private final InvSaveItem invSave;
    private final BigMacItem bigMac;

    public CustomConsumableProviderImpl(InvSaveItem invSave, BigMacItem bigMac) {
        this.invSave = invSave;
        this.bigMac = bigMac;
    }

    @Override
    public ItemStack createConsumable(String typeId, int amount) {
        if (typeId == null) return null;
        return switch (typeId.toUpperCase(Locale.ROOT)) {
            case InvSaveItem.TYPE_ID -> invSave.create(amount);
            case BigMacItem.TYPE_ID  -> bigMac.create(amount);
            default -> null;
        };
    }

    @Override
    public String identifyConsumable(ItemStack item) {
        if (invSave.isInvSave(item)) return InvSaveItem.TYPE_ID;
        if (bigMac.isBigMac(item))   return BigMacItem.TYPE_ID;
        return null;
    }
}
