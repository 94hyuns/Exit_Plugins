package com.exit.shop.util;

import com.exit.core.api.CropItemProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * ShopItem 기준 인벤토리 매칭/카운팅/제거 유틸.
 *
 * - Farming provider 아이템 (작물 열매): PDC 태그의 cropId 로 매칭. typeId 가 cropId.
 *   경작지 블록(typeId=FARMLAND_BLOCK)은 판매 대상이 아니므로 여기서 처리 안 함.
 * - 바닐라 아이템: Material 기반 매칭. 단, 커스텀 PDC 태그 붙은 것(작물/씨앗)은 제외.
 * - 그 외 커스텀 provider(Lamp 등)는 sellable=false 이므로 판매 경로로 오지 않음.
 *
 * <p>category 를 받는 overload 는 인벤토리 + 직업 보관함 (광부/농부) 합산으로 동작.
 * 보관함이 있는 카테고리: MINERAL(광부) / CROP(농부). 그 외엔 인벤토리만.
 */
public final class InventoryMatcher {

    private InventoryMatcher() {}

    /**
     * 플레이어 인벤토리에서 shopItem에 해당하는 아이템 개수 합산.
     */
    public static int count(Player player, ShopItem shopItem) {
        if (isFarmingFruit(shopItem)) {
            CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
            if (crops == null) return 0;
            int count = 0;
            for (ItemStack s : player.getInventory().getContents()) {
                if (s != null && shopItem.getTypeId().equals(crops.identifyFruit(s))) {
                    count += s.getAmount();
                }
            }
            return count;
        }

        // 바닐라 경로
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        int count = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s == null || s.getType() != shopItem.getMaterial()) continue;
            if (crops != null && (crops.identifyFruit(s) != null || crops.identifySeed(s) != null)) {
                continue;  // 커스텀 작물/씨앗은 바닐라 카운트에서 제외
            }
            count += s.getAmount();
        }
        return count;
    }

    /**
     * 플레이어 인벤토리에서 shopItem에 해당하는 아이템을 amount만큼 제거.
     * 호출 전에 count() 로 충분한지 확인 필요.
     */
    public static void remove(Player player, ShopItem shopItem, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        boolean farmingFruit = isFarmingFruit(shopItem);

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s == null) continue;
            boolean match;
            if (farmingFruit) {
                match = crops != null && shopItem.getTypeId().equals(crops.identifyFruit(s));
            } else {
                if (s.getType() != shopItem.getMaterial()) continue;
                if (crops != null && (crops.identifyFruit(s) != null || crops.identifySeed(s) != null)) continue;
                match = true;
            }
            if (!match) continue;

            int take = Math.min(s.getAmount(), remaining);
            if (take >= s.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
            }
            remaining -= take;
        }
    }

    private static boolean isFarmingFruit(ShopItem shopItem) {
        return "Farming".equals(shopItem.getProvider())
                && "FRUIT".equals(shopItem.getVariant());
    }

    // ─── 보관함 통합 (인벤 + 광부/농부 보관함) ───

    /** 인벤토리 + 해당 카테고리 보관함 합산 카운트. 보관함 없는 카테고리는 인벤만. */
    public static int count(Player player, ShopItem shopItem, ShopCategory category) {
        return count(player, shopItem) + countInStorage(player.getUniqueId(), shopItem, category);
    }

    /**
     * amount 만큼 제거. 인벤 먼저 소진 후 부족분은 보관함에서.
     * 호출 전 {@link #count(Player, ShopItem, ShopCategory)} 로 충분한지 확인 필요.
     */
    public static void remove(Player player, ShopItem shopItem, int amount, ShopCategory category) {
        int invHas = count(player, shopItem);
        int fromInv = Math.min(invHas, amount);
        if (fromInv > 0) remove(player, shopItem, fromInv);
        int remaining = amount - fromInv;
        if (remaining > 0) removeFromStorage(player.getUniqueId(), shopItem, remaining, category);
    }

    private static ItemStack[] loadStorage(UUID uuid, ShopCategory category) {
        try {
            if (category == ShopCategory.MINERAL) {
                if (Bukkit.getPluginManager().getPlugin("Job") == null) return null;
                var jobPlugin = com.exit.job.JobPlugin.getInstance();
                if (jobPlugin == null) return null;
                return jobPlugin.getMineralStorageManager().load(uuid);
            } else if (category == ShopCategory.CROP) {
                if (Bukkit.getPluginManager().getPlugin("Farming") == null) return null;
                var farmingPlugin = com.exit.farming.FarmingPlugin.getInstance();
                if (farmingPlugin == null) return null;
                return farmingPlugin.getCropStorageManager().load(uuid);
            }
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
        return null;
    }

    private static void saveStorage(UUID uuid, ShopCategory category, ItemStack[] slots) {
        try {
            if (category == ShopCategory.MINERAL) {
                var jobPlugin = com.exit.job.JobPlugin.getInstance();
                if (jobPlugin == null) return;
                jobPlugin.getMineralStorageManager().save(uuid, slots);
            } else if (category == ShopCategory.CROP) {
                var farmingPlugin = com.exit.farming.FarmingPlugin.getInstance();
                if (farmingPlugin == null) return;
                farmingPlugin.getCropStorageManager().save(uuid, slots);
            }
        } catch (NoClassDefFoundError ignored) {
        }
    }

    private static int countInStorage(UUID uuid, ShopItem shopItem, ShopCategory category) {
        ItemStack[] slots = loadStorage(uuid, category);
        if (slots == null) return 0;
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        boolean farmingFruit = isFarmingFruit(shopItem);
        int count = 0;
        for (ItemStack s : slots) {
            if (s == null) continue;
            if (matches(s, shopItem, crops, farmingFruit)) {
                count += s.getAmount();
            }
        }
        return count;
    }

    private static void removeFromStorage(UUID uuid, ShopItem shopItem, int amount, ShopCategory category) {
        ItemStack[] slots = loadStorage(uuid, category);
        if (slots == null) return;
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        boolean farmingFruit = isFarmingFruit(shopItem);
        int remaining = amount;
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack s = slots[i];
            if (s == null) continue;
            if (!matches(s, shopItem, crops, farmingFruit)) continue;
            int take = Math.min(s.getAmount(), remaining);
            if (take >= s.getAmount()) {
                slots[i] = null;
            } else {
                s.setAmount(s.getAmount() - take);
            }
            remaining -= take;
        }
        saveStorage(uuid, category, slots);
    }

    private static boolean matches(ItemStack s, ShopItem shopItem, CropItemProvider crops, boolean farmingFruit) {
        if (farmingFruit) {
            return crops != null && shopItem.getTypeId().equals(crops.identifyFruit(s));
        }
        if (s.getType() != shopItem.getMaterial()) return false;
        if (crops != null && (crops.identifyFruit(s) != null || crops.identifySeed(s) != null)) return false;
        return true;
    }
}
