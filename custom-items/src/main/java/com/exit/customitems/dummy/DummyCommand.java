package com.exit.customitems.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /dummy 명령어 — vanilla COW 기반 v2 허수아비.
 *
 * /dummy spawn  — 자기 위치에 새 허수아비 1개 생성 (HP 500, 2초 자동 회복)
 * /dummy clear  — 현재 월드 모든 허수아비 (v1+v2) 제거
 */
public final class DummyCommand implements CommandExecutor, TabCompleter {

    private final DummyKeys keys;
    private final NewDummyManager manager;

    public DummyCommand(DummyKeys keys, NewDummyManager manager) {
        this.keys = keys;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어 전용 명령어.");
            return true;
        }
        if (args.length == 0) { sendUsage(player); return true; }
        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                manager.spawn(player.getLocation());
                player.sendMessage(Component.text("[허수아비] 생성 완료 (HP 500, 자동회복).")
                        .color(NamedTextColor.GREEN));
            }
            case "clear" -> {
                int count = clearWorld(player.getWorld());
                player.sendMessage(Component.text("[허수아비] " + count + "개 제거.")
                        .color(NamedTextColor.YELLOW));
            }
            default -> sendUsage(player);
        }
        return true;
    }

    private int clearWorld(World w) {
        int count = 0;
        for (Entity e : w.getEntities()) {
            var pdc = e.getPersistentDataContainer();
            // v1 + v2 모두 정리
            if (pdc.has(keys.marker, PersistentDataType.BYTE)
                    || pdc.has(keys.markerV2, PersistentDataType.BYTE)) {
                e.remove();
                count++;
            }
        }
        return count;
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("/dummy spawn — 허수아비 생성").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/dummy clear — 현재 월드 허수아비 모두 제거").color(NamedTextColor.YELLOW));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Arrays.asList("spawn", "clear").stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
