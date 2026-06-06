package com.exit.shop.price;

import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 시세 변동 엔진 (1.4.0~).
 *
 * <p><b>가격 공식</b>
 * <ul>
 *   <li>판매가 (sellable): currentSellPrice = sellPriceBase × (1 + fluctuation),  fluctuation ∈ [-0.5, +9.0]</li>
 *   <li>구매가 (sellable): currentBuyPrice = currentSellPrice × item.buyMultiplier</li>
 *   <li>구매가 (buy-only): buyPriceFixed (변동 없음)</li>
 * </ul>
 *
 * <p><b>변동 알고리즘 — 3-way 굴림 (매 갱신, 아이템 독립)</b>
 * <ul>
 *   <li><b>NORMAL</b>: fluct += demand + random + gravity (누적식)
 *     <ul>
 *       <li>demand: 거래량 비대칭(구매 우세=↑, 판매 우세=↓), 가중치 ±20%</li>
 *       <li>random: 균등 [-0.3, +0.3]</li>
 *       <li>gravity: fluct > +1.0 시 -k×(fluct-1.0)^1.5 (고점일수록 강한 하락 압력)</li>
 *     </ul>
 *   </li>
 *   <li><b>BOOM</b>: fluct += [+0.5, +1.0] (떡상)</li>
 *   <li><b>CRASH</b>: fluct = (1+fluct)×0.5 - 1 (어제 가격의 50%로 곱셈 폭락)</li>
 * </ul>
 *
 * <p><b>누적 확률 (아이템별 boomStreak 상태)</b>
 * <ul>
 *   <li>기본: BOOM 30% / CRASH 15% / NORMAL 55%</li>
 *   <li>BOOM 1회마다 boomStreak++ → BOOM/CRASH 둘 다 +5% (streak 5에서 cap)</li>
 *   <li>CRASH 발동 시 boomStreak 리셋 → 기본 확률 복귀</li>
 *   <li>NORMAL 발동 + fluct ≥ +3.0: boomStreak -1 (고점에서의 cooldown)</li>
 *   <li>bullCapMode (fluct ≥ +9.0 도달 후): BOOM 0% / CRASH 70% / NORMAL 30%, fluct ≤ +4.0 도달 시 해제</li>
 * </ul>
 *
 * <p><b>갱신 주기</b>: 실시간 10분 (게임시간 12000틱).
 */
public class PriceManager {

    // ─── 상수 ───

    /** 시세 갱신 주기 (게임 틱). 12000 = 실시간 10분. */
    private static final long UPDATE_INTERVAL_TICKS = 12000L;

    // NORMAL 모드 파라미터
    private static final double RANDOM_RANGE      = 0.30;
    private static final double DEMAND_WEIGHT     = 0.20;
    private static final double GRAVITY_THRESHOLD = 1.0;
    private static final double GRAVITY_COEFF     = 0.01;
    private static final double GRAVITY_EXPONENT  = 1.5;

    // BOOM / CRASH 파라미터
    private static final double BOOM_DELTA_MIN  = 0.5;
    private static final double BOOM_DELTA_MAX  = 1.0;
    private static final double CRASH_MULTIPLIER = 0.5;     // 가격이 절반으로

    // 확률 (기본)
    private static final double BOOM_PROB_BASE   = 0.30;
    private static final double CRASH_PROB_BASE  = 0.15;
    private static final double STREAK_BOOST     = 0.05;    // streak 1당 BOOM/CRASH 각 +5%
    private static final int    STREAK_CAP       = 5;
    private static final double BOOM_PROB_CAP    = 0.55;
    private static final double CRASH_PROB_CAP   = 0.40;
    /** NORMAL 발동 시 fluct 가 이 값 이상이면 boomStreak 자연 감소 (고점 cooldown). */
    private static final double STREAK_DECAY_THRESHOLD = 3.0;

    // bullCap 모드 (10배 도달 후)
    private static final double BULLCAP_BOOM_PROB  = 0.00;
    private static final double BULLCAP_CRASH_PROB = 0.70;
    private static final double BULLCAP_TRIGGER    = 9.0;   // fluct ≥ 이 값 → 진입
    private static final double BULLCAP_RELEASE    = 4.0;   // fluct ≤ 이 값 → 해제

    // Clamp
    private static final double UPPER_CLAMP = 9.0;
    private static final double LOWER_CLAMP = -0.5;

    // 사전 알림 임계 (다음 갱신까지 남은 ticks)
    private static final long WARN_5MIN_TICKS = 3000;   // 5분 = 3000틱
    private static final long WARN_1MIN_TICKS = 600;    // 1분 = 600틱

    // ─── 필드 ───

    private final JavaPlugin plugin;
    private final ShopItemRegistry registry;

    private final Map<String, Double> fluctuations = new ConcurrentHashMap<>();
    private final Map<String, Long> todaySellVolume = new ConcurrentHashMap<>();
    private final Map<String, Long> todayBuyVolume = new ConcurrentHashMap<>();

    /** 누적 확률용 — 아이템별 마지막 CRASH 이후 BOOM 발동 횟수 (0..STREAK_CAP). */
    private final Map<String, Integer> boomStreak = new ConcurrentHashMap<>();
    /** 아이템별 bullCap 모드 (fluct ≥ BULLCAP_TRIGGER 도달 후, BULLCAP_RELEASE 까지 유지). */
    private final Map<String, Boolean> bullCapMode = new ConcurrentHashMap<>();

    /** itemId → 다음 trigger 까지의 chunk 누적량. trigger 발동 시 threshold 만큼 차감. */
    private final Map<String, Long> chunkSellVolume = new ConcurrentHashMap<>();
    /** itemId → 현재 chunk 의 플레이어별 기여량. trigger 발동 시 clear. */
    private final Map<String, Map<UUID, Long>> chunkPlayerVolume = new ConcurrentHashMap<>();

    /** 다음 갱신이 발화할 절대 게임틱(fullTime). -1 = 미초기화 → 첫 tick() 에서 align. */
    private long nextUpdateTick = -1;
    private boolean warned5min = false;
    private boolean warned1min = false;
    /** 발화 카운터 (로그/디버그용, save 에도 기록). */
    private long updateCount = 0;
    private final Random random = new Random();
    private final File dataFile;

    public PriceManager(JavaPlugin plugin, ShopItemRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.dataFile = new File(plugin.getDataFolder(), "prices.dat");
        load();
    }

    // ─── 가격 조회 ───

    public long getBuyPrice(ShopItem item) {
        if (!item.isBuyable()) return 0;
        if (item.isSellable()) {
            long sell = getSellPrice(item);
            return Math.max(1, Math.round(sell * item.getBuyMultiplier()));
        }
        return item.getBuyPriceFixed();
    }

    public long getSellPrice(ShopItem item) {
        if (!item.isSellable()) return 0;
        double fluct = fluctuations.getOrDefault(item.getId(), 0.0);
        long price = Math.round(item.getSellPriceBase() * (1.0 + fluct));
        return Math.max(1, price);
    }

    public String getFluctuationDisplay(ShopItem item) {
        if (!item.isSellable()) return "§7-";
        double fluct = fluctuations.getOrDefault(item.getId(), 0.0);
        int percent = (int) Math.round(fluct * 100);
        if (percent >= 0) return "§a▲ +" + percent + "%";
        else return "§c▼ " + percent + "%";
    }

    // ─── 거래량 기록 ───

    public void recordSell(String itemId, long amount) {
        todaySellVolume.merge(itemId, amount, Long::sum);
    }

    public void recordBuy(String itemId, long amount) {
        todayBuyVolume.merge(itemId, amount, Long::sum);
    }

    // ─── 갱신 주기 ───

    public void tick() {
        long fullTime = Bukkit.getWorlds().get(0).getFullTime();

        // 최초 초기화 — 현재 시각 기준 다음 period 경계로 정렬
        if (nextUpdateTick < 0) {
            nextUpdateTick = ((fullTime / UPDATE_INTERVAL_TICKS) + 1) * UPDATE_INTERVAL_TICKS;
            warned5min = false;
            warned1min = false;
            save();
            return;
        }

        // 발화 시점 도달 → 갱신 + 다음 시점 += 10분
        if (fullTime >= nextUpdateTick) {
            recalculatePrices();
            broadcastUpdate();
            nextUpdateTick += UPDATE_INTERVAL_TICKS;
            warned5min = false;
            warned1min = false;
            save();
            return;
        }

        long ticksLeft = nextUpdateTick - fullTime;
        if (!warned1min && ticksLeft <= WARN_1MIN_TICKS) {
            broadcastWarning("§e1분");
            warned1min = true;
            warned5min = true;
        } else if (!warned5min && ticksLeft <= WARN_5MIN_TICKS) {
            broadcastWarning("§e5분");
            warned5min = true;
        }
    }

    /**
     * 관리자용 — 다음 시세 갱신을 지정한 ticks 뒤로 예약.
     * 이후 자연 사이클은 그 시점부터 {@link #UPDATE_INTERVAL_TICKS} 간격으로 자동 유지됨.
     *
     * @param delayTicks 현재로부터 몇 게임틱 뒤 (≥0). 0 이면 다음 tick() 에서 즉시 발화.
     */
    public void scheduleNextUpdate(long delayTicks) {
        long fullTime = Bukkit.getWorlds().get(0).getFullTime();
        nextUpdateTick = fullTime + Math.max(0, delayTicks);
        warned5min = false;
        warned1min = false;
        save();
    }

    /** 다음 갱신까지 남은 ticks. 미초기화 시 -1. */
    public long getTicksUntilNextUpdate() {
        if (nextUpdateTick < 0) return -1;
        long fullTime = Bukkit.getWorlds().get(0).getFullTime();
        return Math.max(0, nextUpdateTick - fullTime);
    }

    private void broadcastWarning(String remaining) {
        Bukkit.broadcast(Component.text("[시세 알림] ", NamedTextColor.GOLD)
                .append(Component.text("시세 변동까지 ", NamedTextColor.WHITE))
                .append(Component.text(remaining))
                .append(Component.text(" 남았습니다.", NamedTextColor.WHITE)));
    }

    private void broadcastUpdate() {
        Bukkit.broadcast(Component.text("[시세 알림] ", NamedTextColor.GOLD)
                .append(Component.text("새로운 시세가 적용되었습니다.", NamedTextColor.AQUA)));
    }

    private void broadcastBullCap(ShopItem item) {
        Bukkit.broadcast(Component.text("[시세] ", NamedTextColor.GOLD)
                .append(Component.text(item.getDisplayName(), NamedTextColor.GOLD))
                .append(Component.text(" 이(가) 사상 최고가 도달!", NamedTextColor.YELLOW)));
    }

    // ─── 핵심 알고리즘 ───

    private enum Outcome { NORMAL, BOOM, CRASH }

    private Outcome rollOutcome(String id) {
        double boomP, crashP;
        if (bullCapMode.getOrDefault(id, false)) {
            boomP  = BULLCAP_BOOM_PROB;
            crashP = BULLCAP_CRASH_PROB;
        } else {
            int streak = boomStreak.getOrDefault(id, 0);
            double boost = STREAK_BOOST * streak;
            boomP  = Math.min(BOOM_PROB_BASE  + boost, BOOM_PROB_CAP);
            crashP = Math.min(CRASH_PROB_BASE + boost, CRASH_PROB_CAP);
        }
        double roll = random.nextDouble();
        if (roll < boomP) return Outcome.BOOM;
        if (roll < boomP + crashP) return Outcome.CRASH;
        return Outcome.NORMAL;
    }

    private void recalculatePrices() {
        for (ShopItem item : registry.getAll()) {
            if (!item.isSellable()) continue;

            String id = item.getId();
            double prev = fluctuations.getOrDefault(id, 0.0);
            Outcome outcome = rollOutcome(id);

            double newFluct;
            switch (outcome) {
                case BOOM -> {
                    double delta = BOOM_DELTA_MIN + random.nextDouble() * (BOOM_DELTA_MAX - BOOM_DELTA_MIN);
                    newFluct = prev + delta;
                }
                case CRASH -> {
                    newFluct = (1.0 + prev) * CRASH_MULTIPLIER - 1.0;
                }
                default -> {
                    long sold = todaySellVolume.getOrDefault(id, 0L);
                    long bought = todayBuyVolume.getOrDefault(id, 0L);
                    double demandFactor = 0.0;
                    if (sold > 0 || bought > 0) {
                        double ratio = (bought - sold) / (double) Math.max(sold + bought, 1);
                        demandFactor = ratio * DEMAND_WEIGHT;
                    }
                    double randomFactor = (random.nextDouble() - 0.5) * 2.0 * RANDOM_RANGE;
                    double gravity = 0.0;
                    if (prev > GRAVITY_THRESHOLD) {
                        gravity = -GRAVITY_COEFF * Math.pow(prev - GRAVITY_THRESHOLD, GRAVITY_EXPONENT);
                    }
                    newFluct = prev + demandFactor + randomFactor + gravity;
                }
            }

            newFluct = Math.max(LOWER_CLAMP, Math.min(UPPER_CLAMP, newFluct));
            fluctuations.put(id, newFluct);

            // 상태 전이
            switch (outcome) {
                case BOOM -> {
                    int newStreak = Math.min(boomStreak.getOrDefault(id, 0) + 1, STREAK_CAP);
                    boomStreak.put(id, newStreak);
                    // BOOM 으로 10배 도달 → bullCapMode 진입
                    if (newFluct >= UPPER_CLAMP - 1e-9 && !bullCapMode.getOrDefault(id, false)) {
                        bullCapMode.put(id, true);
                        boomStreak.put(id, 0);
                        broadcastBullCap(item);
                    }
                }
                case CRASH -> {
                    boomStreak.put(id, 0);
                    bullCapMode.put(id, false);
                }
                default -> {
                    // NORMAL — 고점(fluct ≥ 3.0)에선 streak 자연 감소 (BOOM 연쇄 후 cooldown)
                    if (newFluct >= STREAK_DECAY_THRESHOLD) {
                        int s = boomStreak.getOrDefault(id, 0);
                        if (s > 0) boomStreak.put(id, s - 1);
                    }
                    // bullCap 인 상태에서 NORMAL drift 로 fluct ≤ 4.0 하강 시 해제.
                    if (bullCapMode.getOrDefault(id, false) && newFluct <= BULLCAP_RELEASE) {
                        bullCapMode.put(id, false);
                    }
                }
            }

            // CRASH 결과로 fluct 가 4.0 이하로 떨어지는 경우도 해제 (CRASH case 에서 bullCap=false 처리됐지만 명시적 일관성 확인)
            if (outcome == Outcome.CRASH && newFluct <= BULLCAP_RELEASE) {
                bullCapMode.put(id, false);
            }
        }

        todaySellVolume.clear();
        todayBuyVolume.clear();
        chunkSellVolume.clear();
        chunkPlayerVolume.clear();

        updateCount++;
        plugin.getLogger().info("[Shop] 시세 갱신 완료. count=" + updateCount);
    }

    // ─── 영속화 ───

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            Properties props = new Properties();
            props.setProperty("nextUpdateTick", String.valueOf(nextUpdateTick));
            props.setProperty("updateCount", String.valueOf(updateCount));
            for (var e : fluctuations.entrySet())   props.setProperty("fluct." + e.getKey(),       e.getValue().toString());
            for (var e : todaySellVolume.entrySet())props.setProperty("sell." + e.getKey(),        e.getValue().toString());
            for (var e : todayBuyVolume.entrySet()) props.setProperty("buy." + e.getKey(),         e.getValue().toString());
            for (var e : boomStreak.entrySet())     props.setProperty("boom_streak." + e.getKey(), e.getValue().toString());
            for (var e : bullCapMode.entrySet())    props.setProperty("bullcap." + e.getKey(),     e.getValue().toString());
            try (OutputStream os = new FileOutputStream(dataFile)) {
                props.store(os, "Shop Price Data");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "시세 데이터 저장 실패", e);
        }
    }

    public void load() {
        if (!dataFile.exists()) return;
        try {
            Properties props = new Properties();
            try (InputStream is = new FileInputStream(dataFile)) {
                props.load(is);
            }
            nextUpdateTick = Long.parseLong(props.getProperty("nextUpdateTick", "-1"));
            updateCount    = Long.parseLong(props.getProperty("updateCount", "0"));
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("fluct.")) {
                    fluctuations.put(key.substring(6), Double.parseDouble(props.getProperty(key)));
                } else if (key.startsWith("sell.")) {
                    todaySellVolume.put(key.substring(5), Long.parseLong(props.getProperty(key)));
                } else if (key.startsWith("buy.")) {
                    todayBuyVolume.put(key.substring(4), Long.parseLong(props.getProperty(key)));
                } else if (key.startsWith("boom_streak.")) {
                    boomStreak.put(key.substring(12), Integer.parseInt(props.getProperty(key)));
                } else if (key.startsWith("bullcap.")) {
                    bullCapMode.put(key.substring(8), Boolean.parseBoolean(props.getProperty(key)));
                }
            }
        } catch (IOException | NumberFormatException e) {
            plugin.getLogger().log(Level.WARNING, "시세 데이터 로드 실패", e);
        }
    }

    public Map<String, Double> getFluctuationMap() {
        return Collections.unmodifiableMap(fluctuations);
    }

    /**
     * 관리자용 — 시세 변동 n회 강제 진행.
     *
     * <p>실제 갱신 주기와 무관하게 즉시 n회 {@link #recalculatePrices()} 호출.
     * 첫 회는 누적된 거래량(demand)을 사용하고, 2회차부터는 demand=0 (clear 됨).
     * {@code lastUpdatePeriod} 도 n 만큼 증가시켜 다음 자연 갱신 시점이 어긋나지 않게 함.
     *
     * @param n 진행할 회차 (≥1)
     */
    public void forceAdvance(int n) {
        for (int i = 0; i < n; i++) {
            recalculatePrices();
        }
        save();
    }

    // ─── 즉시 폭락 트리거 (한 타임 누적 판매 기반) ───

    /**
     * 폭락 트리거 결과.
     * - deterministicCount: thresholdHigh 정수배 도달로 인한 확정 폭락 (top seller 표기)
     * - probabilisticCount: low~high 구간 확률 굴림 결과 (0 또는 1, top seller 표기 X)
     */
    public record TriggerResult(int deterministicCount, int probabilisticCount, UUID topSeller) {
        public static final TriggerResult NONE = new TriggerResult(0, 0, null);
        public int total() { return deterministicCount + probabilisticCount; }
    }

    /**
     * 누적 판매량 기반 폭락 트리거.
     * - chunkAfter ≥ thresholdHigh: 정수배만큼 결정적 폭락
     * - thresholdLow ≤ chunkAfter < thresholdHigh: 선형 확률 1회 굴림 (low=0%, high=100%)
     */
    public TriggerResult recordSellAndCheckTrigger(String itemId, UUID seller, long amount,
                                                   long thresholdLow, long thresholdHigh) {
        todaySellVolume.merge(itemId, amount, Long::sum);

        long chunkAfter = chunkSellVolume.getOrDefault(itemId, 0L) + amount;
        Map<UUID, Long> contrib = chunkPlayerVolume.computeIfAbsent(itemId, k -> new HashMap<>());
        contrib.merge(seller, amount, Long::sum);

        int deterministic = 0;
        int probabilistic = 0;
        long remaining = chunkAfter;

        while (remaining >= thresholdHigh) {
            deterministic++;
            remaining -= thresholdHigh;
        }
        if (remaining >= thresholdLow && thresholdHigh > thresholdLow) {
            double p = (double) (remaining - thresholdLow) / (double) (thresholdHigh - thresholdLow);
            if (ThreadLocalRandom.current().nextDouble() < p) {
                probabilistic = 1;
                remaining = 0;
            }
        }

        if (deterministic == 0 && probabilistic == 0) {
            chunkSellVolume.put(itemId, chunkAfter);
            return TriggerResult.NONE;
        }

        UUID top = contrib.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(seller);

        chunkSellVolume.put(itemId, remaining);
        contrib.clear();

        return new TriggerResult(deterministic, probabilistic, top);
    }

    /**
     * 현재 fluctuation 에 multiplier 곱해서 즉시 인하.
     * 예: multiplier=0.75 → 새 가격 = 현재 가격 × 0.75
     *
     * @return 변동 전 fluct (알림 용)
     */
    public double triggerCrash(String itemId, double multiplier) {
        double prev = fluctuations.getOrDefault(itemId, 0.0);
        double newFluct = (1.0 + prev) * multiplier - 1.0;
        newFluct = Math.max(-0.95, Math.min(UPPER_CLAMP, newFluct));
        fluctuations.put(itemId, newFluct);
        return prev;
    }
}
