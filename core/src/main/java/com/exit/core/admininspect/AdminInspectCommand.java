package com.exit.core.admininspect;

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
import java.util.UUID;

public class AdminInspectCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }
        if (!viewer.hasPermission("core.admin")) {
            viewer.sendMessage(AdminInspectHub.plain("권한이 없습니다.").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(AdminInspectHub.plain("사용법: /관리자검사 <닉네임>").color(NamedTextColor.YELLOW));
            return true;
        }

        String name = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        UUID uuid = target.getUniqueId();
        if (uuid == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            viewer.sendMessage(AdminInspectHub.plain("해당 플레이어를 찾을 수 없습니다: " + name)
                    .color(NamedTextColor.RED));
            return true;
        }

        new AdminInspectHub(uuid, target.getName() != null ? target.getName() : name).open(viewer);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("core.admin")) return result;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) result.add(p.getName());
            }
        }
        return result;
    }
}
