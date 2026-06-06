package com.exit.shop.command;

import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItem;
import com.exit.shop.model.ShopItemRegistry;
import com.exit.shop.npc.ShopNPCManager;
import com.exit.shop.price.PriceManager;
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
 * /shop 관리자 명령어.
 *
 * /shop spawn <mineral|crop|lamp> [스킨닉네임]
 * /shop remove <mineral|crop|lamp>
 * /shop setskin <mineral|crop|lamp> <닉네임>
 * /shop prices [mineral|crop|lamp]
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private final ShopNPCManager npcManager;
    private final ShopItemRegistry registry;
    private final PriceManager priceManager;

    public ShopCommand(ShopNPCManager npcManager, ShopItemRegistry registry, PriceManager priceManager) {
        this.npcManager = npcManager;
        this.registry = registry;
        this.priceManager = priceManager;
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
            case "spawn"   -> handleSpawn(player, args);
            case "remove"  -> handleRemove(player, args);
            case "setskin" -> handleSetSkin(player, args);
            case "prices"  -> handlePrices(player, args);
            case "reload"  -> handleReload(player);
            case "report"  -> handleReport(player);
            default        -> sendHelp(player);
        }
        return true;
    }

    private void handleReport(Player player) {
        var plugin = com.exit.shop.ShopPlugin.getInstance();
        var sched = plugin.getReportScheduler();
        if (sched == null) {
            player.sendMessage(Component.text("[Shop] 리포트 스케줄러 비활성 상태.", NamedTextColor.RED));
            return;
        }
        sched.reloadConfig();
        sched.fireNow();
        player.sendMessage(Component.text("[Shop] 일일 리포트 강제 fire 완료 (콘솔 로그 확인).",
                NamedTextColor.GREEN));
    }

    private void handleReload(Player player) {
        com.exit.shop.ShopPlugin plugin = com.exit.shop.ShopPlugin.getInstance();
        plugin.reloadConfig();
        java.io.File cf = new java.io.File(plugin.getDataFolder(), "config.yml");
        registry.reload(cf, plugin.getLogger());
        plugin.getTabRegistry().reload(cf, plugin.getLogger());
        plugin.getButtonStyleRegistry().reload(cf, plugin.getLogger());
        player.sendMessage(Component.text("[Shop] config.yml 재로드 완료. 등록 아이템 수: "
                + registry.getAll().size()).color(NamedTextColor.GREEN));
    }

    private void handleSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /shop spawn <mineral|crop|lamp|fishing|general|dungeon|cooking> [스킨닉네임]").color(NamedTextColor.YELLOW));
            return;
        }
        ShopCategory category = parseCategory(args[1]);
        if (category == null) {
            player.sendMessage(Component.text("유효한 카테고리: mineral, crop, lamp, fishing, general, dungeon, cooking").color(NamedTextColor.RED));
            return;
        }

        String skinName = args.length >= 3 ? args[2] : player.getName();

        player.sendMessage(Component.text("[Shop] NPC 생성 중... (스킨 로딩)").color(NamedTextColor.GRAY));
        npcManager.spawnNPC(player.getLocation(), category, skinName);
        player.sendMessage(
                Component.text("[Shop] ").color(NamedTextColor.GOLD)
                        .append(Component.text(category.getNpcName() + " NPC 생성 완료 (스킨: " + skinName + ")").color(NamedTextColor.GREEN))
        );
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("사용법: /shop remove <mineral|crop|lamp|fishing|general>").color(NamedTextColor.YELLOW));
            return;
        }
        ShopCategory category = parseCategory(args[1]);
        if (category == null) {
            player.sendMessage(Component.text("유효한 카테고리: mineral, crop, lamp, fishing, general, dungeon, cooking").color(NamedTextColor.RED));
            return;
        }

        if (npcManager.removeNPC(category)) {
            player.sendMessage(
                    Component.text("[Shop] ").color(NamedTextColor.GOLD)
                            .append(Component.text(category.getNpcName() + " NPC를 제거했습니다.").color(NamedTextColor.GREEN))
            );
        } else {
            player.sendMessage(Component.text("해당 NPC가 존재하지 않습니다.").color(NamedTextColor.RED));
        }
    }

    private void handleSetSkin(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("사용법: /shop setskin <mineral|crop|lamp|fishing|general> <플레이어명>").color(NamedTextColor.YELLOW));
            return;
        }
        ShopCategory category = parseCategory(args[1]);
        if (category == null) {
            player.sendMessage(Component.text("유효한 카테고리: mineral, crop, lamp, fishing, general, dungeon, cooking").color(NamedTextColor.RED));
            return;
        }

        String skinName = args[2];
        player.sendMessage(Component.text("[Shop] 스킨 변경 중...").color(NamedTextColor.GRAY));

        if (npcManager.setSkin(category, skinName)) {
            player.sendMessage(
                    Component.text("[Shop] ").color(NamedTextColor.GOLD)
                            .append(Component.text(category.getNpcName() + " 스킨 → " + skinName).color(NamedTextColor.GREEN))
            );
        } else {
            player.sendMessage(Component.text("해당 NPC가 없습니다. 먼저 /shop spawn 해주세요.").color(NamedTextColor.RED));
        }
    }

    private void handlePrices(Player player, String[] args) {
        ShopCategory filter = args.length >= 2 ? parseCategory(args[1]) : null;

        player.sendMessage(Component.text("═══ 현재 시세 ═══").color(NamedTextColor.GOLD));
        for (ShopItem item : registry.getAll()) {
            if (filter != null && item.getCategory() != filter) continue;

            Component line = Component.text(" " + item.getDisplayName()).color(NamedTextColor.WHITE);
            if (item.isBuyable()) {
                long buyPrice = priceManager.getBuyPrice(item);
                line = line.append(Component.text(" | 구매: " + buyPrice + "w").color(NamedTextColor.GRAY));
            }
            if (item.isSellable()) {
                long sellPrice = priceManager.getSellPrice(item);
                String fluct = priceManager.getFluctuationDisplay(item);
                line = line.append(Component.text(" | 판매: " + sellPrice + "w ").color(NamedTextColor.GRAY))
                           .append(Component.text(fluct));
            } else {
                line = line.append(Component.text(" | 판매: -").color(NamedTextColor.DARK_GRAY));
            }
            player.sendMessage(line);
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("═══ Shop 명령어 ═══").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text(" /shop spawn <mineral|crop|lamp|fishing|general|dungeon|cooking> [스킨]").color(NamedTextColor.YELLOW)
                .append(Component.text(" — NPC 생성").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /shop remove <mineral|crop|lamp|fishing|general>").color(NamedTextColor.YELLOW)
                .append(Component.text(" — NPC 제거").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /shop setskin <mineral|crop|lamp|fishing|general> <닉네임>").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 스킨 변경").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /shop prices [mineral|crop|lamp|general]").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 시세 확인 (fishing 제외)").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /shop reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" — config.yml 재로드").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text(" /shop report").color(NamedTextColor.YELLOW)
                .append(Component.text(" — 일일 리포트 강제 생성 + git push").color(NamedTextColor.GRAY)));
    }

    private ShopCategory parseCategory(String input) {
        return switch (input.toLowerCase()) {
            case "mineral", "광물" -> ShopCategory.MINERAL;
            case "crop", "작물"   -> ShopCategory.CROP;
            case "lamp", "램프"   -> ShopCategory.LAMP;
            case "fishing", "낚시" -> ShopCategory.FISHING;
            case "general", "잡화" -> ShopCategory.GENERAL;
            case "dungeon", "던전" -> ShopCategory.DUNGEON;
            case "cooking", "요리" -> ShopCategory.COOKING;
            default               -> null;
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return filterPrefix(Arrays.asList("spawn", "remove", "setskin", "prices", "reload"), args[0]);
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload"))
            return filterPrefix(Arrays.asList("mineral", "crop", "lamp", "fishing", "general", "dungeon", "cooking"), args[1]);
        if (args.length == 3 && (args[0].equalsIgnoreCase("setskin") || args[0].equalsIgnoreCase("spawn")))
            return null; // Bukkit 온라인 플레이어 자동완성
        return List.of();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
