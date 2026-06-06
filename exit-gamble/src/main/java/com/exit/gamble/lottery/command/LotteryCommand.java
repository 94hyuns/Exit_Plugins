package com.exit.gamble.lottery.command;

import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.lottery.DrawHistory;
import com.exit.gamble.lottery.LotteryManager;
import com.exit.gamble.lottery.LotteryManager.PurchaseResult;
import com.exit.gamble.lottery.Ticket;
import com.exit.gamble.lottery.gui.LotteryGui;
import com.exit.gamble.lottery.scheduler.LotteryScheduler;
import com.exit.gamble.slot.world.SlotMachineManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class LotteryCommand implements CommandExecutor {

    private static final SimpleDateFormat FMT = new SimpleDateFormat("MM-dd HH:mm");

    private final LotteryManager lottery;
    private final LotteryGui gui;

    public LotteryCommand(LotteryManager lottery, LotteryGui gui) {
        this.lottery = lottery;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        switch (command.getName()) {
            case "복권" -> openGui(sender);
            case "복권구매" -> handleBuy(sender, args);
            case "복권내역" -> handleMyTickets(sender);
            case "복권상태" -> handleStatus(sender);
            case "복권결과" -> handleHistory(sender, args);
            case "복권추첨" -> handleForceDraw(sender);
            case "복권NPC스폰" -> handleNpcSpawn(sender, args);
            case "복권NPC제거" -> handleNpcRemove(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void openGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        gui.open(player);
    }

    private void handleBuy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        int count = 1;
        if (args.length >= 1) {
            try { count = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                player.sendMessage(Component.text("사용법: /복권구매 [수량]", NamedTextColor.RED));
                return;
            }
        }
        PurchaseResult res = lottery.buy(player, count);
        if (!res.success()) {
            player.sendMessage(Component.text("[복권] " + res.error(), NamedTextColor.RED));
            return;
        }
        int digits = String.valueOf(lottery.numberRange() - 1).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < res.numbers().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%0" + digits + "d", res.numbers().get(i)));
        }
        player.sendMessage(Component.text("[복권] " + count + "장 구매 (-" +
                String.format("%,d", res.totalCost()) + "원). 번호: ", NamedTextColor.GREEN)
                .append(Component.text(sb.toString(), NamedTextColor.AQUA)));
    }

    private void handleMyTickets(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        List<Ticket> mine = lottery.ticketsOf(player.getUniqueId());
        if (mine.isEmpty()) {
            player.sendMessage(Component.text("[복권] 보유 티켓 없음", NamedTextColor.GRAY));
            return;
        }
        int digits = String.valueOf(lottery.numberRange() - 1).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mine.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%0" + digits + "d", mine.get(i).number()));
        }
        player.sendMessage(Component.text("[복권] 내 티켓 " + mine.size() + "장: ", NamedTextColor.YELLOW)
                .append(Component.text(sb.toString(), NamedTextColor.AQUA)));
    }

    private void handleStatus(CommandSender sender) {
        long ms = LotteryScheduler.msUntilNextHour();
        long min = ms / 60_000L;
        long sec = (ms / 1000L) % 60L;
        sender.sendMessage(Component.text("[복권] 현재 풀: ", NamedTextColor.YELLOW)
                .append(Component.text(String.format("%,d", lottery.pot()) + "원", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("       다음 추첨까지: " + min + "분 " + sec + "초",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text("       전체 티켓: " + lottery.totalTickets() + "장   /   서버 연속 이월: "
                + lottery.rolloverCount() + "회",
                NamedTextColor.GRAY));
        if (sender instanceof Player p) {
            int mine = lottery.ticketsOf(p.getUniqueId()).size();
            int boughtCycle = lottery.boughtThisCycle(p.getUniqueId());
            int myLimit = lottery.cycleLimitFor(p.getUniqueId());
            int canBuy = Math.max(0, myLimit - boughtCycle);
            int bonus = lottery.playerBonusCount(p.getUniqueId());
            sender.sendMessage(Component.text("       내 티켓 (총): " + mine + "장   /   이번 회차: "
                    + boughtCycle + "/" + myLimit + "장 (+" + canBuy + " 가능)",
                    NamedTextColor.AQUA));
            if (bonus > 0) {
                sender.sendMessage(Component.text("       내 누적 보너스: +"
                        + (bonus * lottery.ticketBonusPerRollover()) + "장 (이월 참여 " + bonus + "회)",
                        NamedTextColor.GOLD));
            }
        }
        sender.sendMessage(Component.text("       누적 추첨 회차: " + lottery.totalDraws() + "회   /   매 회차 시드: +"
                + String.format("%,d", lottery.seedPerDraw()) + "원",
                NamedTextColor.DARK_GRAY));
    }

    private void handleHistory(CommandSender sender, String[] args) {
        int limit = 5;
        if (args.length >= 1) {
            try { limit = Math.min(20, Math.max(1, Integer.parseInt(args[0]))); }
            catch (NumberFormatException ignored) {}
        }
        List<DrawHistory> hist = lottery.recentHistory(limit);
        if (hist.isEmpty()) {
            sender.sendMessage(Component.text("[복권] 추첨 이력 없음", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("[복권] 최근 " + hist.size() + "회 추첨 결과:", NamedTextColor.YELLOW));
        int digits = String.valueOf(lottery.numberRange() - 1).length();
        for (DrawHistory h : hist) {
            String time = FMT.format(new Date(h.drawTime()));
            String numStr = String.format("%0" + digits + "d", h.winningNumber());
            if (h.hasWinner()) {
                sender.sendMessage(Component.text(" " + time + "  " + numStr + "  ", NamedTextColor.GRAY)
                        .append(Component.text(String.join(", ", h.winnerNames()), NamedTextColor.AQUA))
                        .append(Component.text(" (1인당 " + String.format("%,d", h.perPersonPayout()) + "원)",
                                NamedTextColor.GOLD)));
            } else {
                sender.sendMessage(Component.text(" " + time + "  " + numStr + "  꽝 → " +
                        String.format("%,d", h.potBeforeDraw()) + "원 이월", NamedTextColor.DARK_GRAY));
            }
        }
    }

    private void handleForceDraw(CommandSender sender) {
        sender.sendMessage(Component.text("[복권] 강제 추첨 실행", NamedTextColor.YELLOW));
        lottery.draw();
    }

    private void handleNpcSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용 가능", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("사용법: /복권NPC스폰 <id> [스킨플레이어이름]", NamedTextColor.RED));
            return;
        }
        NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
        if (npc == null) {
            player.sendMessage(Component.text("Core NpcService 미연동", NamedTextColor.RED));
            return;
        }
        String npcId = "lottery_" + args[0];
        if (npc.get(SlotMachineManager.NPC_OWNER, npcId).isPresent()) {
            player.sendMessage(Component.text("이미 존재하는 NPC: " + npcId, NamedTextColor.RED));
            return;
        }
        String skin = args.length >= 2 ? args[1] : player.getName();
        String displayName = args[0];   // 사용자가 입력한 raw id 를 그대로 머리 위 이름으로
        boolean ok = npc.spawn(new NpcSpawnSpec(
                SlotMachineManager.NPC_OWNER, npcId, player.getLocation(), displayName, skin, true));
        if (ok) {
            player.sendMessage(Component.text("[복권] NPC '" + npcId + "' 스폰 완료 (스킨: " + skin + ")",
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("[복권] NPC 스폰 실패", NamedTextColor.RED));
        }
    }

    private void handleNpcRemove(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text("사용법: /복권NPC제거 <id>", NamedTextColor.RED));
            return;
        }
        NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
        if (npc == null) {
            sender.sendMessage(Component.text("Core NpcService 미연동", NamedTextColor.RED));
            return;
        }
        String npcId = "lottery_" + args[0];
        boolean ok = npc.remove(SlotMachineManager.NPC_OWNER, npcId);
        sender.sendMessage(Component.text(
                ok ? "[복권] NPC '" + npcId + "' 제거" : "NPC '" + npcId + "' 없음",
                ok ? NamedTextColor.GREEN : NamedTextColor.RED));
    }
}
