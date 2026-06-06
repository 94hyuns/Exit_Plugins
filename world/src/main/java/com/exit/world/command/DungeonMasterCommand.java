package com.exit.world.command;

import com.exit.world.boss.BossArenaManager;
import com.exit.world.manager.DungeonRegistry;
import com.exit.world.npc.DungeonMasterNPCManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /던전마스터 spawn [스킨닉]      — 현재 위치에 NPC 생성
 * /던전마스터 remove              — NPC 제거
 * /던전마스터 setskin <닉네임>    — 스킨 변경
 * /던전마스터 reload              — dungeons.yml 재로드
 */
public class DungeonMasterCommand implements CommandExecutor, TabCompleter {

    private final DungeonMasterNPCManager npcManager;
    private final DungeonRegistry registry;
    private final BossArenaManager bossArenaManager;

    public DungeonMasterCommand(DungeonMasterNPCManager npcManager,
                                DungeonRegistry registry,
                                BossArenaManager bossArenaManager) {
        this.npcManager = npcManager;
        this.registry = registry;
        this.bossArenaManager = bossArenaManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) { sendHelp(player); return true; }

        switch (args[0].toLowerCase()) {
            case "spawn"     -> handleSpawn(player, args);
            case "remove"    -> handleRemove(player);
            case "setskin"   -> handleSetSkin(player, args);
            case "reload"    -> handleReload(player);
            case "bossreset" -> handleBossReset(player, args);
            default          -> sendHelp(player);
        }
        return true;
    }

    private void handleBossReset(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /던전마스터 bossreset <아레나키>  (예: boss1_1)")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        String arenaKey = args[1];
        if (bossArenaManager == null) {
            player.sendMessage(Component.text("[World] 보스 아레나 매니저 비활성 (MythicMobs 미설치)")
                    .color(NamedTextColor.RED));
            return;
        }
        boolean ok = bossArenaManager.adminResetCooldown(arenaKey);
        if (ok) {
            player.sendMessage(Component.text("[World] " + arenaKey + " 쿨다운 초기화 완료. "
                    + "월드에 플레이어 있으면 10초 카운트다운 즉시 시작.")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[World] '" + arenaKey + "' 아레나 없음")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleSpawn(Player player, String[] args) {
        String skin = args.length >= 2 ? args[1] : player.getName();
        player.sendMessage(Component.text("[World] 던전 마스터 NPC 생성 중... (스킨 로딩)")
                .color(NamedTextColor.GRAY));
        if (npcManager.spawn(player.getLocation(), skin)) {
            player.sendMessage(Component.text("[World] 던전 마스터 NPC 생성 완료 (스킨: " + skin + ")")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[World] 생성 실패 — NpcService 미등록")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleRemove(Player player) {
        if (npcManager.remove()) {
            player.sendMessage(Component.text("[World] 던전 마스터 NPC 제거 완료")
                    .color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[World] 던전 마스터 NPC 가 없습니다")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleSetSkin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /던전마스터 setskin <플레이어명>")
                    .color(NamedTextColor.YELLOW));
            return;
        }
        String skin = args[1];
        player.sendMessage(Component.text("[World] 스킨 변경 중...").color(NamedTextColor.GRAY));
        if (npcManager.setSkin(skin)) {
            player.sendMessage(Component.text("[World] 스킨 → " + skin).color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[World] 먼저 /던전마스터 spawn 으로 NPC 생성하세요")
                    .color(NamedTextColor.RED));
        }
    }

    private void handleReload(Player player) {
        registry.load();
        player.sendMessage(Component.text("[World] dungeons.yml 재로드 완료").color(NamedTextColor.GREEN));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("═══ 던전 마스터 ═══").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text(" /던전마스터 spawn [스킨닉]").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 현재 위치에 NPC 생성").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /던전마스터 remove").color(NamedTextColor.YELLOW)
                .append(Component.text(" — NPC 제거").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /던전마스터 setskin <닉네임>").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 스킨 변경").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /던전마스터 reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" — dungeons.yml 재로드").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /던전마스터 bossreset <아레나키>").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 보스 쿨다운 해제 + 10초 후 강제 소환").color(NamedTextColor.GRAY)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterPrefix(Arrays.asList("spawn", "remove", "setskin", "reload", "bossreset"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("setskin")))
            return null; // Bukkit 온라인 플레이어 자동완성
        if (args.length == 2 && args[0].equalsIgnoreCase("bossreset"))
            return filterPrefix(Arrays.asList("boss1_1", "boss1_2", "boss2"), args[1]);
        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
