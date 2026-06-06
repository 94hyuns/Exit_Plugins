package com.exit.shop.gui;

import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopItemRegistry;
import com.exit.shop.model.ShopTab;
import com.exit.shop.price.PriceManager;
import com.exit.shop.tab.ShopTabRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 상점 GUI (v1.2.0).
 *
 * <p>자동 분기 두 모드 ({@link ShopTabRegistry#hasTabs} 기준):
 * <ul>
 *   <li><b>단일 페이지 모드</b> (탭 미설정 카테고리): 6행 인벤토리. 한 페이지 5종, 마지막 행에 페이지 네비.
 *       기존 동작 그대로.</li>
 *   <li><b>탭 모드</b> (탭 설정된 카테고리): 6행 중 0행에 탭 버튼, 본문 4행, 5행에 네비.
 *       탭 정보는 모두 config.yml의 tabs 섹션에서 로드되므로 yml만 수정하면 새 탭 추가 가능.</li>
 * </ul>
 *
 * <p>액션 라우팅 토큰:
 * <ul>
 *   <li>일반: {@code "itemId|ACTION|amount"} where ACTION ∈ {BUY, SELL, SELL_ALL}</li>
 *   <li>탭 전환: {@code "__TAB__|tabId|0"}</li>
 * </ul>
 */
public class ShopGUI {

    private static final NamespacedKey ACTION_KEY = NamespacedKey.fromString("shop:action");

    private final ShopItemRegistry registry;
    private final ShopTabRegistry tabRegistry;
    private final ShopButtonStyleRegistry styleRegistry;
    private final PriceManager priceManager;
    private com.exit.shop.service.BulkSellService bulkSellService;

    private final Map<UUID, ShopCategory> openGUIs = new HashMap<>();
    private final Map<UUID, Integer> openPages = new HashMap<>();
    /** 탭 모드일 때 현재 활성 탭 id. 단일 모드면 absent. */
    private final Map<UUID, String> openTabs = new HashMap<>();

    private static final int ITEMS_PER_PAGE_FLAT = 5;   // 단일 모드: 6행 중 1행 네비
    private static final int ITEMS_PER_PAGE_TAB = 4;    // 탭 모드: 6행 중 1행 탭 + 1행 네비
    private static final int ROW_SIZE = 9;

    public ShopGUI(ShopItemRegistry registry, ShopTabRegistry tabRegistry,
                   ShopButtonStyleRegistry styleRegistry, PriceManager priceManager) {
        this.registry = registry;
        this.tabRegistry = tabRegistry;
        this.styleRegistry = styleRegistry;
        this.priceManager = priceManager;
    }

    /** ShopPlugin 에서 후주입 (BulkSellService 가 ShopGUI 보다 먼저 만들어지기 어려운 경우 대비). */
    public void setBulkSellService(com.exit.shop.service.BulkSellService svc) {
        this.bulkSellService = svc;
    }

    // ═══ 진입점 ═══

    public void open(Player player, ShopCategory category) {
        open(player, category, 0, null);
    }

    public void open(Player player, ShopCategory category, int page) {
        // 호환 — 현재 탭 유지
        String currentTab = openTabs.get(player.getUniqueId());
        open(player, category, page, currentTab);
    }

    /**
     * 메인 라우터.
     * @param tabId null 이고 탭 모드면 default 탭으로 진입. 단일 모드면 무시.
     */
    public void open(Player player, ShopCategory category, int page, String tabId) {
        if (tabRegistry.hasTabs(category)) {
            ShopTab tab = (tabId != null
                    ? tabRegistry.getById(category, tabId).orElse(null)
                    : null);
            if (tab == null) tab = tabRegistry.getDefault(category).orElse(null);
            if (tab != null) {
                openTabbed(player, category, tab, page);
                return;
            }
        }
        openFlat(player, category, page);
    }

    // ═══ 단일 페이지 모드 ═══

    private void openFlat(Player player, ShopCategory category, int page) {
        List<ShopItem> items = registry.getByCategory(category);
        int totalPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE_FLAT));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = category.getGuiTitle();
        if (totalPages > 1) title += " §7(" + (page + 1) + "/" + totalPages + ")";

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        int startIdx = page * ITEMS_PER_PAGE_FLAT;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE_FLAT, items.size());

        for (int i = startIdx; i < endIdx; i++) {
            int row = i - startIdx;
            renderItemRow(inv, items.get(i), row * ROW_SIZE, player);
        }

        renderNavRow(inv, page, totalPages);
        renderBulkSellButton(inv, category, player);

        player.openInventory(inv);
        openGUIs.put(player.getUniqueId(), category);
        openPages.put(player.getUniqueId(), page);
        openTabs.remove(player.getUniqueId());
    }

    // ═══ 탭 모드 ═══

    private void openTabbed(Player player, ShopCategory category, ShopTab activeTab, int page) {
        List<ShopItem> categoryItems = registry.getByCategory(category);
        List<ShopItem> filtered = tabRegistry.filterItems(activeTab, categoryItems);

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) ITEMS_PER_PAGE_TAB));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = category.getGuiTitle() + " §7- " + activeTab.name();
        if (totalPages > 1) title += " §8(" + (page + 1) + "/" + totalPages + ")";

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        // 0행: 탭 버튼
        fillRow(inv, 0);
        for (ShopTab tab : tabRegistry.getTabs(category)) {
            inv.setItem(tab.slot(), createTabButton(tab, tab.id().equals(activeTab.id())));
        }

        // 1~4행: 본문
        int startIdx = page * ITEMS_PER_PAGE_TAB;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE_TAB, filtered.size());
        for (int i = startIdx; i < endIdx; i++) {
            int row = (i - startIdx) + 1;
            renderItemRow(inv, filtered.get(i), row * ROW_SIZE, player);
        }

        // 5행: 네비
        renderNavRow(inv, page, totalPages);
        renderBulkSellButton(inv, category, player);

        player.openInventory(inv);
        openGUIs.put(player.getUniqueId(), category);
        openPages.put(player.getUniqueId(), page);
        openTabs.put(player.getUniqueId(), activeTab.id());
    }

    private ItemStack createTabButton(ShopTab tab, boolean active) {
        // 아이콘 ItemStack 결정
        ItemStack stack = null;
        if (tab.usesProvider()) {
            stack = createTabIconFromProvider(tab);
        }
        if (stack == null) {
            // Material 기반 fallback (또는 기본 동작)
            stack = new ItemStack(tab.icon());
            if (tab.hasCustomModelData()) {
                ItemMeta tmpMeta = stack.getItemMeta();
                var cmd = tmpMeta.getCustomModelDataComponent();
                cmd.setFloats(java.util.List.of((float) tab.customModelData()));
                tmpMeta.setCustomModelDataComponent(cmd);
                stack.setItemMeta(tmpMeta);
            }
        }

        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((active ? "▶ " : "") + tab.name())
                .decoration(TextDecoration.ITALIC, false));

        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.lore(List.of(Component.text("§8현재 탭")));
        } else {
            meta.lore(List.of(Component.text("§8클릭하여 전환")));
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                    "__TAB__|" + tab.id() + "|0");
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * provider 기반 탭 아이콘 발급. ServiceRegistry 통해 외부 플러그인의 진짜 아이템을 가져옴.
     * provider 또는 type 미지정/실패 시 null 반환 (fallback to Material).
     */
    private ItemStack createTabIconFromProvider(ShopTab tab) {
        if (tab.iconType() == null) return null;
        switch (tab.iconProvider()) {
            case "Farming" -> {
                String variant = tab.iconVariant();
                if ("TICKET".equals(variant)) {
                    com.exit.core.api.FarmlandTicketProvider tp =
                            ServiceRegistry.get(com.exit.core.api.FarmlandTicketProvider.class).orElse(null);
                    if (tp != null) return tp.createTicket(1);
                } else if ("SEED".equals(variant)) {
                    com.exit.core.api.CropItemProvider crops =
                            ServiceRegistry.get(com.exit.core.api.CropItemProvider.class).orElse(null);
                    if (crops != null) return crops.createSeed(tab.iconType(), 1);
                } else if ("FRUIT".equals(variant)) {
                    com.exit.core.api.CropItemProvider crops =
                            ServiceRegistry.get(com.exit.core.api.CropItemProvider.class).orElse(null);
                    if (crops != null) return crops.createFruit(tab.iconType(), 1, false);
                }
            }
            case "CustomItems" -> {
                com.exit.core.api.LampProvider lamp =
                        ServiceRegistry.get(com.exit.core.api.LampProvider.class).orElse(null);
                if (lamp != null) return lamp.createLamp(tab.iconType(), 1);
            }
        }
        return null;
    }

    // ═══ 본문 행 / 네비 (모드 공통) ═══

    private void renderNavRow(Inventory inv, int page, int totalPages) {
        fillRow(inv, 5);
        if (page > 0) inv.setItem(45, createNavButton(ShopButtonStyleRegistry.Slot.NAV_PREV, "§e◀ 이전 페이지"));
        if (page < totalPages - 1) inv.setItem(53, createNavButton(ShopButtonStyleRegistry.Slot.NAV_NEXT, "§e다음 페이지 ▶"));
        inv.setItem(49, createNavButton(ShopButtonStyleRegistry.Slot.NAV_CLOSE, "§c✕ 닫기"));
    }

    /**
     * 보관함 일괄 판매 버튼. category 가 MINERAL/CROP 일 때만 표시.
     * 슬롯 47 (close 왼쪽). 클릭 → BulkSellService 가 처리.
     */
    private void renderBulkSellButton(Inventory inv, ShopCategory category, Player player) {
        String tag;
        String displayName;
        if (category == ShopCategory.MINERAL) {
            tag = "__BULKSELL__|MINERAL|0";
            displayName = "§6광부의 보관함 일괄 판매";
        } else if (category == ShopCategory.CROP) {
            tag = "__BULKSELL__|CROP|0";
            displayName = "§a농부의 보관함 일괄 판매";
        } else {
            return;
        }
        long preview = bulkSellService != null ? bulkSellService.previewTotal(player, category) : -1;
        String previewLine;
        if (preview < 0) {
            previewLine = "§c연동 플러그인 미로드";
        } else if (preview == 0) {
            previewLine = "§7예상 금액: §80원 (판매 가능 아이템 없음)";
        } else {
            previewLine = "§7예상 금액: §6" + preview + "원";
        }
        org.bukkit.inventory.ItemStack btn = new org.bukkit.inventory.ItemStack(org.bukkit.Material.CHEST);
        org.bukkit.inventory.meta.ItemMeta meta = btn.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(displayName)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, false));
        meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7보관함의 모든 아이템을 한 번에 판매")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text("§7판매 후 보관함은 비워짐")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text(" ")
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                net.kyori.adventure.text.Component.text(previewLine)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, tag);
        btn.setItemMeta(meta);
        inv.setItem(47, btn);
    }

    private void renderItemRow(Inventory inv, ShopItem shopItem, int baseSlot, Player player) {
        int owned = shopItem.isSellable()
                ? com.exit.shop.util.InventoryMatcher.count(player, shopItem, shopItem.getCategory())
                : 0;

        inv.setItem(baseSlot + 1, createInfoIcon(shopItem, owned));

        boolean showBuy = shopItem.isBuyable();
        boolean showBulk = showBuy && !shopItem.isBulkBuyDisabled();
        if (showBuy && shopItem.isSellable()) {
            inv.setItem(baseSlot + 3, createBuyButton(shopItem, 1));
            if (showBulk) inv.setItem(baseSlot + 4, createBuyButton(shopItem, 16));
            inv.setItem(baseSlot + 6, createSellButton(shopItem, 1, owned >= 1));
            inv.setItem(baseSlot + 7, createSellAllButton(shopItem, owned > 0, owned));
        } else if (showBuy) {
            inv.setItem(baseSlot + 3, createBuyButton(shopItem, 1));
            if (showBulk) inv.setItem(baseSlot + 4, createBuyButton(shopItem, 16));
        } else if (shopItem.isSellable()) {
            inv.setItem(baseSlot + 6, createSellButton(shopItem, 1, owned >= 1));
            inv.setItem(baseSlot + 7, createSellAllButton(shopItem, owned > 0, owned));
        }
    }

    // ═══ 아이콘/버튼 ═══

    private ItemStack createInfoIcon(ShopItem item, int owned) {
        ItemStack stack = createDisplayStack(item);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text(item.getDisplayName())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (item.isBuyable()) {
            long buyPrice = priceManager.getBuyPrice(item);
            String tag = item.hasFixedBuyPrice() ? " §8(고정)" : " §8(연동)";
            lore.add(Component.text("§7구매 가격: §f" + format(buyPrice) + "w" + tag));
        }
        if (item.isSellable()) {
            long sellPrice = priceManager.getSellPrice(item);
            String fluctDisplay = priceManager.getFluctuationDisplay(item);
            lore.add(Component.text("§7판매 가격: §f" + format(sellPrice) + "w " + fluctDisplay));
            lore.add(Component.empty());
            lore.add(Component.text("§7보유: §f" + owned + "개"));
        } else {
            lore.add(Component.text("§8§o구매 전용 아이템"));
        }

        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createDisplayStack(ShopItem item) {
        if (!item.isCustomItem()) {
            ItemStack stack = new ItemStack(item.getMaterial());
            com.exit.shop.util.VanillaMetaApplier.apply(stack, item.getMeta(), null, item.getId());
            return stack;
        }
        switch (item.getProvider()) {
            case "CustomItems" -> {
                com.exit.core.api.LampProvider lamp =
                        ServiceRegistry.get(com.exit.core.api.LampProvider.class).orElse(null);
                if (lamp != null) {
                    ItemStack sample = lamp.createLamp(item.getTypeId(), 1);
                    if (sample != null) return sample;
                }
            }
            case "CustomConsumable" -> {
                com.exit.core.api.CustomConsumableProvider cc =
                        ServiceRegistry.get(com.exit.core.api.CustomConsumableProvider.class).orElse(null);
                if (cc != null) {
                    ItemStack sample = cc.createConsumable(item.getTypeId(), 1);
                    if (sample != null) return sample;
                }
            }
            case "Farming" -> {
                String variant = item.getVariant();
                if ("TICKET".equals(variant)) {
                    com.exit.core.api.FarmlandTicketProvider tp =
                            ServiceRegistry.get(com.exit.core.api.FarmlandTicketProvider.class).orElse(null);
                    if (tp != null) {
                        ItemStack sample = tp.createTicket(1);
                        if (sample != null) return sample;
                    }
                } else if ("SEED".equals(variant)) {
                    com.exit.core.api.CropItemProvider crops =
                            ServiceRegistry.get(com.exit.core.api.CropItemProvider.class).orElse(null);
                    if (crops != null) {
                        ItemStack sample = crops.createSeed(item.getTypeId(), 1);
                        if (sample != null) return sample;
                    }
                } else if ("FRUIT".equals(variant)) {
                    com.exit.core.api.CropItemProvider crops =
                            ServiceRegistry.get(com.exit.core.api.CropItemProvider.class).orElse(null);
                    if (crops != null) {
                        ItemStack sample = crops.createFruit(item.getTypeId(), 1, false);
                        if (sample != null) return sample;
                    }
                }
            }
            case "WaterTool" -> {
                com.exit.core.api.WaterToolProvider wt =
                        ServiceRegistry.get(com.exit.core.api.WaterToolProvider.class).orElse(null);
                if (wt != null) {
                    ItemStack sample = wt.createTool(item.getTypeId(), 1);
                    if (sample != null) return sample;
                }
            }
        }
        return new ItemStack(item.getMaterial() != null ? item.getMaterial() : Material.BARRIER);
    }

    private ItemStack createBuyButton(ShopItem item, int amount) {
        long unit = priceManager.getBuyPrice(item);
        long totalPrice = (amount == 16 && item.getBuyPriceBulk() > 0)
                ? item.getBuyPriceBulk()
                : unit * amount;
        ItemStack stack = applyStyle(ShopButtonStyleRegistry.Slot.BUY, Math.min(amount, 64));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("§a구매 " + amount + "개")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("§7가격: §e" + format(totalPrice) + "w"),
                Component.empty(),
                Component.text("§8클릭하여 구매")
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                item.getId() + "|BUY|" + amount);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createSellButton(ShopItem item, int amount, boolean enabled) {
        if (!enabled) return createDisabledSellButton(amount);

        long totalPrice = priceManager.getSellPrice(item) * amount;
        ItemStack stack = applyStyle(ShopButtonStyleRegistry.Slot.SELL, Math.min(amount, 64));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("§c판매 " + amount + "개")
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("§7가격: §e" + format(totalPrice) + "w"),
                Component.empty(),
                Component.text("§8클릭하여 판매")
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                item.getId() + "|SELL|" + amount);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createSellAllButton(ShopItem item, boolean enabled, int owned) {
        if (!enabled) {
            ItemStack stack = applyStyle(ShopButtonStyleRegistry.Slot.SELL_DISABLED, 1);
            ItemMeta meta = stack.getItemMeta();
            meta.displayName(Component.text("§7전체 판매").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("§8보유량 없음"),
                    Component.empty(),
                    Component.text("§8§o아이템을 보유하면 판매 가능")
            ));
            stack.setItemMeta(meta);
            return stack;
        }

        long unitPrice = priceManager.getSellPrice(item);
        ItemStack stack = applyStyle(ShopButtonStyleRegistry.Slot.SELL_ALL, 1);
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(Component.text("§6전체 판매").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7" + item.getDisplayName() + " " + owned + "개 전량 판매"),
                Component.text("§7예상 수익: §e" + format(unitPrice * owned) + "w"),
                Component.empty(),
                Component.text("§8클릭하여 전체 판매")
        ));
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING,
                item.getId() + "|SELL_ALL|0");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createDisabledSellButton(int amount) {
        ItemStack stack = applyStyle(ShopButtonStyleRegistry.Slot.SELL_DISABLED, Math.min(amount, 64));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("§7판매 " + amount + "개").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§8보유량 부족"),
                Component.empty(),
                Component.text("§8§o아이템을 보유하면 판매 가능")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 버튼 스타일(Material + 선택적 CMD)을 적용한 ItemStack 생성.
     */
    private ItemStack applyStyle(ShopButtonStyleRegistry.Slot slot, int amount) {
        ShopButtonStyleRegistry.Style style = styleRegistry.get(slot);
        ItemStack stack = new ItemStack(style.material(), amount);
        if (style.hasCustomModelData()) {
            ItemMeta meta = stack.getItemMeta();
            var cmd = meta.getCustomModelDataComponent();
            cmd.setFloats(java.util.List.of((float) style.customModelData()));
            meta.setCustomModelDataComponent(cmd);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createNavButton(ShopButtonStyleRegistry.Slot slot, String name) {
        ItemStack stack = applyStyle(slot, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    private void fillRow(Inventory inv, int rowIndex) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        int from = rowIndex * ROW_SIZE;
        for (int i = from; i < from + ROW_SIZE; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, filler);
        }
    }

    // ═══ GUI 상태 관리 ═══

    public Optional<ShopCategory> getOpenCategory(UUID playerId) {
        return Optional.ofNullable(openGUIs.get(playerId));
    }

    public Optional<String> getOpenTab(UUID playerId) {
        return Optional.ofNullable(openTabs.get(playerId));
    }

    public int getOpenPage(UUID playerId) {
        return openPages.getOrDefault(playerId, 0);
    }

    public boolean hasTabs(ShopCategory category) {
        return tabRegistry.hasTabs(category);
    }

    public void close(UUID playerId) {
        openGUIs.remove(playerId);
        openPages.remove(playerId);
        openTabs.remove(playerId);
    }

    public Optional<String> getAction(ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return Optional.empty();
        var pdc = clickedItem.getItemMeta().getPersistentDataContainer();
        if (pdc.has(ACTION_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(ACTION_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }

    private String format(long value) {
        return String.format("%,d", value);
    }
}
