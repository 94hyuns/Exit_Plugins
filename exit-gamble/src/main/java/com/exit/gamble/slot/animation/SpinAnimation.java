package com.exit.gamble.slot.animation;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.engine.ReelEngine;
import com.exit.gamble.slot.engine.SpinResult;
import com.exit.gamble.slot.gui.SlotGui;
import com.exit.gamble.slot.gui.SlotHolder;
import com.exit.gamble.slot.symbol.SlotSymbol;
import com.exit.gamble.slot.world.SlotMachine;
import com.exit.gamble.slot.world.SlotMachineManager;
import com.exit.gamble.slot.world.SlotMachineMirror;
import com.exit.gamble.slot.world.SlotMachineMirror.StatusKind;
import com.exit.gamble.stats.GambleStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

import java.util.UUID;

public class SpinAnimation extends BukkitRunnable {

    private final Plugin plugin;
    private final SlotConfig config;
    private final ReelEngine engine;
    private final SlotGui gui;
    private final SlotMachineMirror mirror;
    private final SlotMachineManager machineManager;
    private final GambleStatsManager stats;
    private final SlotHolder holder;
    private final SpinResult result;
    private final UUID playerId;
    private final String playerName;

    private int tick = 0;
    private final boolean[] reelStopped = new boolean[3];

    public SpinAnimation(Plugin plugin, SlotConfig config, ReelEngine engine,
                         SlotGui gui, SlotMachineMirror mirror, SlotMachineManager machineManager,
                         GambleStatsManager stats,
                         SlotHolder holder, SpinResult result, Player player) {
        this.plugin = plugin;
        this.config = config;
        this.engine = engine;
        this.gui = gui;
        this.mirror = mirror;
        this.machineManager = machineManager;
        this.stats = stats;
        this.holder = holder;
        this.result = result;
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
    }

    public void start() {
        runTaskTimer(plugin, 0L, config.tickInterval());
    }

    @Override
    public void run() {
        int elapsed = tick * config.tickInterval();
        Player player = Bukkit.getPlayer(playerId);
        SlotMachine machine = holder.machine();

        for (int i = 0; i < 3; i++) {
            if (reelStopped[i]) continue;
            int stopAt = stopTickFor(i);
            SlotSymbol next;
            boolean justStopped = elapsed >= stopAt;
            if (justStopped) {
                next = result.reel(i);
                reelStopped[i] = true;
                if (player != null) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            } else {
                next = engine.pickRandom();
            }
            if (player != null && holder.getInventory().getViewers().contains(player)) {
                gui.setReelSymbol(holder.getInventory(), i, next);
            }
            mirror.updateReel(machine, i, next);
        }

        if (reelStopped[0] && reelStopped[1] && reelStopped[2]) {
            finish(player, machine);
            cancel();
            return;
        }
        tick++;
    }

    private int stopTickFor(int reelIndex) {
        return switch (reelIndex) {
            case 0 -> config.reel1StopTick();
            case 1 -> config.reel2StopTick();
            case 2 -> config.reel3StopTick();
            default -> throw new IllegalStateException();
        };
    }

    private void finish(Player player, SlotMachine machine) {
        if (result.isWin() && !holder.isTestMode()) {
            EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
            if (eco != null) eco.addBalance(playerId, result.payout());
            stats.addSlotPayout(playerId, result.payout());
        }

        String msg;
        StatusKind kind;
        if (result.isJackpot()) {
            msg = "★ 잭팟! +" + result.payout() + "원 ★";
            kind = StatusKind.JACKPOT;
            broadcastJackpot(player);
        } else if (result.isWin()) {
            msg = "당첨! +" + result.payout() + "원";
            kind = StatusKind.WIN;
            if (player != null) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            SlotSymbol[] r = result.reels();
            msg = "아쉽... [" + r[0].id() + "][" + r[1].id() + "][" + r[2].id() + "]";
            kind = StatusKind.LOSE;
            if (player != null) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
        }

        holder.setSpinning(false);
        if (player != null && holder.getInventory().getViewers().contains(player)) {
            gui.renderSpinButton(holder.getInventory(), false);
            gui.renderStatus(holder.getInventory(), holder, player, msg);
        }
        mirror.updateStatus(machine, kind, playerName, result.payout());

        if (machine != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (machine.isSpinning()) return;
                if (player == null || !holder.getInventory().getViewers().contains(player)) {
                    machineManager.release(machine);
                    mirror.updateStatus(machine, StatusKind.IDLE, "", 0);
                    mirror.resetReels(machine, config.symbols().get(0));
                }
            }, 60L);
        }
    }

    private void broadcastJackpot(Player winner) {
        String payoutFmt = String.format("%,d", result.payout());

        // 위너 타이틀
        if (winner != null) {
            winner.showTitle(Title.title(
                    Component.text("★ JACKPOT ★", NamedTextColor.GOLD)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("+" + payoutFmt + "원", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        }

        // 서버 전체 브로드캐스트 (5줄 framed)
        Component line = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GOLD);
        Bukkit.broadcast(line);
        Bukkit.broadcast(Component.text("                  ★ 슬롯머신 잭팟! ★", NamedTextColor.GOLD));
        Bukkit.broadcast(Component.text("        " + winnerName() + " 님이 ", NamedTextColor.WHITE)
                .append(Component.text(payoutFmt + "원", NamedTextColor.YELLOW))
                .append(Component.text(" 을 따냈습니다!", NamedTextColor.WHITE)));
        Bukkit.broadcast(Component.text("                       축하합니다 🎉", NamedTextColor.AQUA));
        Bukkit.broadcast(line);

        // 모든 온라인 플레이어에게 사운드
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private String winnerName() {
        return playerName == null ? "Anonymous" : playerName;
    }
}
