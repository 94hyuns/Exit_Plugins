package com.exit.job.command;

import com.exit.job.JobPlugin;
import com.exit.job.storage.MineralStorageItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /보관함재발급 &lt;직업&gt; &lt;플레이어&gt; — 보관함 분실 시 OP 가 재발급.
 *
 * <p>지원 직업: 광부/miner, 어부/fisher, 농부/farmer.
 * <p>플레이어는 온라인 상태여야 함 (PDC 마커 갱신 + 인벤 지급).
 * <p>Fishing/Farming 의 storage item 은 reflection 으로 호출 (Job compile-time 의존 회피).
 */
public class ReissueStorageCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("job.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /보관함재발급 <광부|어부|농부> <플레이어>",
                    NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false));
            return true;
        }
        String jobArg = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어가 온라인이 아닙니다: " + args[1],
                    NamedTextColor.RED));
            return true;
        }

        switch (jobArg) {
            case "광부", "miner"  -> grantMineral(sender, target);
            case "어부", "fisher" -> grantFishViaReflection(sender, target);
            case "농부", "farmer" -> grantCropViaReflection(sender, target);
            default -> sender.sendMessage(Component.text("직업은 광부/어부/농부 중 하나여야 합니다.",
                    NamedTextColor.RED));
        }
        return true;
    }

    private void grantMineral(CommandSender sender, Player target) {
        ItemStack stack = MineralStorageItem.create(JobPlugin.getInstance());
        deliver(target, stack);
        success(sender, target, "광부의 보관함");
    }

    private void grantFishViaReflection(CommandSender sender, Player target) {
        Plugin fishing = Bukkit.getPluginManager().getPlugin("Fishing");
        if (fishing == null || !fishing.isEnabled()) {
            sender.sendMessage(Component.text("Fishing 플러그인이 로드되지 않아 어부 보관함을 발급할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }
        ItemStack stack = invokeStorageCreate("com.exit.fishing.storage.FishStorageItem", fishing);
        if (stack == null) {
            sender.sendMessage(Component.text("어부 보관함 생성 실패 (Fishing API 호환 문제).",
                    NamedTextColor.RED));
            return;
        }
        deliver(target, stack);
        success(sender, target, "어부의 보관함");
    }

    private void grantCropViaReflection(CommandSender sender, Player target) {
        Plugin farming = Bukkit.getPluginManager().getPlugin("Farming");
        if (farming == null || !farming.isEnabled()) {
            sender.sendMessage(Component.text("Farming 플러그인이 로드되지 않아 농부 보관함을 발급할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }
        ItemStack stack = invokeStorageCreate("com.exit.farming.storage.CropStorageItem", farming);
        if (stack == null) {
            sender.sendMessage(Component.text("농부 보관함 생성 실패 (Farming API 호환 문제).",
                    NamedTextColor.RED));
            return;
        }
        deliver(target, stack);
        success(sender, target, "농부의 보관함");
    }

    private ItemStack invokeStorageCreate(String className, Plugin owner) {
        try {
            Class<?> cls = Class.forName(className);
            Method create = cls.getMethod("create", Plugin.class);
            Object result = create.invoke(null, owner);
            return (result instanceof ItemStack is) ? is : null;
        } catch (Throwable t) {
            JobPlugin.getInstance().getLogger().warning(
                    "[Job] " + className + ".create 호출 실패: " + t.getMessage());
            return null;
        }
    }

    private void deliver(Player target, ItemStack stack) {
        var leftover = target.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            leftover.values().forEach(s -> target.getWorld()
                    .dropItemNaturally(target.getLocation(), s));
        }
    }

    private void success(CommandSender sender, Player target, String boxName) {
        sender.sendMessage(Component.text("[보관함재발급] " + target.getName() + " 에게 "
                        + boxName + " 발급 완료", NamedTextColor.GREEN)
                .decoration(TextDecoration.BOLD, false));
        target.sendMessage(Component.text("[보관함재발급] " + boxName + " 을(를) 받았습니다.",
                NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("job.admin")) return List.of();
        if (args.length == 1) return filterPrefix(List.of("광부", "어부", "농부"), args[0]);
        if (args.length == 2) {
            List<String> names = new ArrayList<>();
            for (var p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filterPrefix(names, args[1]);
        }
        return List.of();
    }

    private static List<String> filterPrefix(List<String> source, String prefix) {
        if (prefix == null || prefix.isEmpty()) return source;
        String lo = prefix.toLowerCase(Locale.ROOT);
        return source.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lo)).toList();
    }
}
