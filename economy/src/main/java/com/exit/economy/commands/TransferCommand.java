package com.exit.economy.commands;

import com.exit.economy.EconomyPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class TransferCommand implements CommandExecutor {

    private final EconomyPlugin plugin;

    public TransferCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("economy.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /" + label + " <플레이어> <금액>").color(NamedTextColor.RED));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("'" + args[0] + "' 플레이어를 찾을 수 없습니다. (온라인만 가능)").color(NamedTextColor.RED));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("금액은 숫자로 입력해주세요.").color(NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(Component.text("1w 이상 송금할 수 있습니다.").color(NamedTextColor.RED));
            return true;
        }

        boolean success = plugin.getEconomy().addBalance(target.getUniqueId(), amount);
        if (!success) {
            sender.sendMessage(Component.text("송금 처리에 실패했습니다.").color(NamedTextColor.RED));
            return true;
        }

        long newBalance = plugin.getEconomy().getBalance(target.getUniqueId());

        sender.sendMessage(
            Component.text("✦ [관리자] ").color(NamedTextColor.AQUA)
                .append(Component.text(target.getName()).color(NamedTextColor.WHITE))
                .append(Component.text("에게 ").color(NamedTextColor.AQUA))
                .append(Component.text(String.format("%,d", amount) + "w").color(NamedTextColor.GOLD))
                .append(Component.text(" 송금 완료 (잔액: ").color(NamedTextColor.AQUA))
                .append(Component.text(String.format("%,d", newBalance) + "w").color(NamedTextColor.GOLD))
                .append(Component.text(")").color(NamedTextColor.AQUA))
        );
        target.sendMessage(
            Component.text("✦ 관리자로부터 ").color(NamedTextColor.GREEN)
                .append(Component.text(String.format("%,d", amount) + "w").color(NamedTextColor.GOLD))
                .append(Component.text("가 지급되었습니다. 잔액: ").color(NamedTextColor.GREEN))
                .append(Component.text(String.format("%,d", newBalance) + "w").color(NamedTextColor.GOLD))
        );

        plugin.getBalanceScoreboard().update(target);
        return true;
    }
}
