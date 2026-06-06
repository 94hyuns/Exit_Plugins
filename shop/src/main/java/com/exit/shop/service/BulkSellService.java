package com.exit.shop.service;

import com.exit.core.api.CropItemProvider;
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopItemRegistry;
import com.exit.shop.price.PriceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 보관함 일괄 판매 처리. ShopListener 가 BULK_SELL_STORAGE|<CATEGORY> action 받으면 호출.
 *
 * <p>매칭:
 * <ul>
 *   <li>MINERAL → Job 의 MineralStorageManager</li>
 *   <li>CROP → Farming 의 CropStorageManager</li>
 * </ul>
 *
 * <p>가격: storage 각 ItemStack 의 Material 로 ShopItemRegistry 검색 후 PriceManager.getSellPrice * count 합산.
 * Material 매칭 안 되는 아이템은 sellable 아니므로 0원 처리하고 player 인벤에 반환.
 */
public class BulkSellService {

    private final JavaPlugin plugin;
    private final ShopItemRegistry registry;
    private final PriceManager priceManager;
    private final com.exit.shop.stats.TransactionLog txLog;

    public BulkSellService(JavaPlugin plugin, ShopItemRegistry registry,
                           PriceManager priceManager,
                           com.exit.shop.stats.TransactionLog txLog) {
        this.plugin = plugin;
        this.registry = registry;
        this.priceManager = priceManager;
        this.txLog = txLog;
    }

    /**
     * 보관함 일괄판매 시 받게 될 합계 미리보기. 보관함 데이터는 건드리지 않음.
     * 매칭 안 되는 아이템 / 비-sellable 은 0원 처리.
     * 플러그인 미로드 시 -1 반환.
     */
    public long previewTotal(Player player, ShopCategory category) {
        ItemStack[] data = peekFor(category, player.getUniqueId());
        if (data == null) return -1;
        long total = 0;
        for (ItemStack stack : data) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            ShopItem match = findShopItemForStack(stack, category);
            if (match == null || !match.isSellable()) continue;
            total += priceManager.getSellPrice(match) * stack.getAmount();
        }
        return total;
    }

    private ItemStack[] peekFor(ShopCategory category, UUID uuid) {
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

    public void execute(Player player, ShopCategory category) {
        UUID uuid = player.getUniqueId();
        ItemStack[] data = takeAllFor(category, uuid);
        if (data == null) {
            player.sendMessage(Component.text("연동된 플러그인이 로드되지 않아 일괄 판매할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }

        long total = 0;
        int soldCount = 0;
        int returnedCount = 0;

        boolean crashEnabled = plugin.getConfig().getBoolean("price-crash.enabled", true);
        long crashLow  = plugin.getConfig().getLong("price-crash.threshold-low", 640);
        long crashHigh = plugin.getConfig().getLong("price-crash.threshold-high", 1600);
        double crashMultiplier = plugin.getConfig().getDouble("price-crash.multiplier", 0.75);

        // 1단계: 정산 + itemId 별 amount 합산 (폭락 굴림은 itemId 당 1회만)
        Map<String, Long> aggregateAmount = new HashMap<>();
        Map<String, ShopItem> matchedItems = new HashMap<>();
        for (ItemStack stack : data) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;

            ShopItem match = findShopItemForStack(stack, category);
            if (match == null || !match.isSellable()) {
                // 가격 매칭 실패 → 인벤 반환
                returnItem(player, stack);
                returnedCount += stack.getAmount();
                continue;
            }
            int amt = stack.getAmount();
            long unit = priceManager.getSellPrice(match);
            long sub = unit * amt;
            total += sub;
            soldCount += amt;
            txLog.recordSell(uuid, match.getId(), amt, sub);

            aggregateAmount.merge(match.getId(), (long) amt, Long::sum);
            matchedItems.putIfAbsent(match.getId(), match);
        }

        // 2단계: itemId 마다 한 번씩 trigger 체크
        boolean crashSaveNeeded = false;
        if (crashEnabled) {
            for (Map.Entry<String, Long> entry : aggregateAmount.entrySet()) {
                String itemId = entry.getKey();
                ShopItem item = matchedItems.get(itemId);
                PriceManager.TriggerResult tr = priceManager.recordSellAndCheckTrigger(
                        itemId, uuid, entry.getValue(), crashLow, crashHigh);
                applyTriggers(item, tr, crashMultiplier);
                if (tr.total() > 0) crashSaveNeeded = true;
            }
        } else {
            for (Map.Entry<String, Long> entry : aggregateAmount.entrySet()) {
                priceManager.recordSell(entry.getKey(), entry.getValue().intValue());
            }
        }

        if (crashSaveNeeded) priceManager.save();

        if (total > 0) {
            EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
            if (eco != null) {
                eco.addBalance(uuid, total);
            }
        }

        Component msg = Component.text("[일괄 판매] ", NamedTextColor.GOLD)
                .append(Component.text(soldCount + "개 판매 / " + total + "원 입금",
                        NamedTextColor.GREEN))
                .decoration(TextDecoration.BOLD, false);
        player.sendMessage(msg);
        if (returnedCount > 0) {
            player.sendMessage(Component.text("[일괄 판매] 판매 불가 아이템 "
                            + returnedCount + "개는 인벤토리로 반환",
                    NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false));
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
    }

    /**
     * 보관함 stack 의 정확한 ShopItem 매칭.
     *
     * <p>CROP 카테고리는 모든 열매가 Material.BEETROOT 라 Material 매칭만으론 첫 등록 작물(밀)로
     * 잘못 매칭됨. PDC 의 crop_id 를 읽어 typeId 로 정확히 매칭한다.
     * CROP 외 / PDC 없음 → Material 매칭 fallback.
     */
    private ShopItem findShopItemForStack(ItemStack stack, ShopCategory category) {
        if (category == ShopCategory.CROP) {
            CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
            if (crops != null) {
                String cropId = crops.identifyFruit(stack);
                if (cropId != null) {
                    for (ShopItem si : registry.getByCategory(category)) {
                        if ("Farming".equals(si.getProvider())
                                && "FRUIT".equals(si.getVariant())
                                && cropId.equals(si.getTypeId())) {
                            return si;
                        }
                    }
                    return null;  // 작물 열매인데 등록 없음 → 판매 불가
                }
            }
        }
        // 바닐라 (광물 등) Material 매칭
        return findShopItemByMaterial(stack.getType(), category);
    }

    private ShopItem findShopItemByMaterial(Material m, ShopCategory category) {
        for (ShopItem si : registry.getByCategory(category)) {
            // 작물 열매는 Material 매칭 대상에서 제외 (모두 BEETROOT 라 충돌)
            if ("Farming".equals(si.getProvider()) && "FRUIT".equals(si.getVariant())) continue;
            if (si.getMaterial() == m) return si;
        }
        return null;
    }

    /**
     * category 매칭되는 plugin 의 storage 에서 take.
     * CROP 은 씨앗을 보관함에 그대로 두고 열매(및 기타)만 꺼냄 — 씨앗은 판매 불가라
     * 인벤 반환 / 보관함 비우기 둘 다 사용자 입장에서 번거롭기 때문.
     * plugin 미설치 시 null.
     */
    private ItemStack[] takeAllFor(ShopCategory category, UUID uuid) {
        try {
            if (category == ShopCategory.MINERAL) {
                if (Bukkit.getPluginManager().getPlugin("Job") == null) return null;
                var jobPlugin = com.exit.job.JobPlugin.getInstance();
                if (jobPlugin == null) return null;
                return jobPlugin.getMineralStorageManager().takeAll(uuid);
            } else if (category == ShopCategory.CROP) {
                if (Bukkit.getPluginManager().getPlugin("Farming") == null) return null;
                var farmingPlugin = com.exit.farming.FarmingPlugin.getInstance();
                if (farmingPlugin == null) return null;
                return takeCropStorageExceptSeeds(farmingPlugin.getCropStorageManager(), uuid);
            }
        } catch (NoClassDefFoundError ignored) {
            return null;
        }
        return null;
    }

    /**
     * 농부 보관함에서 씨앗을 제외한 모든 아이템을 꺼냄. 씨앗은 보관함에 그대로 보존.
     * CropItemProvider 미등록 시 안전하게 전체 takeAll fallback.
     */
    private ItemStack[] takeCropStorageExceptSeeds(
            com.exit.farming.storage.CropStorageManager mgr, UUID uuid) {
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        if (crops == null) return mgr.takeAll(uuid);

        ItemStack[] slots = mgr.load(uuid);
        ItemStack[] kept = new ItemStack[slots.length];
        ItemStack[] taken = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack s = slots[i];
            if (s == null || s.getType().isAir()) continue;
            if (crops.identifySeed(s) != null) {
                kept[i] = s;
            } else {
                taken[i] = s;
            }
        }
        mgr.save(uuid, kept);
        return taken;
    }

    private void returnItem(Player player, ItemStack stack) {
        var leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> player.getWorld()
                    .dropItemNaturally(player.getLocation(), s));
        }
    }

    /** trigger 결과에 따라 확정/확률 폭락 적용 + 메시지. */
    private void applyTriggers(ShopItem item, PriceManager.TriggerResult tr, double mult) {
        for (int i = 0; i < tr.deterministicCount(); i++) {
            double prev = priceManager.triggerCrash(item.getId(), mult);
            broadcastCrash(item, tr.topSeller(), prev);
        }
        if (tr.probabilisticCount() > 0) {
            double prev = priceManager.triggerCrash(item.getId(), mult);
            broadcastCrashRandom(item, prev);
        }
    }

    /**
     * 시세 폭락 broadcast — chunk 의 max 기여자 이름 노출 (수치 임계값은 비공개).
     * ShopListener 의 동일 헬퍼와 한 쌍 — 향후 별도 util 추출 고려.
     */
    private void broadcastCrash(ShopItem item, UUID topSellerUuid, double prevFluct) {
        String name = Bukkit.getOfflinePlayer(topSellerUuid).getName();
        if (name == null) name = "알 수 없는 플레이어";
        Bukkit.broadcast(
                Component.text("[시세 알림] ", NamedTextColor.GOLD)
                        .append(Component.text(name, NamedTextColor.YELLOW))
                        .append(Component.text(" 님이 ", NamedTextColor.WHITE))
                        .append(Component.text(item.getDisplayName(), NamedTextColor.AQUA))
                        .append(Component.text(" 을(를) 대량 매도! 가격 폭락 (-25%)", NamedTextColor.RED))
        );
        Player top = Bukkit.getPlayer(topSellerUuid);
        if (top != null) {
            top.sendMessage(
                    Component.text("📉 ", NamedTextColor.RED)
                            .append(Component.text("당신이 가장 큰 지분으로 시세 하락에 기여했습니다.", NamedTextColor.YELLOW))
            );
        }
    }

    /** 확률 폭락 broadcast — 자연 발생 느낌 (top seller 표기 X). */
    private void broadcastCrashRandom(ShopItem item, double prevFluct) {
        Bukkit.broadcast(
                Component.text("[악재] ", NamedTextColor.GOLD)
                        .append(Component.text(item.getDisplayName(), NamedTextColor.AQUA))
                        .append(Component.text(" 에 악재가 터졌습니다! 가격이 폭락합니다! (-25%)",
                                NamedTextColor.RED))
        );
    }
}
