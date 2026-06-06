package com.exit.shop.command;

import com.exit.shop.price.PriceManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * /시세변동                       — 다음 시세 갱신까지 남은 시간 표시.
 * /시세변동 skip &lt;n&gt;          — 관리자: 시세 변동 n회 강제 진행.
 * /시세변동 schedule &lt;분&gt; [초] — 관리자: 다음 시세 갱신을 지정 시각 뒤로 예약 (이후 10분 사이클 유지).
 *
 * <p>갱신 주기: 게임시간 12000틱 = 실시간 10분.
 */
public class PriceTimerCommand implements CommandExecutor, TabCompleter {

    private static final long UPDATE_INTERVAL_TICKS = 12000L;
    private static final long WARN_5MIN_TICKS = 3000;
    private static final long WARN_1MIN_TICKS = 600;
    private static final int  MAX_SKIP = 1000;

    private final PriceManager priceManager;

    public PriceTimerCommand(PriceManager priceManager) {
        this.priceManager = priceManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "skip"     -> { return handleSkip(sender, args); }
                case "schedule" -> { return handleSchedule(sender, args); }
            }
        }
        return handleTimer(sender);
    }

    private boolean handleTimer(CommandSender sender) {
        long ticksLeft = priceManager.getTicksUntilNextUpdate();
        if (ticksLeft < 0) {
            sender.sendMessage(Component.text("시세 갱신 시점이 아직 초기화되지 않았습니다 (다음 tick 에서 정렬됨).", NamedTextColor.YELLOW));
            return true;
        }
        long secondsLeft = ticksLeft / 20;
        long minutes = secondsLeft / 60;
        long seconds = secondsLeft % 60;

        String phase;
        NamedTextColor phaseColor;
        if (ticksLeft <= WARN_1MIN_TICKS) {
            phase = "1분 알림 단계";
            phaseColor = NamedTextColor.RED;
        } else if (ticksLeft <= WARN_5MIN_TICKS) {
            phase = "5분 알림 단계";
            phaseColor = NamedTextColor.GOLD;
        } else {
            phase = "정상";
            phaseColor = NamedTextColor.GREEN;
        }

        sender.sendMessage(Component.text("══════ 시세 변동 타이머 ══════", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("남은 시간: ", NamedTextColor.WHITE)
                .append(Component.text(minutes + "분 " + seconds + "초", NamedTextColor.YELLOW)));
        sender.sendMessage(Component.text("현재 단계: ", NamedTextColor.WHITE)
                .append(Component.text(phase, phaseColor)));
        sender.sendMessage(Component.text("기본 주기: " + (UPDATE_INTERVAL_TICKS / 20 / 60) + "분", NamedTextColor.DARK_GRAY));
        sender.sendMessage(Component.text("관리자: /시세변동 skip <n> | /시세변동 schedule <분> [초]", NamedTextColor.DARK_GRAY));
        return true;
    }

    private boolean handleSkip(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /시세변동 skip <n>", NamedTextColor.RED));
            return true;
        }
        int n;
        try {
            n = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("n 은 정수여야 합니다.", NamedTextColor.RED));
            return true;
        }
        if (n < 1 || n > MAX_SKIP) {
            sender.sendMessage(Component.text("n 은 1 ~ " + MAX_SKIP + " 사이여야 합니다.", NamedTextColor.RED));
            return true;
        }

        long t0 = System.currentTimeMillis();
        priceManager.forceAdvance(n);
        long elapsed = System.currentTimeMillis() - t0;

        sender.sendMessage(Component.text("[시세] ", NamedTextColor.GOLD)
                .append(Component.text(n + "회 강제 진행 완료 (" + elapsed + "ms)", NamedTextColor.AQUA)));
        return true;
    }

    private boolean handleSchedule(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /시세변동 schedule <분> [초]", NamedTextColor.RED));
            return true;
        }
        int minutes, seconds = 0;
        try {
            minutes = Integer.parseInt(args[1]);
            if (args.length >= 3) seconds = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("분·초는 정수여야 합니다.", NamedTextColor.RED));
            return true;
        }
        if (minutes < 0 || seconds < 0 || seconds >= 60) {
            sender.sendMessage(Component.text("분 ≥ 0, 0 ≤ 초 < 60 이어야 합니다.", NamedTextColor.RED));
            return true;
        }
        long totalSeconds = (long) minutes * 60 + seconds;
        if (totalSeconds == 0) {
            sender.sendMessage(Component.text("0초 예약은 불가합니다. 즉시 갱신은 /시세변동 skip 1 사용.", NamedTextColor.RED));
            return true;
        }
        long delayTicks = totalSeconds * 20;
        priceManager.scheduleNextUpdate(delayTicks);

        sender.sendMessage(Component.text("[시세] ", NamedTextColor.GOLD)
                .append(Component.text("다음 시세 변동을 " + minutes + "분 " + seconds + "초 뒤로 예약했습니다.",
                        NamedTextColor.AQUA)));
        sender.sendMessage(Component.text("이후 자동 10분 주기 유지됨.", NamedTextColor.DARK_GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("skip", "schedule");
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("skip")) return List.of("1", "10", "50", "100");
            if (args[0].equalsIgnoreCase("schedule")) return List.of("1", "5", "10", "30");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("schedule")) {
            return List.of("0", "15", "30", "45");
        }
        return Collections.emptyList();
    }
}
