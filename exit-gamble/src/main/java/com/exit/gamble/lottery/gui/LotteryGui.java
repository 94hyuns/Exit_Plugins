package com.exit.gamble.lottery.gui;

import com.exit.gamble.lottery.LotteryManager;
import com.exit.gamble.lottery.Ticket;
import com.exit.gamble.lottery.scheduler.LotteryScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class LotteryGui {

    public static final int STATUS_SLOT = 4;
    public static final int INFO_SLOT = 5;
    public static final int CLOSE_SLOT = 8;
    public static final int BUY_1_SLOT = 11;
    public static final int BUY_5_SLOT = 13;
    public static final int BUY_10_SLOT = 15;
    public static final int MY_TICKETS_SLOT = 22;

    public static final String ACTION_BUY_1 = "LOTTERY_BUY_1";
    public static final String ACTION_BUY_5 = "LOTTERY_BUY_5";
    public static final String ACTION_BUY_10 = "LOTTERY_BUY_10";
    public static final String ACTION_MY_TICKETS = "LOTTERY_MY_TICKETS";
    public static final String ACTION_CLOSE = "LOTTERY_CLOSE";

    private final Plugin plugin;
    private final LotteryManager lottery;
    private final NamespacedKey actionKey;

    public LotteryGui(Plugin plugin, LotteryManager lottery) {
        this.plugin = plugin;
        this.lottery = lottery;
        this.actionKey = new NamespacedKey(plugin, "lottery_action");
    }

    public NamespacedKey actionKey() { return actionKey; }

    public void open(Player player) {
        LotteryHolder holder = new LotteryHolder(player);
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("복권 판매소", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        holder.setInventory(inv);

        fillBackground(inv);
        renderStatus(inv, player);
        renderInfo(inv);
        renderBuyButton(inv, BUY_1_SLOT, 1, ACTION_BUY_1);
        renderBuyButton(inv, BUY_5_SLOT, 5, ACTION_BUY_5);
        renderBuyButton(inv, BUY_10_SLOT, 10, ACTION_BUY_10);
        renderMyTickets(inv, player);
        renderClose(inv);

        player.openInventory(inv);
    }

    public void renderStatus(Inventory inv, Player player) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("복권 현황", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        long msUntil = LotteryScheduler.msUntilNextHour();
        long minutes = msUntil / 60_000L;
        long seconds = (msUntil / 1000L) % 60L;

        int myCount = lottery.ticketsOf(player.getUniqueId()).size();
        int cycleLimit = lottery.cycleLimitFor(player.getUniqueId());
        int boughtCycle = lottery.boughtThisCycle(player.getUniqueId());
        int canBuyMore = Math.max(0, cycleLimit - boughtCycle);
        int myBonus = lottery.playerBonusCount(player.getUniqueId());

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("현재 상금 풀: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d", lottery.pot()) + "원", NamedTextColor.GOLD))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("다음 추첨까지: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%d분 %d초", minutes, seconds), NamedTextColor.AQUA))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ", NamedTextColor.GRAY));
        lore.add(Component.text("내 티켓 (총): ", NamedTextColor.GRAY)
                .append(Component.text(myCount + "장", NamedTextColor.WHITE))
                .append(Component.text("  /  전체 풀: " + lottery.totalTickets() + "장", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("이번 회차 구매: ", NamedTextColor.GRAY)
                .append(Component.text(boughtCycle + " / " + cycleLimit + "장", NamedTextColor.WHITE))
                .append(Component.text("  (추가 가능 " + canBuyMore + "장)", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        if (myBonus > 0) {
            lore.add(Component.text("내 누적 보너스: ", NamedTextColor.GRAY)
                    .append(Component.text("+" + (myBonus * lottery.ticketBonusPerRollover()) + "장",
                            NamedTextColor.GOLD))
                    .append(Component.text(" (참여 " + myBonus + "회)", NamedTextColor.DARK_GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (lottery.rolloverCount() > 0) {
            lore.add(Component.text("서버 연속 이월: ", NamedTextColor.GRAY)
                    .append(Component.text(lottery.rolloverCount() + "회",
                            lottery.rolloverCount() >= 5 ? NamedTextColor.GOLD : NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(STATUS_SLOT, item);
    }

    private void renderInfo(Inventory inv) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("복권 정보", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        int totalTickets = lottery.totalTickets();
        double winProb = totalTickets > 0
                ? (1.0 - Math.pow(1.0 - 1.0 / lottery.numberRange(), totalTickets)) * 100.0
                : 0.0;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("티켓 1장: " + String.format("%,d", lottery.ticketPrice()) + "원",
                NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("번호 범위: 0 ~ " + (lottery.numberRange() - 1),
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("판매액 100% → 상금 풀 적립",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("매 회차 시드 ", NamedTextColor.GRAY)
                .append(Component.text("+" + String.format("%,d", lottery.seedPerDraw()) + "원",
                        NamedTextColor.GREEN))
                .append(Component.text(" 자동 적립", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ", NamedTextColor.GRAY));
        lore.add(Component.text("매 정각 자동 추첨 — 안 터지면 다음 정각으로 이월",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("이월 시 그 회차 구매자에게만 한도 +"
                + lottery.ticketBonusPerRollover() + "장 (개인별 누적)",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("티켓은 회차 단위 — 추첨 후 모두 소멸 (이월돼도 새로 사야 함)",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("당첨 시 모든 카운터 리셋 (보너스 0 으로)",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ", NamedTextColor.GRAY));
        lore.add(Component.text(String.format("현재 풀 기준 회당 당첨 확률: %.2f%%", winProb),
                NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        inv.setItem(INFO_SLOT, item);
    }

    private void renderBuyButton(Inventory inv, int slot, int count, String action) {
        long total = lottery.ticketPrice() * count;
        ItemStack item = new ItemStack(Material.NAME_TAG, count);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(count + "장 구매", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("가격: " + String.format("%,d", total) + "원", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("랜덤 번호 자동 부여", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void renderMyTickets(Inventory inv, Player player) {
        int count = lottery.ticketsOf(player.getUniqueId()).size();
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("내 티켓 보기", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("보유: " + count + "장", NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("클릭 시 채팅에 번호 출력", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, ACTION_MY_TICKETS);
        item.setItemMeta(meta);
        inv.setItem(MY_TICKETS_SLOT, item);
    }

    private void renderClose(Inventory inv) {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("닫기", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, ACTION_CLOSE);
        item.setItemMeta(meta);
        inv.setItem(CLOSE_SLOT, item);
    }

    private void fillBackground(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i == STATUS_SLOT || i == INFO_SLOT || i == CLOSE_SLOT
                    || i == BUY_1_SLOT || i == BUY_5_SLOT || i == BUY_10_SLOT
                    || i == MY_TICKETS_SLOT) continue;
            inv.setItem(i, filler);
        }
    }

    /** 내 티켓 번호를 채팅으로 출력 (MY_TICKETS 클릭 시 호출). */
    public void sendMyTicketsChat(Player player) {
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
}
