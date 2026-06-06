package com.exit.core.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /도움 - 초보자가 사용 가능한 일반 유저용 명령어 안내.
 *
 * 출력 형식:
 *   - 한글 명령어 우선, 영문/두벌식 alias 병기
 *   - 일부 카테고리만 그룹화 (땅 / 울캐쉬), 나머지는 평탄 나열
 *   - 볼드 사용 금지 (TTF 폰트 호환성)
 */
public final class HelpCommand implements CommandExecutor {

    private static final NamedTextColor C_HEADER = NamedTextColor.GOLD;
    private static final NamedTextColor C_CMD    = NamedTextColor.YELLOW;
    private static final NamedTextColor C_ALIAS  = NamedTextColor.GRAY;
    private static final NamedTextColor C_DASH   = NamedTextColor.DARK_GRAY;
    private static final NamedTextColor C_DESC   = NamedTextColor.WHITE;
    private static final NamedTextColor C_NOTE   = NamedTextColor.GRAY;

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("플레이어만 사용할 수 있는 명령어입니다.").color(NamedTextColor.RED));
            return true;
        }

        // 헤더
        player.sendMessage(Component.text("══════ ", C_HEADER)
                .append(Component.text("초보자 도움말", NamedTextColor.YELLOW))
                .append(Component.text(" ══════", C_HEADER)));
        player.sendMessage(Component.text("명령어를 사용해서 서버를 즐겨보세요!", C_NOTE));
        player.sendMessage(Component.empty());

        // ── 평탄 영역 ──
        sendCmd(player, "/탈것",     "/ride, /xkfrjt",                       "탈것 GUI 를 엽니다");
        sendCmd(player, "/포탈",     "/portal, /vhxkf",                      "포탈 GUI 를 엽니다");
        sendCmd(player, "/낚시도감", "/도감, /skzltehrka",                   "어류 도감을 확인합니다");
        sendCmd(player, "/계절",     "/rPwjf",                               "현재 계절을 확인합니다");
        sendCmd(player, "/직업정보", "/wlrdjqwjdqh",                         "내 직업 현황을 확인합니다");
        sendCmd(player, "/경작지정보", "/rudwkrwlwjdqh",                     "바라보는 경작지의 정보를 확인합니다");
        sendCmd(player, "/스킬목록", "/skilllist, /tmzlffhrurp",             "인챈트(스킬) 목록을 확인합니다");
        sendCmd(player, "/스킬정보", "/skillinfo, /tmzlfwjdqh",              "인챈트(스킬) 상세 정보를 확인합니다");
        sendCmd(player, "/램프작업대 open", "/lampbulk open, /fosvmwkrdjqeo open", "램프 작업대 GUI 를 엽니다");

        player.sendMessage(Component.empty());

        // ── 그룹: 청크(땅) 관리 ──
        player.sendMessage(Component.text("▶ 청크(땅) 관리 ", C_HEADER)
                .append(Component.text("(/땅 = /land = /Eka)", C_ALIAS)));
        sendSubCmd(player, "/땅 구매", "현재 청크를 구매합니다");
        sendSubCmd(player, "/땅 반환", "현재 청크를 반환합니다 (75% 환불)");
        sendSubCmd(player, "/땅 목록", "내 청크 목록을 확인합니다");
        sendSubCmd(player, "/땅 정보", "청크 정보 모드를 토글합니다");

        player.sendMessage(Component.empty());

        // ── 그룹: 울캐쉬 ──
        player.sendMessage(Component.text("▶ 울캐쉬 (화폐)", C_HEADER));
        sendCmd(player, "/출금 <금액>", "/withdraw, /chfrma", "잔액에서 울캐쉬 종이를 출금합니다");
        sendCmd(player, "/입금",        "/deposit, /dlqrma",  "들고 있는 울캐쉬 종이를 입금합니다");

        // 푸터
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("═══════════════════════", C_HEADER));

        return true;
    }

    /** 한 줄: "/명령어 (alias) - 설명" */
    private void sendCmd(Player p, String cmd, String aliases, String desc) {
        Component line = Component.text(cmd + " ", C_CMD)
                .append(Component.text("(" + aliases + ") ", C_ALIAS))
                .append(Component.text("- ", C_DASH))
                .append(Component.text(desc, C_DESC));
        p.sendMessage(line);
    }

    /** 서브커맨드(별칭 묶음으로 이미 안내한 경우): "/명령어 - 설명" */
    private void sendSubCmd(Player p, String cmd, String desc) {
        Component line = Component.text(cmd + " ", C_CMD)
                .append(Component.text("- ", C_DASH))
                .append(Component.text(desc, C_DESC));
        p.sendMessage(line);
    }
}
