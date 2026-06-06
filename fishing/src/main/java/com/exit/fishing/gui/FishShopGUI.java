package com.exit.fishing.gui;

import com.exit.fishing.FishingPlugin;
import com.exit.fishing.fish.FishRegistry;
import com.exit.fishing.fish.FishSpecies;
import com.exit.fishing.item.FishItem;
import com.exit.fishing.season.Season;
import com.exit.fishing.season.SeasonManager;
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 낚시 상점 GUI (5행, 45슬롯).
 *
 * 레이아웃 (7×4 = 28 판매 슬롯):
 *   슬롯 1~7, 10~16, 19~25, 28~34 : 판매 슬롯 (플레이어가 물고기 넣는 곳)
 *   슬롯 8, 17, 26, 35 : 행 구분 장식
 *   슬롯 0, 9, 18, 27, 36~44 : 테두리 장식
 *   슬롯 40 : 생선 바구니 (전체 판매 버튼)
 *   슬롯 44 : 어류 도감 (계절별 제철 조회)
 *
 * 판매가: (cm + g * gram-weight) × 계절 배수 (제철 1.5 / 반대 0.5 / 중간 1.0) × 최고급 배수
 * 울캐쉬로 바로 입금 (Core EconomyProvider).
 */
public class FishShopGUI {

    public static final String TITLE_PREFIX = "낚시 상점 - ";

    /** 판매 슬롯 (7x4) */
    private static final int[] SELL_SLOTS = {
            0, 1, 2, 3, 4, 5, 6,
            9, 10, 11, 12, 13, 14, 15,
            18, 19, 20, 21, 22, 23, 24,
            27, 28, 29, 30, 31, 32, 33
    };

    private static final int SLOT_BASKET = 40;
    private static final int SLOT_CODEX = 44;
    private static final int SLOT_BULK_STORAGE = 36;
    /** 현재 계절 물고기만 일괄판매 (보관함 내 in-season 만 추출). 2026-05-14 추가. */
    private static final int SLOT_BULK_SEASONAL = 37;

    private FishShopGUI() {}

    public static void open(Player player, SeasonManager seasons) {
        Season season = seasons.current();
        Inventory inv = Bukkit.createInventory(
                new FishShopHolder(season),
                45,
                Component.text(TITLE_PREFIX + season.korean())
                        .color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false)
        );

        // 테두리 장식 (36 = 보관함 일괄판매, 37 = 계절 일괄판매)
        ItemStack filler = filler();
        int[] decor = {7, 8, 16, 17, 25, 26, 34, 35, 38, 39, 41, 42, 43};
        for (int s : decor) inv.setItem(s, filler);

        // 보관함 일괄 판매 버튼 + 예상 금액 미리보기
        long bulkPreview = previewStorageTotal(player, season, FishingPlugin.getInstance());
        String bulkPreviewLine;
        if (bulkPreview < 0)        bulkPreviewLine = "§c보관함 시스템 미설치";
        else if (bulkPreview == 0)  bulkPreviewLine = "§7예상 금액: §80w (보관함 비어있음)";
        else                        bulkPreviewLine = "§7예상 금액: §6" + bulkPreview + "w";

        ItemStack bulk = new ItemStack(Material.CHEST);
        bulk.editMeta(meta -> {
            meta.displayName(Component.text("어부의 보관함 일괄 판매")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(
                    Component.text("보관함의 모든 물고기를 한 번에 판매").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("판매 후 보관함은 비워짐").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(" ").decoration(TextDecoration.ITALIC, false),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(bulkPreviewLine)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });
        inv.setItem(SLOT_BULK_STORAGE, bulk);

        // 계절 일괄 판매 버튼 + 현재 계절 추출 금액 미리보기
        long seasonalPreview = previewStorageInSeasonTotal(player, season, FishingPlugin.getInstance());
        String seasonalPreviewLine;
        if (seasonalPreview < 0)       seasonalPreviewLine = "§c보관함 시스템 미설치";
        else if (seasonalPreview == 0) seasonalPreviewLine = "§7예상 금액: §80w (제철 없음)";
        else                           seasonalPreviewLine = "§7예상 금액: §6" + seasonalPreview + "w";

        ItemStack seasonalBulk = new ItemStack(Material.TROPICAL_FISH);
        final Season frozenSeason = season;
        seasonalBulk.editMeta(meta -> {
            meta.displayName(Component.text("현재 계절(" + frozenSeason.korean() + ") 일괄 판매")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(
                    Component.text("보관함의 제철 물고기만 판매").color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("(" + frozenSeason.korean() + " 외의 물고기는 그대로 보관)")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text(" ").decoration(TextDecoration.ITALIC, false),
                    LegacyComponentSerializer.legacyAmpersand().deserialize(seasonalPreviewLine)
                            .decoration(TextDecoration.ITALIC, false)
            ));
        });
        inv.setItem(SLOT_BULK_SEASONAL, seasonalBulk);

        // 바구니 — 표시명만 설정. lore 는 updateBasketPreview() 가 갱신.
        ItemStack basket = new ItemStack(Material.COD_BUCKET);
        basket.editMeta(meta -> meta.displayName(Component.text("생선 바구니", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)));
        inv.setItem(SLOT_BASKET, basket);
        // open 시점은 빈 슬롯이라 0마리 / 0w 로 표시됨
        updateBasketPreview(inv, season, FishingPlugin.getInstance());

        // 도감
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        book.editMeta(meta -> {
            meta.displayName(Component.text("어류 도감", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(randomTip()));
        });
        inv.setItem(SLOT_CODEX, book);

        player.openInventory(inv);
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }

    private static Component randomTip() {
        String[] tips = {
                "현재 계절의 반대 생선은 판매시 0.5배 보상만 받습니다",
                "계절에 따른 제철 생선은 도감에서 확인할 수 있습니다",
                "등급은 크기와 질량에 따라 책정됩니다",
                "현재 계절의 반대 생선은 낚이지 않습니다",
                "생선은 24종류의 월별 생선이 있습니다",
                "각각 제철 생선은 1.5배 수익을 냅니다",
                "아주 드물게 최고급 물고기가 나옵니다"
        };
        String tip = tips[ThreadLocalRandom.current().nextInt(tips.length)];
        return Component.text("낚시 tip : " + tip, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static boolean isSellSlot(int slot) {
        for (int s : SELL_SLOTS) if (s == slot) return true;
        return false;
    }

    public static int basketSlot() { return SLOT_BASKET; }
    public static int codexSlot() { return SLOT_CODEX; }

    /** 한 마리 ItemStack 의 판매가 계산 (단위 가격 × amount). */
    public static long calcStackPrice(ItemStack it, Season season, FishingPlugin plugin) {
        if (it == null || !FishItem.isFish(it)) return 0;
        FishSpecies sp = FishItem.getSpecies(it);
        if (sp == null) return 0;

        double cmWeight = plugin.getConfig().getDouble("price.cm-weight", 0.3);
        double gramWeight = plugin.getConfig().getDouble("price.gram-weight", 0.15);
        double inMul = plugin.getConfig().getDouble("price.in-season-multiplier", 1.5);
        double offMul = plugin.getConfig().getDouble("price.off-season-multiplier", 0.5);
        double premiumMul = plugin.getConfig().getDouble("price.premium-multiplier", 2.0);

        double mul;
        if (FishRegistry.inSeason(season).contains(sp)) mul = inMul;
        else if (FishRegistry.offSeason(season).contains(sp)) mul = offMul;
        else mul = 1.0;

        int cm = FishItem.getLength(it);
        int g = FishItem.getMass(it);
        boolean premium = FishItem.isPremium(it);

        double perFish = (cm * cmWeight + g * gramWeight) * mul;
        if (premium) perFish *= premiumMul;
        return Math.round(perFish * it.getAmount());
    }

    /** 판매 슬롯 전체의 합산 판매가 + 마리 수. 미리보기용. */
    public static long calcTotalPrice(Inventory inv, Season season, FishingPlugin plugin) {
        long total = 0;
        for (int slot : SELL_SLOTS) {
            total += calcStackPrice(inv.getItem(slot), season, plugin);
        }
        return total;
    }

    /** 판매 슬롯의 마리 수 합산. */
    public static int calcTotalCount(Inventory inv) {
        int count = 0;
        for (int slot : SELL_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it != null && FishItem.isFish(it)) count += it.getAmount();
        }
        return count;
    }

    /** 바구니 아이템 lore 갱신 — 현재 판매 슬롯 합계 표시. */
    public static void updateBasketPreview(Inventory inv, Season season, FishingPlugin plugin) {
        ItemStack basket = inv.getItem(SLOT_BASKET);
        if (basket == null || basket.getType() != Material.COD_BUCKET) return;
        int count = calcTotalCount(inv);
        long total = calcTotalPrice(inv, season, plugin);
        basket.editMeta(meta -> meta.lore(List.of(
                Component.text("클릭 : ", NamedTextColor.WHITE)
                        .append(Component.text("[ 전체 판매 ]", NamedTextColor.RED))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("올려둔 물고기 : ", NamedTextColor.GRAY)
                        .append(Component.text(count + " 마리", NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("예상 판매가 : ", NamedTextColor.GRAY)
                        .append(Component.text(String.format("%,d", total) + "w",
                                count > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY))
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("현재 계절 : ", NamedTextColor.GRAY)
                        .append(Component.text(season.korean(), NamedTextColor.AQUA))
                        .decoration(TextDecoration.ITALIC, false)
        )));
        inv.setItem(SLOT_BASKET, basket);
    }

    /**
     * 전체 판매 실행. 판매 슬롯의 모든 물고기를 합산하고 울캐쉬 입금.
     * @return 판매된 물고기 총 마리수 (0이면 판매 실패)
     */
    public static SellResult sellAll(Player player, Inventory inv, Season season, FishingPlugin plugin) {
        double grand = 0.0;
        int count = 0;
        String firstName = null;

        double cmWeight = plugin.getConfig().getDouble("price.cm-weight", 0.3);
        double gramWeight = plugin.getConfig().getDouble("price.gram-weight", 0.15);
        double inMul = plugin.getConfig().getDouble("price.in-season-multiplier", 1.5);
        double offMul = plugin.getConfig().getDouble("price.off-season-multiplier", 0.5);
        double premiumMul = plugin.getConfig().getDouble("price.premium-multiplier", 2.0);

        List<FishSpecies> inSeason = FishRegistry.inSeason(season);
        List<FishSpecies> offSeason = FishRegistry.offSeason(season);

        for (int slot : SELL_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it == null || !FishItem.isFish(it)) continue;

            FishSpecies sp = FishItem.getSpecies(it);
            if (sp == null) continue;

            double mul;
            if (inSeason.contains(sp)) mul = inMul;
            else if (offSeason.contains(sp)) mul = offMul;
            else mul = 1.0;

            int cm = FishItem.getLength(it);
            int g = FishItem.getMass(it);
            boolean premium = FishItem.isPremium(it);

            double perFish = (cm * cmWeight + g * gramWeight) * mul;
            if (premium) perFish *= premiumMul;

            int amount = it.getAmount();
            grand += perFish * amount;
            count += amount;
            if (firstName == null) firstName = sp.koreanName();

            inv.setItem(slot, null);
        }

        long finalAmount = Math.round(grand);
        boolean paid = finalAmount > 0 && doPayout(player, finalAmount);
        if (paid) recordStats(player, finalAmount, count);
        return new SellResult(count, finalAmount, firstName, paid);
    }

    /**
     * 보관함 일괄 판매. ItemStack[] 배열을 받아 동일 가격 로직으로 판매 + 입금.
     * 비-fish 아이템은 player 인벤으로 반환.
     */
    public static SellResult sellAllFromArray(Player player, ItemStack[] items, Season season, FishingPlugin plugin) {
        double grand = 0.0;
        int count = 0;
        String firstName = null;

        double cmWeight = plugin.getConfig().getDouble("price.cm-weight", 0.3);
        double gramWeight = plugin.getConfig().getDouble("price.gram-weight", 0.15);
        double inMul = plugin.getConfig().getDouble("price.in-season-multiplier", 1.5);
        double offMul = plugin.getConfig().getDouble("price.off-season-multiplier", 0.5);
        double premiumMul = plugin.getConfig().getDouble("price.premium-multiplier", 2.0);

        List<FishSpecies> inSeason = FishRegistry.inSeason(season);
        List<FishSpecies> offSeason = FishRegistry.offSeason(season);

        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (!FishItem.isFish(it)) {
                // non-fish — 인벤 반환
                var leftover = player.getInventory().addItem(it);
                if (!leftover.isEmpty()) leftover.values().forEach(rest ->
                        player.getWorld().dropItemNaturally(player.getLocation(), rest));
                continue;
            }
            FishSpecies sp = FishItem.getSpecies(it);
            if (sp == null) continue;

            double mul;
            if (inSeason.contains(sp)) mul = inMul;
            else if (offSeason.contains(sp)) mul = offMul;
            else mul = 1.0;

            int cm = FishItem.getLength(it);
            int g = FishItem.getMass(it);
            boolean premium = FishItem.isPremium(it);

            double perFish = (cm * cmWeight + g * gramWeight) * mul;
            if (premium) perFish *= premiumMul;

            int amount = it.getAmount();
            grand += perFish * amount;
            count += amount;
            if (firstName == null) firstName = sp.koreanName();
        }

        long finalAmount = Math.round(grand);
        boolean paid = finalAmount > 0 && doPayout(player, finalAmount);
        if (paid) recordStats(player, finalAmount, count);
        return new SellResult(count, finalAmount, firstName, paid);
    }

    /** Shop 의 일일 통계로 fish sell 기록 (선택적 — Shop 미로드 시 noop). */
    private static void recordStats(Player player, long revenue, int count) {
        try {
            com.exit.core.api.ShopStatsRecorder rec =
                    com.exit.core.registry.ServiceRegistry.get(com.exit.core.api.ShopStatsRecorder.class)
                            .orElse(null);
            if (rec != null) rec.recordFishSell(player.getUniqueId(), revenue, count);
        } catch (NoClassDefFoundError ignored) {
            // Core 구버전 (ShopStatsRecorder 없음) — 무시
        }
    }

    public static int bulkStorageSlot() { return SLOT_BULK_STORAGE; }
    public static int bulkSeasonalSlot() { return SLOT_BULK_SEASONAL; }

    /**
     * 보관함의 현재 계절 물고기만 합산한 미리보기 금액.
     * 비매칭/비-fish 는 스킵. 보관함 미설치 → -1.
     */
    private static long previewStorageInSeasonTotal(Player player, Season season, FishingPlugin plugin) {
        var storage = plugin.getFishStorageManager();
        if (storage == null) return -1;
        ItemStack[] items = storage.load(player.getUniqueId());

        double cmWeight = plugin.getConfig().getDouble("price.cm-weight", 0.3);
        double gramWeight = plugin.getConfig().getDouble("price.gram-weight", 0.15);
        double inMul = plugin.getConfig().getDouble("price.in-season-multiplier", 1.5);
        double premiumMul = plugin.getConfig().getDouble("price.premium-multiplier", 2.0);

        List<FishSpecies> inSeason = FishRegistry.inSeason(season);

        double grand = 0;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (!FishItem.isFish(it)) continue;
            FishSpecies sp = FishItem.getSpecies(it);
            if (sp == null || !inSeason.contains(sp)) continue;
            int cm = FishItem.getLength(it);
            int g = FishItem.getMass(it);
            boolean premium = FishItem.isPremium(it);
            double perFish = (cm * cmWeight + g * gramWeight) * inMul;
            if (premium) perFish *= premiumMul;
            grand += perFish * it.getAmount();
        }
        return Math.round(grand);
    }

    /**
     * 어부 보관함의 현재 내용을 sellAllFromArray 와 같은 공식으로 미리 합산.
     * 보관함 데이터는 건드리지 않음 (peek only).
     * 보관함 시스템 미설치 / 비어있음 → 0 또는 -1.
     */
    private static long previewStorageTotal(Player player, Season season, FishingPlugin plugin) {
        var storage = plugin.getFishStorageManager();
        if (storage == null) return -1;
        ItemStack[] items = storage.load(player.getUniqueId());

        double cmWeight = plugin.getConfig().getDouble("price.cm-weight", 0.3);
        double gramWeight = plugin.getConfig().getDouble("price.gram-weight", 0.15);
        double inMul = plugin.getConfig().getDouble("price.in-season-multiplier", 1.5);
        double offMul = plugin.getConfig().getDouble("price.off-season-multiplier", 0.5);
        double premiumMul = plugin.getConfig().getDouble("price.premium-multiplier", 2.0);

        List<FishSpecies> inSeason = FishRegistry.inSeason(season);
        List<FishSpecies> offSeason = FishRegistry.offSeason(season);

        double grand = 0;
        for (ItemStack it : items) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (!FishItem.isFish(it)) continue;
            FishSpecies sp = FishItem.getSpecies(it);
            if (sp == null) continue;
            double mul = inSeason.contains(sp) ? inMul
                    : offSeason.contains(sp) ? offMul : 1.0;
            int cm = FishItem.getLength(it);
            int g = FishItem.getMass(it);
            boolean premium = FishItem.isPremium(it);
            double perFish = (cm * cmWeight + g * gramWeight) * mul;
            if (premium) perFish *= premiumMul;
            grand += perFish * it.getAmount();
        }
        return Math.round(grand);
    }

    private static boolean doPayout(Player player, long amount) {
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            player.sendMessage(Component.text("경제 시스템을 불러올 수 없습니다.", NamedTextColor.RED));
            return false;
        }
        return eco.addBalance(player.getUniqueId(), amount);
    }

    public record SellResult(int count, long amount, String firstFishName, boolean paid) {}

    /** Inventory holder 로 GUI 인스턴스 식별 (타이틀 비교보다 안전) */
    public static class FishShopHolder implements org.bukkit.inventory.InventoryHolder {
        private final Season openedAt;
        public FishShopHolder(Season openedAt) { this.openedAt = openedAt; }
        public Season season() { return openedAt; }
        @Override public Inventory getInventory() { return null; }
    }

    /** 판매 슬롯 내용을 플레이어 인벤토리로 반환 (GUI 닫을 때) */
    public static void returnItems(Player player, Inventory inv) {
        for (int slot : SELL_SLOTS) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it.getType() == Material.AIR) continue;
            // 플레이어 인벤토리에 넣고, 넘치면 월드에 드랍
            var leftover = player.getInventory().addItem(it);
            leftover.values().forEach(drop ->
                    player.getWorld().dropItem(player.getLocation(), drop));
            inv.setItem(slot, null);
        }
    }

    // 단순 helper
    @SuppressWarnings("unused")
    private static UUID uuid(Player p) { return p.getUniqueId(); }
}
