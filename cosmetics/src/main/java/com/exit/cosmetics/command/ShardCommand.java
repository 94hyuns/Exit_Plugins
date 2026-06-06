package com.exit.cosmetics.command;

import com.exit.core.data.PlayerDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /가루 &lt;player&gt; &lt;amount&gt; — 관리자용 치장 가루 직접 지급.
 * 테스트/이벤트 보상용. 오프라인 플레이어도 캐시되어 있으면 지급 가능.
 */
public class ShardCommand implements CommandExecutor, TabCompleter {

    private final PlayerDataManager dataManager;

    public ShardCommand(PlayerDataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /가루 <player> <amount>").color(NamedTextColor.YELLOW));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[0]).color(NamedTextColor.RED));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("수량은 정수여야 합니다.").color(NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(Component.text("수량은 1 이상이어야 합니다.").color(NamedTextColor.RED));
            return true;
        }

        boolean ok = dataManager.addShards(target.getUniqueId(), amount);
        if (ok) {
            sender.sendMessage(Component.text(target.getName() + " 에게 치장 가루 " + amount + " 지급 완료.")
                    .color(NamedTextColor.GREEN));
            Player online = target.getPlayer();
            if (online != null) {
                online.sendMessage(Component.text("§d치장 가루 §f" + amount + "§7을(를) 받았습니다.")
                        .color(NamedTextColor.LIGHT_PURPLE));
            }
        } else {
            sender.sendMessage(Component.text("지급 실패 (DB 오류).").color(NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            String p = args[0].toLowerCase();
            return names.stream().filter(s -> s.toLowerCase().startsWith(p)).collect(Collectors.toList());
        }
        if (args.length == 2) {
            return List.of("100", "1000", "5000");
        }
        return List.of();
    }
}
