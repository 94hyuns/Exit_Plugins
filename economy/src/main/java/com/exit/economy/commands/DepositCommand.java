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

public class DepositCommand implements CommandExecutor {

    private final EconomyPlugin plugin;

    public DepositCommand(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능한 명령어입니다.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CashNoteFactory.isCashNote(item)) {
            player.sendMessage(
                Component.text("울캐쉬 종이를 손에 들고 명령어를 입력하거나, 우클릭으로 자동 입금하세요.").color(NamedTextColor.RED)
            );
            return true;
        }

        long total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (CashNoteFactory.isCashNote(slot)) {
                total += CashNoteFactory.getAmount(slot) * slot.getAmount();
                player.getInventory().setItem(i, null);
            }
        }

        if (total <= 0) {
            player.sendMessage(Component.text("입금할 울캐쉬 종이가 없습니다.").color(NamedTextColor.RED));
            return true;
        }

        plugin.getEconomy().addBalance(player.getUniqueId(), total);

        player.sendMessage(
            Component.text("✦ ").color(NamedTextColor.GOLD)
                .append(Component.text(String.format("%,d", total) + "w").color(NamedTextColor.YELLOW))
                .append(Component.text(" 전액 입금 완료 → 잔액: ").color(NamedTextColor.GREEN))
                .append(Component.text(String.format("%,d", plugin.getEconomy().getBalance(player.getUniqueId())) + "w").color(NamedTextColor.GOLD))
        );
        plugin.getBalanceScoreboard().update(player);
        return true;
    }
}
