package com.exit.customitems.lamp;

import com.exit.core.api.LampInfo;
import com.exit.core.api.LampProvider;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Core의 {@link LampProvider} 구현체.
 * Shop 등 외부 플러그인이 Core의 ServiceRegistry를 통해 이 구현체에 접근.
 * CustomItems의 내부 타입({@link LampType}, {@link LampItem})을 외부에 노출하지 않고
 * 문자열 typeId 기반으로만 통신하여 결합도를 낮춘다.
 */
public class LampProviderImpl implements LampProvider {

    private final LampItem lampItem;
    private final List<LampInfo> catalog;

    public LampProviderImpl(LampItem lampItem) {
        this.lampItem = lampItem;

        List<LampInfo> list = new ArrayList<>();
        for (LampType type : LampType.values()) {
            list.add(new LampInfo(
                type.name(),              // "LIFE" / "COMBAT"
                type.getDisplayName(),    // "생활 램프" / "전투 램프"
                type.getDescription()
            ));
        }
        this.catalog = Collections.unmodifiableList(list);
    }

    @Override
    public List<LampInfo> listTypes() {
        return catalog;
    }

    @Override
    public ItemStack createLamp(String typeId, int amount) {
        LampType type = LampType.fromString(typeId);
        if (type == null) return null;
        if (amount <= 0) amount = 1;
        if (amount > 64) amount = 64;
        return lampItem.create(type, amount);
    }

    @Override
    public String identify(ItemStack item) {
        LampType type = lampItem.getTypeOf(item);
        return type == null ? null : type.name();
    }
}
