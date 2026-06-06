package com.exit.customitems.weapon;

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

/**
 * {@code /frostmourne give <player> [amount]} — 1차 테스트용 OP 지급 명령어.
 * 보스 리워드 연동 시 별도 give 경로로 대체될 수 있음.
 */
public class FrostmourneCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "customitems.frostmourne.admin";

    private final FrostmourneItem frostmourneItem;

    public FrostmourneCommand(FrostmourneItem frostmourneItem) {
        this.frostmourneItem = frostmourneItem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            sendUsage(sender);
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

        ItemStack stack = frostmourneItem.create(amount);
        var overflow = target.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text(
                target.getName() + "에게 프로스트모운 " + amount + "개 지급.")
                .color(NamedTextColor.AQUA));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("사용법: /frostmourne give <player> [amount]")
                .color(NamedTextColor.YELLOW));
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
