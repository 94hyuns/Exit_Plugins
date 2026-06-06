package com.exit.customitems.lamp;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@code /lamp give <player> <life|combat> [amount]} 관리자 명령어.
 * 2단계에서 상점 연동이 붙어도 이 명령어는 디버깅/지급용으로 유지.
 */
public class LampCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "customitems.lamp.admin";

    private final LampItem lampItem;

    public LampCommand(LampItem lampItem) {
        this.lampItem = lampItem;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            sendUsage(sender);
            return true;
        }

        if (args.length < 3) {
            sendUsage(sender);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[1])
                .color(NamedTextColor.RED));
            return true;
        }

        LampType type = LampType.fromString(args[2]);
        if (type == null) {
            sender.sendMessage(Component.text("알 수 없는 람프 타입: " + args[2]
                + " (life | combat | mutation)").color(NamedTextColor.RED));
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("개수는 숫자여야 합니다: " + args[3])
                    .color(NamedTextColor.RED));
                return true;
            }
            if (amount <= 0 || amount > 64) {
                sender.sendMessage(Component.text("개수는 1~64 사이여야 합니다.")
                    .color(NamedTextColor.RED));
                return true;
            }
        }

        ItemStack stack = lampItem.create(type, amount);
        var overflow = target.getInventory().addItem(stack);
        // 인벤토리가 가득 차면 바닥에 드롭
        for (ItemStack leftover : overflow.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }

        sender.sendMessage(Component.text(
            target.getName() + "에게 " + type.getDisplayName() + " " + amount + "개 지급.")
            .color(NamedTextColor.GREEN));
        target.sendMessage(Component.text(
            type.getDisplayName() + " " + amount + "개를 받았습니다.")
            .color(NamedTextColor.GREEN));
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("사용법: /lamp give <player> <life|combat|mutation> [amount]")
            .color(NamedTextColor.YELLOW));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            return filter(List.of("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.asList("life", "combat", "mutation"), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "8", "16", "32", "64"), args[3]);
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
