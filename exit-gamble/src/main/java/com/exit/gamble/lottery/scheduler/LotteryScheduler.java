package com.exit.gamble.lottery.scheduler;

import com.exit.gamble.lottery.LotteryManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class LotteryScheduler {

    private static final long HOUR_MS = 3_600_000L;
    private static final long TICK_MS = 50L;

    private final Plugin plugin;
    private final LotteryManager manager;
    private BukkitTask task;

    public LotteryScheduler(Plugin plugin, LotteryManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        scheduleNext();
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void scheduleNext() {
        long now = System.currentTimeMillis();
        long nextHourMs = ((now / HOUR_MS) + 1) * HOUR_MS;
        long delayMs = nextHourMs - now;
        long delayTicks = Math.max(1L, delayMs / TICK_MS);

        long minutes = delayMs / 60_000L;
        long seconds = (delayMs / 1000L) % 60L;
        plugin.getLogger().info(String.format("[Lottery] 다음 추첨까지 %d분 %d초", minutes, seconds));

        task = Bukkit.getScheduler().runTaskLater(plugin, this::runDraw, delayTicks);
    }

    private void runDraw() {
        try {
            manager.draw();
        } catch (Exception e) {
            plugin.getLogger().severe("[Lottery] 추첨 중 예외: " + e.getMessage());
            e.printStackTrace();
        }
        // 다음 정각 예약 (재귀 X, 그냥 runTaskLater 재호출)
        scheduleNext();
    }

    /** 다음 정각까지 남은 ms (UI 표시용). */
    public static long msUntilNextHour() {
        long now = System.currentTimeMillis();
        long nextHourMs = ((now / HOUR_MS) + 1) * HOUR_MS;
        return nextHourMs - now;
    }
}
