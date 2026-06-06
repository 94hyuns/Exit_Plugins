package com.exit.customitems.lamp.bulk;

import com.exit.customitems.lamp.enchant.EnchantStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /lampbulk [spawn|remove|open] 처리.
 *
 * <ul>
 *   <li>open / spawn / remove: 모두 admin (customitems.lampbulk.admin)</li>
 *   <li>일반 사용자는 NPC 우클릭으로만 GUI 진입 가능</li>
 * </ul>
 */
public class BulkLampCommand implements CommandExecutor, TabCompleter {

    private final BulkLampKeys keys;
    private final BulkLampNPCManager npcManager;
    private final EnchantStorage storage;

    public BulkLampCommand(BulkLampKeys keys, BulkLampNPCManager npcManager, EnchantStorage storage) {
        this.keys = keys;
        this.npcManager = npcManager;
        this.storage = storage;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            usage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open" -> {
                if (!player.hasPermission("customitems.lampbulk.admin")) {
                    player.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                new BulkLampGUI(keys, storage).openFor(player);
                player.sendMessage(Component.text("[램프] 작업대를 열었습니다.", NamedTextColor.GREEN));
            }
            case "spawn" -> {
                if (!player.hasPermission("customitems.lampbulk.admin")) {
                    player.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                Villager v = npcManager.spawn(player.getLocation());
                player.sendMessage(Component.text("[램프] 작업대 NPC 를 생성했습니다. (id=" + v.getEntityId() + ")",
                        NamedTextColor.GREEN));
            }
            case "remove" -> {
                if (!player.hasPermission("customitems.lampbulk.admin")) {
                    player.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
                    return true;
                }
                Entity target = player.getTargetEntity(6);
                if (target == null) {
                    player.sendMessage(Component.text("[램프] 시선 6블록 이내에 엔티티가 없습니다.", NamedTextColor.RED));
                    return true;
                }
                if (npcManager.remove(target)) {
                    player.sendMessage(Component.text("[램프] 작업대 NPC 를 제거했습니다.", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("[램프] 작업대 NPC 가 아닙니다.", NamedTextColor.RED));
                }
            }
            default -> usage(player);
        }
        return true;
    }

    private void usage(Player p) {
        p.sendMessage(Component.text("사용법:", NamedTextColor.YELLOW));
        p.sendMessage(Component.text("  /lampbulk open  — 본인에게 GUI 열기 (admin)", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /lampbulk spawn — 현재 위치에 NPC 생성 (admin)", NamedTextColor.GRAY));
        p.sendMessage(Component.text("  /lampbulk remove — 시선이 향한 NPC 제거 (admin)", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : new String[]{"open", "spawn", "remove"}) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
