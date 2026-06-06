package com.exit.cosmetics.command;

import com.exit.core.api.CosmeticProvider;
import com.exit.cosmetics.CosmeticsPlugin;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.npc.CosmeticNpcManager;
import com.exit.cosmetics.registry.CosmeticRegistry;
import com.exit.cosmetics.ticket.TicketManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * /치장 관리자 명령어.
 *
 * <ul>
 *   <li>/치장 setnpc — 현재 위치에 NPC 설치</li>
 *   <li>/치장 removenpc — NPC 제거</li>
 *   <li>/치장 give &lt;player&gt; &lt;cosmeticId&gt; — 치장 지급</li>
 *   <li>/치장 giveticket &lt;player&gt; [amount] — 뽑기권 지급 (기본 1장)</li>
 *   <li>/치장 reload — config 재로드</li>
 * </ul>
 */
public class CosmeticCommand implements CommandExecutor, TabCompleter {

    private final CosmeticsPlugin plugin;
    private final CosmeticNpcManager npcManager;
    private final CosmeticProvider provider;
    private final CosmeticRegistry registry;
    private final TicketManager ticketManager;

    public CosmeticCommand(CosmeticsPlugin plugin, CosmeticNpcManager npcManager,
                           CosmeticProvider provider, CosmeticRegistry registry,
                           TicketManager ticketManager) {
        this.plugin = plugin;
        this.npcManager = npcManager;
        this.provider = provider;
        this.registry = registry;
        this.ticketManager = ticketManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setnpc" -> handleSetNpc(sender);
            case "removenpc" -> handleRemoveNpc(sender);
            case "give" -> handleGive(sender, args);
            case "giveticket" -> handleGiveTicket(sender, args, false);
            case "givemountticket" -> handleGiveTicket(sender, args, true);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSetNpc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능합니다.").color(NamedTextColor.RED));
            return;
        }
        npcManager.relocateNpc(player.getLocation());
        player.sendMessage(Component.text("치장 상인 NPC가 현재 위치에 설치되었습니다.").color(NamedTextColor.GREEN));
    }

    private void handleRemoveNpc(CommandSender sender) {
        npcManager.removeNpc();
        sender.sendMessage(Component.text("치장 상인 NPC가 제거되었습니다.").color(NamedTextColor.GREEN));
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("사용법: /치장 give <player> <cosmeticId>").color(NamedTextColor.YELLOW));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("플레이어를 찾을 수 없습니다: " + args[1]).color(NamedTextColor.RED));
            return;
        }
        String cosmeticId = args[2];
        CosmeticDefinition def = registry.get(cosmeticId);
        if (def == null) {
            sender.sendMessage(Component.text("알 수 없는 치장 ID: " + cosmeticId).color(NamedTextColor.RED));
            return;
        }
        if (provider.grantCosmetic(target.getUniqueId(), cosmeticId)) {
            sender.sendMessage(Component.text(target.getName() + " 에게 " + def.getDisplayName() + " §a지급 완료"));
        } else {
            sender.sendMessage(Component.text("지급 실패 (이미 보유 중이거나 DB 오류)").color(NamedTextColor.YELLOW));
        }
    }

    private void handleGiveTicket(CommandSender sender, String[] args, boolean mountKind) {
        String label = mountKind ? "givemountticket" : "giveticket";
        String ticketKor = mountKind ? "탈것 뽑기권" : "치장 뽑기권";
        if (args.length < 2) {
            sender.sendMessage(Component.text("사용법: /치장 " + label + " <player> [amount]").color(NamedTextColor.YELLOW));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("온라인 상태의 플레이어만 지급 가능합니다: " + args[1]).color(NamedTextColor.RED));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); }
            catch (NumberFormatException e) {
                sender.sendMessage(Component.text("수량은 1~64 사이 정수여야 합니다.").color(NamedTextColor.RED));
                return;
            }
        }

        ItemStack ticket = mountKind ? ticketManager.createMountTicket(amount) : ticketManager.createTicket(amount);
        Map<Integer, ItemStack> leftover = target.getInventory().addItem(ticket);
        if (!leftover.isEmpty()) {
            for (ItemStack left : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }
        }
        sender.sendMessage(Component.text(target.getName() + " 에게 " + ticketKor + " " + amount + "장 지급.").color(NamedTextColor.GREEN));
        target.sendMessage(Component.text(ticketKor + " " + amount + "장을 받았습니다!").color(NamedTextColor.AQUA));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadAll();
        sender.sendMessage(Component.text("카탈로그/뽑기권/연출 설정 재로드 완료.").color(NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("════ 치장 관리자 명령어 ════").color(NamedTextColor.AQUA));

        sender.sendMessage(Component.text("[NPC]").color(NamedTextColor.YELLOW));
        sendCmd(sender, "/치장 setnpc",                    "현재 위치에 치장 상인 설치");
        sendCmd(sender, "/치장 removenpc",                 "치장 상인 제거");

        sender.sendMessage(Component.text("[지급]").color(NamedTextColor.YELLOW));
        sendCmd(sender, "/치장 give <player> <id>",        "특정 치장을 직접 지급");
        sendCmd(sender, "/치장 giveticket <player> [n]",   "치장 뽑기권 지급 (기본 1장)");
        sendCmd(sender, "/치장 givemountticket <p> [n]",   "탈것 뽑기권 지급 (기본 1장)");

        sender.sendMessage(Component.text("[기타]").color(NamedTextColor.YELLOW));
        sendCmd(sender, "/치장 reload",                    "config / mounts.yml 재로드 + NPC 재스폰");
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("플레이어용: /ride 또는 /탈것 - 보유 탈것 GUI")
                .color(NamedTextColor.DARK_GRAY));
    }

    private void sendCmd(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(Component.text("  " + cmd, NamedTextColor.GRAY)
                .append(Component.text(" — " + desc, NamedTextColor.DARK_GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("setnpc", "removenpc", "give", "giveticket", "givemountticket", "reload"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveticket")
                || args[0].equalsIgnoreCase("givemountticket"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return filter(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> ids = registry.getAll().stream()
                    .map(CosmeticDefinition::getId)
                    .collect(Collectors.toList());
            return filter(ids, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> src, String prefix) {
        String p = prefix.toLowerCase();
        return src.stream().filter(s -> s.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }
}
