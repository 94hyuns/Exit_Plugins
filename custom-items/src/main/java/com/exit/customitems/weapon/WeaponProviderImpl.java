package com.exit.customitems.weapon;

import com.exit.core.api.WeaponProvider;
import com.exit.customitems.armor.AnubisArmorItem;
import com.exit.customitems.armor.WingedArmorItem;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Core WeaponProvider 구현. CustomItems 의 무기/방어구 ItemStack 을 외부 플러그인에 노출.
 * 지원: FROSTMOURNE, GREATSWORD, DRAGON_WINGS, ANUBIS_HELMET, ANUBIS_LEGGINGS, ANUBIS_BOOTS.
 */
public final class WeaponProviderImpl implements WeaponProvider {

    private final FrostmourneItem frostmourneItem;
    private final GreatSwordItem greatSwordItem;
    private final WingedArmorItem wingedArmorItem;
    private final AnubisArmorItem anubisArmorItem;

    public WeaponProviderImpl(FrostmourneItem frostmourneItem,
                              GreatSwordItem greatSwordItem,
                              WingedArmorItem wingedArmorItem,
                              AnubisArmorItem anubisArmorItem) {
        this.frostmourneItem = frostmourneItem;
        this.greatSwordItem = greatSwordItem;
        this.wingedArmorItem = wingedArmorItem;
        this.anubisArmorItem = anubisArmorItem;
    }

    @Override
    public List<String> listTypes() {
        return List.of(
                FrostmourneItem.TYPE_ID,
                GreatSwordItem.TYPE_ID,
                WingedArmorItem.TYPE_ID,
                AnubisArmorItem.HELMET_TYPE_ID,
                AnubisArmorItem.LEGGINGS_TYPE_ID,
                AnubisArmorItem.BOOTS_TYPE_ID
        );
    }

    @Override
    public ItemStack createWeapon(String typeId, int amount) {
        if (typeId == null) return null;
        if (FrostmourneItem.TYPE_ID.equalsIgnoreCase(typeId)) {
            return frostmourneItem.create(amount);
        }
        if (GreatSwordItem.TYPE_ID.equalsIgnoreCase(typeId)) {
            return greatSwordItem.create(amount);
        }
        if (WingedArmorItem.TYPE_ID.equalsIgnoreCase(typeId)) {
            return wingedArmorItem.create(amount);
        }
        if (AnubisArmorItem.HELMET_TYPE_ID.equalsIgnoreCase(typeId)) {
            return anubisArmorItem.createHelmet(amount);
        }
        if (AnubisArmorItem.LEGGINGS_TYPE_ID.equalsIgnoreCase(typeId)) {
            return anubisArmorItem.createLeggings(amount);
        }
        if (AnubisArmorItem.BOOTS_TYPE_ID.equalsIgnoreCase(typeId)) {
            return anubisArmorItem.createBoots(amount);
        }
        return null;
    }

    @Override
    public String identify(ItemStack item) {
        if (item == null) return null;
        if (frostmourneItem.isFrostmourne(item)) return FrostmourneItem.TYPE_ID;
        if (greatSwordItem.isGreatSword(item)) return GreatSwordItem.TYPE_ID;
        if (wingedArmorItem.isWingedArmor(item)) return WingedArmorItem.TYPE_ID;
        if (anubisArmorItem.isAnubisHelmet(item)) return AnubisArmorItem.HELMET_TYPE_ID;
        if (anubisArmorItem.isAnubisLeggings(item)) return AnubisArmorItem.LEGGINGS_TYPE_ID;
        if (anubisArmorItem.isAnubisBoots(item)) return AnubisArmorItem.BOOTS_TYPE_ID;
        return null;
    }
}
