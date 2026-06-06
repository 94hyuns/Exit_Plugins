package com.exit.customitems.armor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WingedArmorCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "customitems.wingedarmor.admin";

    private final WingedArmorItem wingedArmorItem;

    public WingedArmorCommand(WingedArmorItem wingedArmorItem) {
        this.wingedArmorItem = wingedArmorItem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text("사용법: /wingedarmor give <player> [amount]")
                    .color(NamedTextColor.YELLOW));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[1])
                    .color(NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("개수는 숫자여야 합니다: " + args[2])
                        .color(NamedTextColor.RED));
                return true;
            }
            if (amount <= 0 || amount > 64) {
                sender.sendMessage(Component.text("개수는 1~64 사이여야 합니다.")
                        .color(NamedTextColor.RED));
                return true;
            }
        }

        ItemStack stack = wingedArmorItem.create(amount);
        var overflow = target.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text(
                target.getName() + "에게 용의 날개 " + amount + "개 지급.")
                .color(NamedTextColor.LIGHT_PURPLE));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) return filter(List.of("give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "64"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> src, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : src) if (s.toLowerCase().startsWith(lower)) out.add(s);
        return out;
    }
}
