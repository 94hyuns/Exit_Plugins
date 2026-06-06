package com.exit.gamble.lottery;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.lottery.config.LotteryConfig;
import com.exit.gamble.stats.GambleStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class LotteryManager {

    private static final String DATA_FILE = "lottery.yml";
    private static final int HISTORY_MAX = 20;

    private final Plugin plugin;
    private final LotteryConfig config;
    private final GambleStatsManager stats;
    private final File file;

    private long pot = 0;
    private long totalDraws = 0;
    private int rolloverCount = 0;                       // 통계용 연속 이월 횟수 (당첨 시 0)
    private final List<Ticket> tickets = new ArrayList<>();
    private final Map<UUID, Integer> boughtThisCycle = new HashMap<>();
    /** 1.5.0+: 플레이어별 누적 보너스 카운트 (이번 회차 구매자만 +1). 당첨 시 전체 clear. */
    private final Map<UUID, Integer> playerBonusCount = new HashMap<>();
    private final LinkedList<DrawHistory> history = new LinkedList<>();

    public LotteryManager(Plugin plugin, LotteryConfig config, GambleStatsManager stats) {
        this.plugin = plugin;
        this.config = config;
        this.stats = stats;
        this.file = new File(plugin.getDataFolder(), DATA_FILE);
    }

    public void load() {
        if (!file.exists()) {
            pot = config.initialPotSeed();
            save();
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        pot = cfg.getLong("pot", config.initialPotSeed());
        totalDraws = cfg.getLong("total-draws", 0);
        rolloverCount = cfg.getInt("rollover-count", 0);

        boughtThisCycle.clear();
        var bsec = cfg.getConfigurationSection("bought-this-cycle");
        if (bsec != null) {
            for (String key : bsec.getKeys(false)) {
                try { boughtThisCycle.put(UUID.fromString(key), bsec.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }

        playerBonusCount.clear();
        var psec = cfg.getConfigurationSection("player-bonus-count");
        if (psec != null) {
            for (String key : psec.getKeys(false)) {
                try { playerBonusCount.put(UUID.fromString(key), psec.getInt(key)); }
                catch (IllegalArgumentException ignored) {}
            }
        }

        tickets.clear();
        List<Map<?, ?>> ticketMaps = cfg.getMapList("tickets");
        for (Map<?, ?> raw : ticketMaps) {
            try {
                Map<String, Object> m = asStringMap(raw);
                UUID owner = UUID.fromString(String.valueOf(m.get("owner")));
                Object ownerNameObj = m.get("ownerName");
                String ownerName = ownerNameObj == null ? "?" : String.valueOf(ownerNameObj);
                int number = ((Number) m.get("number")).intValue();
                Object ptObj = m.get("purchaseTime");
                long pt = ptObj instanceof Number n ? n.longValue() : 0L;
                tickets.add(new Ticket(owner, ownerName, number, pt));
            } catch (Exception e) {
                plugin.getLogger().warning("[Lottery] 티켓 파싱 실패: " + raw);
            }
        }

        history.clear();
        List<Map<?, ?>> histList = cfg.getMapList("history-list");
        for (Map<?, ?> raw : histList) loadHistoryEntry(asStringMap(raw));
        plugin.getLogger().info("[Lottery] 로드: 티켓 " + tickets.size() + "장, 현재 풀 " + pot + "원, 누적 추첨 " + totalDraws + "회");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private void loadHistoryEntry(Map<String, Object> m) {
        try {
            long drawTime = ((Number) m.get("drawTime")).longValue();
            int winNum = ((Number) m.get("winningNumber")).intValue();
            long pot0 = ((Number) m.get("potBeforeDraw")).longValue();
            Object winnersObj = m.get("winnerNames");
            List<String> winners = winnersObj instanceof List ? (List<String>) winnersObj : List.of();
            Object perPersonObj = m.get("perPersonPayout");
            long perPerson = perPersonObj instanceof Number n ? n.longValue() : 0L;
            history.add(new DrawHistory(drawTime, winNum, pot0, winners, perPerson));
        } catch (Exception e) {
            plugin.getLogger().warning("[Lottery] 히스토리 파싱 실패");
        }
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("pot", pot);
        cfg.set("total-draws", totalDraws);
        cfg.set("rollover-count", rolloverCount);

        for (Map.Entry<UUID, Integer> e : boughtThisCycle.entrySet()) {
            cfg.set("bought-this-cycle." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Integer> e : playerBonusCount.entrySet()) {
            cfg.set("player-bonus-count." + e.getKey(), e.getValue());
        }

        List<Map<String, Object>> ticketMaps = new ArrayList<>(tickets.size());
        for (Ticket t : tickets) {
            Map<String, Object> m = new HashMap<>();
            m.put("owner", t.owner().toString());
            m.put("ownerName", t.ownerName());
            m.put("number", t.number());
            m.put("purchaseTime", t.purchaseTime());
            ticketMaps.add(m);
        }
        cfg.set("tickets", ticketMaps);

        List<Map<String, Object>> histMaps = new ArrayList<>(history.size());
        for (DrawHistory h : history) {
            Map<String, Object> m = new HashMap<>();
            m.put("drawTime", h.drawTime());
            m.put("winningNumber", h.winningNumber());
            m.put("potBeforeDraw", h.potBeforeDraw());
            m.put("winnerNames", h.winnerNames());
            m.put("perPersonPayout", h.perPersonPayout());
            histMaps.add(m);
        }
        cfg.set("history-list", histMaps);

        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[Lottery] lottery.yml 저장 실패", e);
        }
    }

    // ─── 티켓 구매 ───

    public PurchaseResult buy(Player player, int count) {
        if (count <= 0) return PurchaseResult.failure("수량은 1 이상");

        int cycleLimit = cycleLimitFor(player.getUniqueId());
        int alreadyBought = boughtThisCycle.getOrDefault(player.getUniqueId(), 0);
        if (alreadyBought + count > cycleLimit) {
            int canBuy = Math.max(0, cycleLimit - alreadyBought);
            return PurchaseResult.failure(
                    "이번 회차 구매 한도 초과 (한도 " + cycleLimit + "장, 이미 " + alreadyBought
                            + "장 구매. 추가 가능 " + canBuy + "장)");
        }

        long totalCost = config.ticketPrice() * count;
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) return PurchaseResult.failure("경제 시스템 미연동");
        if (eco.getBalance(player.getUniqueId()) < totalCost) {
            return PurchaseResult.failure("잔액 부족 (필요: " + String.format("%,d", totalCost) + "원)");
        }
        if (!eco.subtractBalance(player.getUniqueId(), totalCost)) {
            return PurchaseResult.failure("잔액 차감 실패");
        }

        List<Integer> bought = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int number = ThreadLocalRandom.current().nextInt(config.numberRange());
            tickets.add(new Ticket(player.getUniqueId(), player.getName(), number, System.currentTimeMillis()));
            bought.add(number);
        }
        pot += totalCost;
        boughtThisCycle.merge(player.getUniqueId(), count, Integer::sum);
        stats.addLotteryBet(player.getUniqueId(), totalCost);
        save();
        return PurchaseResult.success(bought, totalCost);
    }

    // ─── 추첨 ───

    public DrawHistory draw() {
        totalDraws++;

        // 매 회차 시드 머니 적립 (판매액과 별개)
        pot += config.seedPerDraw();

        int winningNumber = ThreadLocalRandom.current().nextInt(config.numberRange());
        long potNow = pot;

        List<Ticket> winners = new ArrayList<>();
        for (Ticket t : tickets) {
            if (t.number() == winningNumber) winners.add(t);
        }

        DrawHistory entry;
        if (winners.isEmpty()) {
            // 이월 — 이번 회차에 산 사람들에게만 보너스 +1
            int participants = boughtThisCycle.size();
            for (UUID uuid : boughtThisCycle.keySet()) {
                playerBonusCount.merge(uuid, 1, Integer::sum);
            }
            rolloverCount++;
            boughtThisCycle.clear();
            tickets.clear();  // 1.5.1+: 티켓은 회차 단위. 미당첨이라도 추첨 후 전부 소멸.
            entry = new DrawHistory(System.currentTimeMillis(), winningNumber, potNow, List.of(), 0);
            history.addFirst(entry);
            trimHistory();
            save();
            broadcastNoWinner(winningNumber, potNow, participants);
            return entry;
        }

        // 분배
        long perPerson = potNow / winners.size();
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        long distributed = perPerson * winners.size();
        long remainder = potNow - distributed;

        List<String> winnerNames = new ArrayList<>();
        for (Ticket t : winners) {
            if (eco != null) eco.addBalance(t.owner(), perPerson);
            stats.addLotteryPayout(t.owner(), perPerson);
            winnerNames.add(t.ownerName());
        }
        tickets.clear();  // 1.5.1+: 회차 단위 티켓 — 비당첨 티켓도 함께 소멸
        pot = remainder;

        // 당첨 발생 → 모든 카운터 리셋 (글로벌 + per-player 보너스 + 이번 회차)
        rolloverCount = 0;
        boughtThisCycle.clear();
        playerBonusCount.clear();

        entry = new DrawHistory(System.currentTimeMillis(), winningNumber, potNow, winnerNames, perPerson);
        history.addFirst(entry);
        trimHistory();
        save();
        broadcastWinners(winningNumber, potNow, winnerNames, perPerson);
        return entry;
    }

    private void trimHistory() {
        while (history.size() > HISTORY_MAX) history.removeLast();
    }

    // ─── 브로드캐스트 ───

    private void broadcastNoWinner(int winNum, long potCarried, int participants) {
        Component line = Component.text("─────────────────────────────", NamedTextColor.GRAY);
        Bukkit.broadcast(line);
        Bukkit.broadcast(Component.text("[복권] 정각 추첨 — 당첨 번호: ", NamedTextColor.YELLOW)
                .append(Component.text(formatNumber(winNum), NamedTextColor.GOLD)));
        Bukkit.broadcast(Component.text("       당첨자 없음 → 상금 ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d", potCarried) + "원", NamedTextColor.YELLOW))
                .append(Component.text(" 다음 정각으로 이월", NamedTextColor.GRAY)));
        if (participants > 0) {
            Bukkit.broadcast(Component.text(
                    "       (이번 회차 구매자 " + participants + "명에게 1인 한도 +"
                            + config.maxTicketsBonusPerRollover() + "장 부여, 연속 이월 " + rolloverCount + "회)",
                    NamedTextColor.DARK_GRAY));
        } else {
            Bukkit.broadcast(Component.text(
                    "       (구매자 없음 — 보너스 부여 안 됨, 연속 이월 " + rolloverCount + "회)",
                    NamedTextColor.DARK_GRAY));
        }
        Bukkit.broadcast(line);
    }

    private void broadcastWinners(int winNum, long potBefore, List<String> winnerNames, long perPerson) {
        Component line = Component.text("─────────────────────────────", NamedTextColor.GOLD);
        Bukkit.broadcast(line);
        Bukkit.broadcast(Component.text("[복권] 정각 추첨 — 당첨 번호: ", NamedTextColor.YELLOW)
                .append(Component.text(formatNumber(winNum), NamedTextColor.GOLD)));
        if (winnerNames.size() == 1) {
            Bukkit.broadcast(Component.text("       당첨자: ", NamedTextColor.WHITE)
                    .append(Component.text(winnerNames.get(0), NamedTextColor.AQUA))
                    .append(Component.text("   상금: ", NamedTextColor.WHITE))
                    .append(Component.text(String.format("%,d", perPerson) + "원", NamedTextColor.GOLD)));
        } else {
            Bukkit.broadcast(Component.text("       당첨자 " + winnerNames.size() + "명: ", NamedTextColor.WHITE)
                    .append(Component.text(String.join(", ", winnerNames), NamedTextColor.AQUA)));
            Bukkit.broadcast(Component.text("       1인당 ", NamedTextColor.WHITE)
                    .append(Component.text(String.format("%,d", perPerson) + "원", NamedTextColor.GOLD))
                    .append(Component.text("  (총 " + String.format("%,d", potBefore) + "원 분할)", NamedTextColor.GRAY)));
        }
        Bukkit.broadcast(line);
    }

    private String formatNumber(int n) {
        int digits = String.valueOf(config.numberRange() - 1).length();
        return String.format("%0" + digits + "d", n);
    }

    // ─── Getters / 조회 ───

    public long pot() { return pot; }
    public int totalTickets() { return tickets.size(); }
    public long totalDraws() { return totalDraws; }
    public int numberRange() { return config.numberRange(); }
    public long ticketPrice() { return config.ticketPrice(); }
    public int rolloverCount() { return rolloverCount; }
    public long seedPerDraw() { return config.seedPerDraw(); }
    public int boughtThisCycle(UUID player) { return boughtThisCycle.getOrDefault(player, 0); }
    public int playerBonusCount(UUID player) { return playerBonusCount.getOrDefault(player, 0); }

    /** 1.5.0+: 본인 보너스 카운트 기준 회차 한도. */
    public int cycleLimitFor(UUID player) {
        return config.maxTicketsPerCycle(playerBonusCount(player));
    }

    public int ticketBonusPerRollover() { return config.maxTicketsBonusPerRollover(); }
    public int ticketBaseline() { return config.maxTicketsBaseline(); }

    public List<Ticket> ticketsOf(UUID player) {
        List<Ticket> mine = new ArrayList<>();
        for (Ticket t : tickets) if (t.owner().equals(player)) mine.add(t);
        return mine;
    }

    public List<DrawHistory> recentHistory(int limit) {
        return new ArrayList<>(history.subList(0, Math.min(limit, history.size())));
    }

    public record PurchaseResult(boolean success, String error, List<Integer> numbers, long totalCost) {
        public static PurchaseResult success(List<Integer> nums, long cost) {
            return new PurchaseResult(true, null, nums, cost);
        }
        public static PurchaseResult failure(String err) {
            return new PurchaseResult(false, err, List.of(), 0);
        }
    }
}
