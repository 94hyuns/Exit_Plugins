package com.exit.shop.listener;

import com.exit.core.api.CropItemProvider;
import com.exit.core.api.CustomConsumableProvider;
import com.exit.core.api.EconomyProvider;
import com.exit.core.api.FarmlandTicketProvider;
import com.exit.core.api.FishShopProvider;
import com.exit.core.api.LampProvider;
import com.exit.core.api.WaterToolProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.gui.ShopGUI;
import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopItemRegistry;
import com.exit.shop.npc.ShopNPCManager;
import com.exit.shop.price.PriceManager;
import com.exit.shop.util.InventoryMatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

import java.util.Optional;

/**
 * 상점 이벤트 리스너.
 *
 * - PlayerUseUnknownEntityEvent: 패킷 기반 가짜 플레이어 NPC 상호작용
 *   → FISHING 이면 FishShopProvider 경유, 그 외는 일반 Shop GUI
 * - PlayerJoinEvent / PlayerChangedWorldEvent / PlayerRespawnEvent:
 *   클라이언트가 NPC를 잊는 시점마다 패킷 재전송
 * - InventoryClickEvent: Shop GUI 내 구매/판매
 *
 * Provider 라우팅:
 *   - CustomItems (LampProvider) → 램프 발급
 *   - Farming (CropItemProvider / FarmlandTicketProvider) → 작물 열매 / 경작지 티켓
 */
public class ShopListener implements Listener {

    private final JavaPlugin plugin;
    private final ShopNPCManager npcManager;
    private final ShopGUI shopGUI;
    private final ShopItemRegistry registry;
    private final PriceManager priceManager;
    private final com.exit.shop.service.BulkSellService bulkSellService;
    private final com.exit.shop.stats.TransactionLog txLog;

    public ShopListener(JavaPlugin plugin, ShopNPCManager npcManager, ShopGUI shopGUI,
                        ShopItemRegistry registry, PriceManager priceManager,
                        com.exit.shop.stats.TransactionLog txLog) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.shopGUI = shopGUI;
        this.registry = registry;
        this.priceManager = priceManager;
        this.txLog = txLog;
        this.bulkSellService = new com.exit.shop.service.BulkSellService(plugin, registry, priceManager, txLog);
        // ShopGUI 가 미리보기에 service 를 쓸 수 있도록 후주입
        shopGUI.setBulkSellService(this.bulkSellService);
    }

    // ─── NPC 클릭 라우팅 ───
    // (NpcService 클릭 핸들러에서 호출됨. ShopPlugin.onEnable 에서 등록.)

    public void onNpcClick(Player player, ShopCategory category) {
        // 낚시 상인은 Fishing 플러그인의 전용 GUI로 라우팅
        if (category == ShopCategory.FISHING) {
            FishShopProvider fish = ServiceRegistry.get(FishShopProvider.class).orElse(null);
            if (fish == null) {
                player.sendMessage(Component.text(
                        "낚시 플러그인이 로드되지 않아 상점을 열 수 없습니다.", NamedTextColor.RED));
                return;
            }
            fish.openShop(player);
            return;
        }
        // 그 외 카테고리는 일반 Shop GUI
        shopGUI.open(player, category);
    }

    // ─── GUI 클릭 처리 ───

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Optional<ShopCategory> catOpt = shopGUI.getOpenCategory(player.getUniqueId());
        if (catOpt.isEmpty()) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ShopCategory category = catOpt.get();

        // 플레이어 인벤토리 하단 클릭은 무시 (rawSlot >= 54)
        int slot = event.getRawSlot();
        if (slot >= 54 || slot < 0) return;

        // 네비게이션 (5행 = 절대 슬롯 45=이전, 49=닫기, 53=다음)
        if (slot == 45 || slot == 53) {
            int currentPage = shopGUI.getOpenPage(player.getUniqueId());
            int newPage = (slot == 45) ? currentPage - 1 : currentPage + 1;
            String tab = shopGUI.getOpenTab(player.getUniqueId()).orElse(null);
            shopGUI.open(player, category, newPage, tab);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // 액션 태그 추출
        Optional<String> actionOpt = shopGUI.getAction(clicked);
        if (actionOpt.isEmpty()) return;

        String[] parts = actionOpt.get().split("\\|");
        if (parts.length != 3) return;

        // 탭 전환 라우팅 (모든 카테고리에 일반화됨)
        if ("__TAB__".equals(parts[0])) {
            String tabId = parts[1];
            shopGUI.open(player, category, 0, tabId);
            return;
        }

        // 보관함 일괄 판매
        if ("__BULKSELL__".equals(parts[0])) {
            ShopCategory targetCat;
            try { targetCat = ShopCategory.valueOf(parts[1]); }
            catch (IllegalArgumentException e) { return; }
            bulkSellService.execute(player, targetCat);
            // 닫고 재오픈 (storage 비워진 상태 반영 불필요 — 그냥 닫기)
            player.closeInventory();
            return;
        }

        String itemId = parts[0];
        String action = parts[1];
        int amount;
        try { amount = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { return; }

        ShopItem shopItem = registry.get(itemId);
        if (shopItem == null) return;

        switch (action) {
            case "BUY"      -> handleBuy(player, shopItem, amount, category);
            case "SELL"     -> handleSell(player, shopItem, amount, category);
            case "SELL_ALL" -> handleSellAll(player, shopItem, category);
        }
    }

    // ─── 구매 ───

    private void handleBuy(Player player, ShopItem item, int amount, ShopCategory category) {
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            player.sendMessage(Component.text("경제 시스템을 불러올 수 없습니다.").color(NamedTextColor.RED));
            return;
        }

        ItemStack giveItem = resolvePurchaseStack(item, amount);
        if (giveItem == null) {
            player.sendMessage(Component.text("현재 이 아이템은 구매할 수 없습니다. 관리자에게 문의하세요.")
                    .color(NamedTextColor.RED));
            return;
        }

        long unit = priceManager.getBuyPrice(item);
        long totalPrice = (amount == 16 && item.getBuyPriceBulk() > 0)
                ? item.getBuyPriceBulk()
                : unit * amount;

        if (!eco.subtractBalance(player.getUniqueId(), totalPrice)) {
            long balance = eco.getBalance(player.getUniqueId());
            player.sendMessage(
                    Component.text("잔액이 부족합니다. 현재 잔액: ").color(NamedTextColor.RED)
                            .append(Component.text(String.format("%,d", balance) + "w").color(NamedTextColor.GOLD))
            );
            return;
        }

        if (!hasSpace(player, giveItem)) {
            eco.addBalance(player.getUniqueId(), totalPrice);
            player.sendMessage(Component.text("인벤토리에 공간이 부족합니다.").color(NamedTextColor.RED));
            return;
        }

        player.getInventory().addItem(giveItem);
        priceManager.recordBuy(item.getId(), amount);
        txLog.recordBuy(player.getUniqueId(), item.getId(), amount, totalPrice);

        player.sendMessage(
                Component.text(item.getDisplayName() + " " + amount + "개를 ").color(NamedTextColor.GREEN)
                        .append(Component.text(String.format("%,d", totalPrice) + "w").color(NamedTextColor.GOLD))
                        .append(Component.text("에 구매했습니다.").color(NamedTextColor.GREEN))
        );

        reopenAfterTrade(player, category);
    }

    /**
     * 구매 시 지급할 ItemStack 해결.
     * - 바닐라: Material 기반 ItemStack 생성
     * - CustomItems provider: LampProvider 로 램프 발급
     * - Farming provider — variant로 분기:
     *     TICKET → FarmlandTicketProvider.createTicket
     *     SEED   → CropItemProvider.createSeed
     *     FRUIT  → CropItemProvider.createFruit (premium=false)
     *              실제로는 열매가 sell-only라 BUY 경로엔 안 옴. 안전망.
     */
    private ItemStack resolvePurchaseStack(ShopItem item, int amount) {
        if (!item.isCustomItem()) {
            ItemStack stack = new ItemStack(item.getMaterial(), amount);
            com.exit.shop.util.VanillaMetaApplier.apply(stack, item.getMeta(), plugin.getLogger(), item.getId());
            return stack;
        }
        return switch (item.getProvider()) {
            case "CustomItems" -> {
                LampProvider lamp = ServiceRegistry.get(LampProvider.class).orElse(null);
                if (lamp == null) {
                    plugin.getLogger().warning("[Shop] LampProvider 미등록 — '"
                            + item.getId() + "' 구매 불가. CustomItems 플러그인 확인 필요.");
                    yield null;
                }
                yield lamp.createLamp(item.getTypeId(), amount);
            }
            case "CustomConsumable" -> {
                CustomConsumableProvider cc = ServiceRegistry.get(CustomConsumableProvider.class).orElse(null);
                if (cc == null) {
                    plugin.getLogger().warning("[Shop] CustomConsumableProvider 미등록 — '"
                            + item.getId() + "' 구매 불가. CustomItems 플러그인 확인 필요.");
                    yield null;
                }
                yield cc.createConsumable(item.getTypeId(), amount);
            }
            case "Farming" -> {
                String variant = item.getVariant();
                if ("TICKET".equals(variant)) {
                    FarmlandTicketProvider tp = ServiceRegistry.get(FarmlandTicketProvider.class).orElse(null);
                    if (tp == null) {
                        plugin.getLogger().warning("[Shop] FarmlandTicketProvider 미등록 — '"
                                + item.getId() + "' 구매 불가");
                        yield null;
                    }
                    yield tp.createTicket(amount);
                }
                CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
                if (crops == null) {
                    plugin.getLogger().warning("[Shop] CropItemProvider 미등록 — '"
                            + item.getId() + "' 구매 불가");
                    yield null;
                }
                if ("SEED".equals(variant)) {
                    yield crops.createSeed(item.getTypeId(), amount);
                }
                if ("FRUIT".equals(variant)) {
                    yield crops.createFruit(item.getTypeId(), amount, false);
                }
                plugin.getLogger().warning("[Shop] '" + item.getId() + "' Farming variant 미설정 — fallback createFruit");
                yield crops.createFruit(item.getTypeId(), amount, false);
            }
            case "WaterTool" -> {
                WaterToolProvider wt = ServiceRegistry.get(WaterToolProvider.class).orElse(null);
                if (wt == null) {
                    plugin.getLogger().warning("[Shop] WaterToolProvider 미등록 — '"
                            + item.getId() + "' 구매 불가. Farming 1.3.0+ 가 활성인지 확인.");
                    yield null;
                }
                ItemStack stack = wt.createTool(item.getTypeId(), amount);
                if (stack == null) {
                    plugin.getLogger().warning("[Shop] '" + item.getId() + "' 알 수 없는 WaterTool typeId: "
                            + item.getTypeId());
                }
                yield stack;
            }
            case "MythicMobs" -> {
                // MythicMobs item 발급 — cooking_pot 등 외부 팩 아이템 판매용
                try {
                    var mm = io.lumine.mythic.bukkit.MythicBukkit.inst();
                    if (mm == null) {
                        plugin.getLogger().warning("[Shop] MythicMobs 미로드 — '"
                                + item.getId() + "' 구매 불가");
                        yield null;
                    }
                    var opt = mm.getItemManager().getItem(item.getTypeId());
                    if (opt.isEmpty()) {
                        plugin.getLogger().warning("[Shop] MythicMobs item '"
                                + item.getTypeId() + "' 미정의 (item: " + item.getId() + ")");
                        yield null;
                    }
                    org.bukkit.inventory.ItemStack stack =
                            io.lumine.mythic.bukkit.BukkitAdapter.adapt(
                                    opt.get().generateItemStack(amount));
                    yield stack;
                } catch (Throwable t) {
                    plugin.getLogger().warning("[Shop] MythicMobs item 발급 실패 ('"
                            + item.getId() + "'): " + t.getMessage());
                    yield null;
                }
            }
            default -> {
                plugin.getLogger().warning("[Shop] 알 수 없는 provider: '" + item.getProvider()
                        + "' (item: " + item.getId() + ")");
                yield null;
            }
        };
    }

    // ─── 판매 ───

    private void handleSell(Player player, ShopItem item, int amount, ShopCategory category) {
        if (!item.isSellable()) {
            player.sendMessage(Component.text("이 아이템은 판매할 수 없습니다.").color(NamedTextColor.RED));
            return;
        }

        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            player.sendMessage(Component.text("경제 시스템을 불러올 수 없습니다.").color(NamedTextColor.RED));
            return;
        }

        int has = InventoryMatcher.count(player, item, category);
        if (has < amount) {
            player.sendMessage(
                    Component.text(item.getDisplayName() + "이(가) 부족합니다. 보유: " + has + "개")
                            .color(NamedTextColor.RED)
            );
            return;
        }

        long unitPrice = priceManager.getSellPrice(item);
        long totalPrice = unitPrice * amount;

        InventoryMatcher.remove(player, item, amount, category);
        eco.addBalance(player.getUniqueId(), totalPrice);
        txLog.recordSell(player.getUniqueId(), item.getId(), amount, totalPrice);

        // 누적 폭락 trigger — low/high 선형 보간 (low=0%, high=100% + high 단위 결정 폭락)
        if (plugin.getConfig().getBoolean("price-crash.enabled", true)) {
            long low  = plugin.getConfig().getLong("price-crash.threshold-low", 640);
            long high = plugin.getConfig().getLong("price-crash.threshold-high", 1600);
            double multiplier = plugin.getConfig().getDouble("price-crash.multiplier", 0.75);
            PriceManager.TriggerResult tr = priceManager.recordSellAndCheckTrigger(
                    item.getId(), player.getUniqueId(), amount, low, high);
            for (int i = 0; i < tr.deterministicCount(); i++) {
                double prevFluct = priceManager.triggerCrash(item.getId(), multiplier);
                broadcastCrash(item, tr.topSeller(), prevFluct);
            }
            if (tr.probabilisticCount() > 0) {
                double prevFluct = priceManager.triggerCrash(item.getId(), multiplier);
                broadcastCrashRandom(item, prevFluct);
            }
            if (tr.total() > 0) priceManager.save();
        } else {
            priceManager.recordSell(item.getId(), amount);
        }

        player.sendMessage(
                Component.text(item.getDisplayName() + " " + amount + "개를 ").color(NamedTextColor.GREEN)
                        .append(Component.text(String.format("%,d", totalPrice) + "w").color(NamedTextColor.GOLD))
                        .append(Component.text("에 판매했습니다.").color(NamedTextColor.GREEN))
        );

        reopenAfterTrade(player, category);
    }

    /**
     * 시세 폭락 broadcast — chunk 의 max 기여자 이름 노출 (수치 임계값은 비공개).
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

    private void handleSellAll(Player player, ShopItem item, ShopCategory category) {
        int total = InventoryMatcher.count(player, item, category);
        if (total <= 0) {
            player.sendMessage(Component.text(item.getDisplayName() + "이(가) 없습니다.").color(NamedTextColor.RED));
            return;
        }
        handleSell(player, item, total, category);
    }

    // ─── GUI 재오픈 (구매/판매 후) ───

    private void reopenAfterTrade(Player player, ShopCategory category) {
        int page = shopGUI.getOpenPage(player.getUniqueId());
        String tab = shopGUI.getOpenTab(player.getUniqueId()).orElse(null);
        shopGUI.open(player, category, page, tab);
    }

    // ─── GUI 닫기 ───

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            shopGUI.close(player.getUniqueId());
        }
    }

    // ─── 유틸 ───

    private boolean hasSpace(Player player, ItemStack item) {
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack == null || stack.getType() == Material.AIR) return true;
            if (stack.isSimilar(item) && stack.getAmount() + item.getAmount() <= stack.getMaxStackSize()) return true;
        }
        return false;
    }
}
