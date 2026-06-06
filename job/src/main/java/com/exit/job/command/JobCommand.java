package com.exit.job.command;

import com.exit.job.gui.JobOverviewGUI;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JobCommand implements CommandExecutor, TabCompleter {

    private final JobManager jobManager;
    private final JobOverviewGUI overviewGUI;

    public JobCommand(JobManager jobManager, JobOverviewGUI overviewGUI) {
        this.jobManager = jobManager;
        this.overviewGUI = overviewGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName()) {
            case "직업정보" -> handleInfo(sender, args);
            case "직업관리" -> handleAdmin(sender, args);
            default -> false;
        };
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("게임 내에서만 사용 가능합니다.", NamedTextColor.RED));
            return true;
        }

        UUID target = player.getUniqueId();
        if (args.length >= 1) {
            if (!player.isOp()) {
                player.sendMessage(Component.text("다른 플레이어 조회는 OP만 가능합니다.", NamedTextColor.RED));
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
            if (op.getUniqueId() == null) {
                player.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[0], NamedTextColor.RED));
                return true;
            }
            target = op.getUniqueId();
        }

        overviewGUI.open(player, target);
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("job.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(Component.text(
                    "사용법: /직업관리 <addexp|setlevel|setexp> <플레이어> <jobId> <값>",
                    NamedTextColor.YELLOW));
            return true;
        }
        String op = args[0].toLowerCase();
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId() == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다.", NamedTextColor.RED));
            return true;
        }
        JobType type = JobType.fromId(args[2]);
        if (type == null) {
            sender.sendMessage(Component.text("jobId 는 miner/fisher/farmer 중 하나여야 합니다.",
                    NamedTextColor.RED));
            return true;
        }
        long value;
        try { value = Long.parseLong(args[3]); }
        catch (NumberFormatException e) {
            sender.sendMessage(Component.text("값은 정수여야 합니다.", NamedTextColor.RED));
            return true;
        }

        switch (op) {
            case "addexp" -> {
                jobManager.addExp(target.getUniqueId(), type, value);
                sender.sendMessage(Component.text(
                        target.getName() + " 의 " + type.id() + " 직업에 EXP " + value + " 추가",
                        NamedTextColor.GREEN));
            }
            case "setlevel" -> {
                jobManager.setLevel(target.getUniqueId(), type, (int) value);
                sender.sendMessage(Component.text(
                        target.getName() + " 의 " + type.id() + " 레벨을 " + value + " 로 설정",
                        NamedTextColor.GREEN));
            }
            case "setexp" -> {
                jobManager.setExp(target.getUniqueId(), type, value);
                sender.sendMessage(Component.text(
                        target.getName() + " 의 " + type.id() + " EXP를 " + value + " 로 설정",
                        NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("알 수 없는 옵션: " + op, NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equals("직업관리")) {
            if (args.length == 1) return filterPrefix(List.of("addexp", "setlevel", "setexp"), args[0]);
            if (args.length == 2) {
                List<String> names = new java.util.ArrayList<>();
                for (var p : org.bukkit.Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filterPrefix(names, args[1]);
            }
            if (args.length == 3) return filterPrefix(List.of("miner", "fisher", "farmer"), args[2]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) return source;
        String lo = prefix.toLowerCase(java.util.Locale.ROOT);
        return source.stream().filter(s -> s.toLowerCase(java.util.Locale.ROOT).startsWith(lo)).toList();
    }
}
