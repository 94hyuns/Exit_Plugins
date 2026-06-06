package com.exit.gamble.lottery.config;

import org.bukkit.plugin.Plugin;

public class LotteryConfig {

    private final Plugin plugin;
    private long ticketPrice = 500;
    private int numberRange = 1000;
    private long seedPerDraw = 60_000;
    private int maxTicketsBaseline = 50;
    private int maxTicketsBonusPerRollover = 20;
    private long initialPotSeed = 0;

    public LotteryConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var root = plugin.getConfig().getConfigurationSection("lottery");
        if (root == null) {
            plugin.getLogger().info("[Lottery] config.yml 에 lottery 섹션 없음 — 기본값 사용");
            return;
        }
        this.ticketPrice = root.getLong("ticket-price", 500);
        this.numberRange = Math.max(10, root.getInt("number-range", 1000));
        this.seedPerDraw = Math.max(0, root.getLong("seed-per-draw", 60_000));
        this.maxTicketsBaseline = Math.max(1, root.getInt("max-tickets-baseline", 50));
        this.maxTicketsBonusPerRollover = Math.max(0, root.getInt("max-tickets-bonus-per-rollover", 20));
        // max-tickets-cap 키는 deprecated (1.5.0 부터 cap 제거, 무제한). 호환성 위해 읽기만 함.
        this.initialPotSeed = Math.max(0, root.getLong("initial-pot-seed", 0));
    }

    public long ticketPrice() { return ticketPrice; }
    public int numberRange() { return numberRange; }
    public long seedPerDraw() { return seedPerDraw; }
    public int maxTicketsBaseline() { return maxTicketsBaseline; }
    public int maxTicketsBonusPerRollover() { return maxTicketsBonusPerRollover; }
    public long initialPotSeed() { return initialPotSeed; }

    /**
     * 개인 보너스 카운트 기준 1인당 회차 한도. baseline + bonus × 본인 카운트.
     * (1.5.0 부터 cap 제거 — 사실상 무제한)
     */
    public int maxTicketsPerCycle(int bonusCount) {
        long limit = (long) maxTicketsBaseline + (long) maxTicketsBonusPerRollover * bonusCount;
        return (int) Math.min(limit, Integer.MAX_VALUE);
    }
}
