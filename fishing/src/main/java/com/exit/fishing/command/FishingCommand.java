package com.exit.fishing.command;

import com.exit.fishing.FishingPlugin;
import com.exit.fishing.season.Season;
import com.exit.fishing.season.SeasonManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

/**
 * 낚시 관련 3개 명령어 통합 executor.
 * - /낚시           : 낚시 상점 GUI 즉시 오픈 (테스트용, OP)
 * - /계절           : 현재 계절 조회 (일반) / 설정 (OP)
 * - /낚시리로드      : config 재로드 + 계절 태스크 재시작 (OP)
 */
public class FishingCommand implements CommandExecutor, TabCompleter {

    private final FishingPlugin plugin;
    private final SeasonManager seasons;

    public FishingCommand(FishingPlugin plugin, SeasonManager seasons) {
        this.plugin = plugin;
        this.seasons = seasons;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName()) {
            case "낚시" -> cmdFishing(sender);
            case "낚시도감" -> cmdCodex(sender, args);
            case "계절" -> cmdSeason(sender, args);
            case "낚시리로드" -> cmdReload(sender);
            default -> false;
        };
    }

    /**
     * /낚시도감 [계절]
     *   인자 없음 → 현재 계절 도감
     *   인자 있음 → 해당 계절 도감 (봄|여름|가을|겨울)
     */
    private boolean cmdCodex(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("콘솔에서는 사용할 수 없습니다.", NamedTextColor.RED));
            return true;
        }

        Season tab;
        if (args.length == 0) {
            tab = seasons.current();
        } else {
            tab = Season.fromKorean(args[0]);
            if (tab == null) {
                player.sendMessage(Component.text("계절은 봄/여름/가을/겨울 중 하나입니다.", NamedTextColor.RED));
                return true;
            }
        }
        com.exit.fishing.gui.FishCodexGUI.open(player, tab);
        return true;
    }

    private boolean cmdFishing(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("콘솔에서는 사용할 수 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (!player.isOp()) {
            player.sendMessage(Component.text("관리자만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        // 테스트용: 낚시 상점 GUI 즉시 오픈. 실제 배포 환경에선 낚시 상인 NPC를 거침.
        com.exit.fishing.gui.FishShopGUI.open(player, seasons);
        return true;
    }

    private boolean cmdSeason(CommandSender sender, String[] args) {
        String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
        if (args.length == 0) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(prefix + " &7지금 계절은 &f" + seasons.current().korean() + "&7 입니다"));
            return true;
        }
        if (!sender.isOp()) {
            sender.sendMessage(Component.text("계절을 설정하려면 관리자여야 합니다.", NamedTextColor.RED));
            return true;
        }
        Season s = Season.fromKorean(args[0]);
        if (s == null) {
            sender.sendMessage(Component.text("사용법: /계절 [봄|여름|가을|겨울]", NamedTextColor.YELLOW));
            return true;
        }
        seasons.set(s);
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(prefix + " &7계절을 &f" + s.korean() + " &7로 세팅했습니다"));
        return true;
    }

    private boolean cmdReload(CommandSender sender) {
        if (!sender.isOp() && !sender.hasPermission("fishing.reload")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        plugin.reloadConfig();
        seasons.restartTask();
        sender.sendMessage(Component.text("낚시 플러그인 설정을 다시 불러왔습니다. "
                + "(계절 주기: " + plugin.getConfig().getInt("season.cycle-minutes") + "분)",
                NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ((command.getName().equals("계절") || command.getName().equals("낚시도감"))
                && args.length == 1) {
            return Stream.of("봄", "여름", "가을", "겨울")
                    .filter(s -> s.startsWith(args[0]))
                    .toList();
        }
        return List.of();
    }
}
