package com.exit.economy.display;

import com.exit.core.events.BalanceChangeEvent;
import com.exit.economy.EconomyPlugin;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BalanceScoreboard implements Listener {

    private static final String OBJECTIVE_NAME = "economy_bal";

    private final EconomyPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public BalanceScoreboard(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    // 잔액 변경 이벤트 구독 → 자동 갱신
    @EventHandler
    public void onBalanceChange(BalanceChangeEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerUuid());
        if (player != null && player.isOnline()) {
            update(player);
        }
    }

    public void update(Player player) {
        long balance = plugin.getEconomy().getBalance(player.getUniqueId());
        if (balance < 0) balance = 0;

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), k -> manager.getNewScoreboard());

        Objective old = board.getObjective(OBJECTIVE_NAME);
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective(
            OBJECTIVE_NAME,
            Criteria.DUMMY,
            Component.text("✦ 울캐쉬").color(NamedTextColor.GOLD)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Score blank1 = obj.getScore(" ");
        blank1.setScore(3);
        blank1.customName(Component.empty());
        blank1.numberFormat(NumberFormat.blank());

        Score balScore = obj.getScore("balance");
        balScore.setScore(2);
        balScore.customName(
            Component.text(String.format("%,d", balance) + "w")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        );
        balScore.numberFormat(NumberFormat.blank());

        Score blank2 = obj.getScore("  ");
        blank2.setScore(1);
        blank2.customName(Component.empty());
        blank2.numberFormat(NumberFormat.blank());

        player.setScoreboard(board);
    }

    public void cleanup() {
        boards.clear();
    }
}
