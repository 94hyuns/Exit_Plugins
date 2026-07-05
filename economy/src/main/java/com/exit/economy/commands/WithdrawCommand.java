package com.exit.economy.commands;

import com.exit.economy.EconomyPlugin;
import com.exit.economy.items.CashNoteFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class WithdrawCommand implements CommandExecutor {

    private final EconomyPlugin plugin;

    public WithdrawCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능한 명령어입니다.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("사용법: /" + label + " <금액>").color(NamedTextColor.RED));
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("금액은 숫자로 입력해주세요.").color(NamedTextColor.RED));
            return true;
        }

        if (amount <= 0) {
            player.sendMessage(Component.text("1w 이상 출금할 수 있습니다.").color(NamedTextColor.RED));
            return true;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("인벤토리가 가득 찼습니다.").color(NamedTextColor.RED));
            return true;
        }

        boolean success = plugin.getEconomy().subtractBalance(player.getUniqueId(), amount);
        if (!success) {
            long balance = plugin.getEconomy().getBalance(player.getUniqueId());
            player.sendMessage(
                Component.text("잔액이 부족합니다. 현재 잔액: ").color(NamedTextColor.RED)
                    .append(Component.text(String.format("%,d", balance) + "w").color(NamedTextColor.GOLD))
            );
            return true;
        }

        ItemStack note = CashNoteFactory.createNote(plugin, amount);
        player.getInventory().addItem(note);

        player.sendMessage(
            Component.text("✦ ").color(NamedTextColor.GOLD)
                .append(Component.text(String.format("%,d", amount) + "w").color(NamedTextColor.YELLOW))
                .append(Component.text(" 출금 완료 → 잔액: ").color(NamedTextColor.GREEN))
                .append(Component.text(String.format("%,d", plugin.getEconomy().getBalance(player.getUniqueId())) + "w").color(NamedTextColor.GOLD))
        );
        plugin.getBalanceScoreboard().update(player);
        return true;
    }
}
