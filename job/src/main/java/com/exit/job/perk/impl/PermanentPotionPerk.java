package com.exit.job.perk.impl;

import com.exit.job.perk.PerkApplier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 영구 PotionEffect 능력 (FIRE_RESISTANCE = 용암 면역, WATER_BREATHING = 수중 호흡 등).
 *
 * <p>duration = -1 → 무한 (Paper 1.21+ 지원). ambient/particles/icon 모두 false 로 깔끔.
 */
public class PermanentPotionPerk implements PerkApplier {
    private final PotionEffectType type;

    public PermanentPotionPerk(PotionEffectType type) {
        this.type = type;
    }

    @Override
    public void apply(Player player) {
        player.addPotionEffect(new PotionEffect(type, -1, 0, false, false, false));
    }

    @Override
    public void remove(Player player) {
        player.removePotionEffect(type);
    }
}
