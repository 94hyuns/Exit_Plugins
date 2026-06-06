package com.exit.job.perk.impl;

import com.exit.job.perk.PerkApplier;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.ArrayList;

/**
 * MAX_HEALTH 에 영구 +1 모디파이어 부착. NamespacedKey 로 멱등 처리.
 * 같은 직업의 max_health_1, max_health_2 는 서로 다른 key 로 인스턴스화하여 누적.
 */
public class MaxHealthPerk implements PerkApplier {
    private final NamespacedKey key;
    private final double amount;

    public MaxHealthPerk(NamespacedKey key, double amount) {
        this.key = key;
        this.amount = amount;
    }

    @Override
    public void apply(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeOurs(attr);
        attr.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public void remove(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        removeOurs(attr);
    }

    private void removeOurs(AttributeInstance attr) {
        // ConcurrentModification 회피용 복사
        for (AttributeModifier m : new ArrayList<>(attr.getModifiers())) {
            if (key.equals(m.getKey())) attr.removeModifier(m);
        }
    }
}
