package com.exit.customitems.lamp.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 방어구 SET 인챈트의 적용 레벨 = 그 인챈트가 박힌 부위 수.
 * 한 부위에 같은 SET 인챈트가 여러 번 박힐 수는 없으므로 각 부위는 0/1 카운트.
 *
 * <p>행운(LuckyEnchant) 시너지: 다른 SET 인챈트가 **최소 1부위에 실제로 박혀 있을 때만**
 * 행운 부위가 그 SET 의 카운트에 +1 추가됨.
 * 같은 부위에 SET + 행운이 동시에 박혀 있다면 setCount + 시너지 둘 다 적용 (+2 효과).
 * 예: 갑옷에 체국+행운(변성), 투구·각반·신발에 체국 → 카운트 = 4(체국) + 1(행운 시너지) = 5.
 *
 * <p>최종 결과는 최대 **5부위 cap** (체국 풀세팅 + 변성 행운 1부위 시 도달).
 */
public final class SetLevelCounter {

    private SetLevelCounter() {}

    /** 행운(Lucky) NamespacedKey. 플러그인 enable 시 한 번 설정. */
    private static NamespacedKey luckyKey;

    public static void setLuckyKey(NamespacedKey key) {
        luckyKey = key;
    }

    public static int countOnArmor(Player player, NamespacedKey enchantKey, EnchantStorage storage) {
        boolean isLuckyItself = luckyKey != null && enchantKey.equals(luckyKey);
        int setCount = 0;
        int luckyExtraCount = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            boolean hasSet = false, hasLucky = false;
            for (RolledEnchant r : storage.load(armor)) {
                NamespacedKey k = r.enchant().getKey();
                if (k.equals(enchantKey)) hasSet = true;
                if (luckyKey != null && k.equals(luckyKey)) hasLucky = true;
            }
            if (hasSet) setCount++;
            if (hasLucky) luckyExtraCount++;  // SET 박힌 부위에도 행운 있으면 둘 다 카운트 (+2)
        }

        int total;
        if (isLuckyItself) {
            // 행운 자체 카운트는 행운 박힌 부위만 (시너지 적용 X)
            total = setCount;
        } else {
            // 다른 SET 시너지: 그 SET 이 1부위 이상 실제로 박혀 있을 때만 행운 부위 추가
            total = (setCount > 0) ? setCount + luckyExtraCount : 0;
        }
        return Math.min(5, total);
    }

    /** 부위에 행운이 박혔는지. (ArmorAttributeManager 의 BASIC 2배 처리에 사용) */
    public static boolean pieceHasLucky(ItemStack armor, EnchantStorage storage) {
        if (luckyKey == null || armor == null) return false;
        for (RolledEnchant r : storage.load(armor)) {
            if (r.enchant().getKey().equals(luckyKey)) return true;
        }
        return false;
    }
}
