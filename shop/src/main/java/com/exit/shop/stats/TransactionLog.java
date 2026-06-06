package com.exit.shop.stats;

import com.exit.core.api.ShopStatsRecorder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 일일 거래 통계 (오늘치 누적). in-memory + 디스크 flush (5분 주기).
 *
 * <p>구조 (in-memory):
 * <ul>
 *   <li>shopBuys : itemId → (uuid → count)</li>
 *   <li>shopSells: itemId → (uuid → count)</li>
 *   <li>shopBuyRevenue : itemId → (uuid → 금액)</li>
 *   <li>shopSellRevenue: itemId → (uuid → 금액)</li>
 *   <li>fishSellRevenue: uuid → 금액</li>
 *   <li>fishSellCount  : uuid → 어획수</li>
 * </ul>
 *
 * <p>영속화: {@code plugins/Shop/transaction_log.yml}. 서버 재시작에도 누적 유지.
 */
public class TransactionLog implements ShopStatsRecorder {

    private final JavaPlugin plugin;
    private final File file;
    private LocalDate periodStart = LocalDate.now();

    private final Map<String, Map<UUID, Long>> shopBuys       = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> shopSells      = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> shopBuyRevenue = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> shopSellRevenue= new ConcurrentHashMap<>();
    private final Map<UUID, Long> fishSellRevenue = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fishSellCount   = new ConcurrentHashMap<>();
    /** 이 기간 동안 한 번이라도 거래에 참여한 플레이어들의 마지막 알려진 이름. */
    private final Map<UUID, String> seenNames = new ConcurrentHashMap<>();

    public TransactionLog(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "transaction_log.yml");
    }

    // ─── 기록 API ───

    public void recordBuy(UUID buyer, String itemId, int count, long revenue) {
        addNested(shopBuys, itemId, buyer, count);
        addNested(shopBuyRevenue, itemId, buyer, revenue);
        rememberName(buyer);
    }

    public void recordSell(UUID seller, String itemId, int count, long revenue) {
        addNested(shopSells, itemId, seller, count);
        addNested(shopSellRevenue, itemId, seller, revenue);
        rememberName(seller);
    }

    @Override
    public void recordFishSell(UUID seller, long revenue, int fishCount) {
        fishSellRevenue.merge(seller, revenue, Long::sum);
        fishSellCount.merge(seller, (long) fishCount, Long::sum);
        rememberName(seller);
    }

    private static void addNested(Map<String, Map<UUID, Long>> m, String key, UUID uuid, long delta) {
        m.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).merge(uuid, delta, Long::sum);
    }

    private void rememberName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name != null) seenNames.put(uuid, name);
    }

    // ─── 조회 (리포트 생성용) ───

    public LocalDate getPeriodStart() { return periodStart; }
    public Map<String, Map<UUID, Long>> getShopBuys()        { return shopBuys; }
    public Map<String, Map<UUID, Long>> getShopSells()       { return shopSells; }
    public Map<String, Map<UUID, Long>> getShopBuyRevenue()  { return shopBuyRevenue; }
    public Map<String, Map<UUID, Long>> getShopSellRevenue() { return shopSellRevenue; }
    public Map<UUID, Long> getFishSellRevenue() { return fishSellRevenue; }
    public Map<UUID, Long> getFishSellCount()   { return fishSellCount; }
    public Map<UUID, String> getSeenNames()     { return seenNames; }

    public boolean isEmpty() {
        return shopBuys.isEmpty() && shopSells.isEmpty() && fishSellRevenue.isEmpty();
    }

    /** 모든 누적 데이터 리셋 + 기간 시작일 갱신. */
    public void resetTo(LocalDate newPeriodStart) {
        shopBuys.clear();
        shopSells.clear();
        shopBuyRevenue.clear();
        shopSellRevenue.clear();
        fishSellRevenue.clear();
        fishSellCount.clear();
        seenNames.clear();
        this.periodStart = newPeriodStart;
        save();
    }

    // ─── 영속화 (YAML) ───

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            y.set("period-start", periodStart.toString());
            writeNested(y, "buys", shopBuys);
            writeNested(y, "sells", shopSells);
            writeNested(y, "buy-revenue", shopBuyRevenue);
            writeNested(y, "sell-revenue", shopSellRevenue);
            writeFlat(y, "fish-revenue", fishSellRevenue);
            writeFlat(y, "fish-count", fishSellCount);
            for (var e : seenNames.entrySet()) {
                y.set("names." + e.getKey(), e.getValue());
            }
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "transaction_log 저장 실패", e);
        }
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        try {
            String s = y.getString("period-start");
            if (s != null) periodStart = LocalDate.parse(s);
        } catch (Exception ignored) {}
        readNested(y, "buys", shopBuys);
        readNested(y, "sells", shopSells);
        readNested(y, "buy-revenue", shopBuyRevenue);
        readNested(y, "sell-revenue", shopSellRevenue);
        readFlat(y, "fish-revenue", fishSellRevenue);
        readFlat(y, "fish-count", fishSellCount);
        ConfigurationSection ns = y.getConfigurationSection("names");
        if (ns != null) for (String key : ns.getKeys(false)) {
            try { seenNames.put(UUID.fromString(key), ns.getString(key, "?")); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    private static void writeNested(YamlConfiguration y, String root,
                                    Map<String, Map<UUID, Long>> data) {
        for (var itemEntry : data.entrySet()) {
            for (var playerEntry : itemEntry.getValue().entrySet()) {
                y.set(root + "." + itemEntry.getKey() + "." + playerEntry.getKey(),
                        playerEntry.getValue());
            }
        }
    }

    private static void writeFlat(YamlConfiguration y, String root, Map<UUID, Long> data) {
        for (var e : data.entrySet()) y.set(root + "." + e.getKey(), e.getValue());
    }

    private static void readNested(YamlConfiguration y, String root,
                                   Map<String, Map<UUID, Long>> target) {
        ConfigurationSection rootSec = y.getConfigurationSection(root);
        if (rootSec == null) return;
        for (String itemId : rootSec.getKeys(false)) {
            ConfigurationSection itemSec = rootSec.getConfigurationSection(itemId);
            if (itemSec == null) continue;
            Map<UUID, Long> inner = new ConcurrentHashMap<>();
            for (String uuidStr : itemSec.getKeys(false)) {
                try {
                    inner.put(UUID.fromString(uuidStr), itemSec.getLong(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!inner.isEmpty()) target.put(itemId, inner);
        }
    }

    private static void readFlat(YamlConfiguration y, String root, Map<UUID, Long> target) {
        ConfigurationSection sec = y.getConfigurationSection(root);
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try { target.put(UUID.fromString(key), sec.getLong(key)); }
            catch (IllegalArgumentException ignored) {}
        }
    }

    // ─── 편의: 정렬된 플레이어별 합산 (리포트 표용) ───

    /** 한 itemId 의 (uuid, count) 쌍을 count 내림차순으로 반환. */
    public List<Map.Entry<UUID, Long>> sortedByCount(Map<String, Map<UUID, Long>> m, String itemId) {
        Map<UUID, Long> inner = m.getOrDefault(itemId, Map.of());
        return inner.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .toList();
    }

    /** flat map 을 value 내림차순으로 반환. */
    public List<Map.Entry<UUID, Long>> sortedFlat(Map<UUID, Long> m) {
        return m.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .toList();
    }

    /** 플레이어 이름 (없으면 uuid 단축형). */
    public String nameOf(UUID uuid) {
        String n = seenNames.get(uuid);
        if (n != null) return n;
        String s = uuid.toString();
        return s.substring(0, 8);
    }

    /** 디버그용 — 전체 키 카운트. */
    public Map<String, Integer> stats() {
        Map<String, Integer> s = new LinkedHashMap<>();
        s.put("buys-items", shopBuys.size());
        s.put("sells-items", shopSells.size());
        s.put("fish-players", fishSellRevenue.size());
        s.put("seen-names", seenNames.size());
        return s;
    }

    // 사용 안 함 (load 후 정리용 placeholder)
    private void unusedSuppress() { new HashMap<>(); }
}
