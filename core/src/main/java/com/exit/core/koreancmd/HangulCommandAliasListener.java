package com.exit.core.koreancmd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * 모든 플러그인의 한글 명령어/별칭을 자동 스캔하여 두벌식 romanization 으로도 실행 가능하게 한다.
 *
 * <p>예: 플러그인 commands.yml 에 {@code aliases: [스킬정보]} 만 등록되어 있어도,
 * 사용자가 IME 끈 상태로 {@code /tmzlfwjdqh} 입력하면 자동으로 {@code /스킬정보} 로 라우팅됨.
 *
 * <p><b>두 가지 메커니즘 동시 적용 (Core 1.3.2+)</b>:
 * <ol>
 *   <li><b>RomanizationCommand 동적 등록</b> (Brigadier 자동완성 지원): 각 한글 명령어의
 *       두벌식 romanization 을 진짜 Bukkit 명령어로 등록 → 클라이언트 자동완성 후보에 노출.
 *       실행 시 {@link Bukkit#dispatchCommand} 로 한글 원본 명령어에 위임.</li>
 *   <li><b>PlayerCommandPreprocessEvent 백업 라우팅</b>: 어떤 이유로 alias 등록이
 *       실패하더라도 사용자가 두벌식 끝까지 친 후 엔터 시 메시지 재작성으로 정상 실행 보장.</li>
 * </ol>
 *
 * <p>플러그인이 늦게 로드되어도 {@link PluginEnableEvent} 로 매핑이 갱신되며,
 * Core 자체 onEnable 후 한 번 지연 스캔도 수행해 누락 방지.
 *
 * <p><b>Trade-off</b>: 자동완성 후보에는 한글 + romanization 둘 다 노출됨.
 * 깔끔하게 한글만 노출하는 건 Brigadier 한계로 불가능.
 */
public final class HangulCommandAliasListener implements Listener {

    private final Plugin core;
    /** romanization (lower-cased) → 한글 명령어. 모든 등록된 한글 명령어/별칭의 매핑. */
    private final Map<String, String> romanizationToHangul = new HashMap<>();
    /** 이미 Bukkit CommandMap 에 등록한 romanization 추적 (중복 등록 방지). */
    private final Set<String> registeredRomanizations = new HashSet<>();

    public HangulCommandAliasListener(Plugin core) {
        this.core = core;
    }

    /** 모든 플러그인 스캔하여 매핑 재구축 후 RomanizationCommand 등록. */
    public void rebuild() {
        romanizationToHangul.clear();
        Logger logger = core.getLogger();
        int count = 0;
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            PluginDescriptionFile desc = p.getDescription();
            Map<String, Map<String, Object>> cmds = desc.getCommands();
            if (cmds == null) continue;
            for (Map.Entry<String, Map<String, Object>> entry : cmds.entrySet()) {
                String name = entry.getKey();
                if (HangulRomanizer.containsHangul(name)) {
                    if (registerMapping(name)) count++;
                }
                Object aliasesObj = entry.getValue() == null ? null : entry.getValue().get("aliases");
                if (aliasesObj instanceof Collection<?> col) {
                    for (Object a : col) {
                        if (a == null) continue;
                        String alias = a.toString();
                        if (HangulRomanizer.containsHangul(alias)) {
                            if (registerMapping(alias)) count++;
                        }
                    }
                } else if (aliasesObj instanceof String s) {
                    if (HangulRomanizer.containsHangul(s)) {
                        if (registerMapping(s)) count++;
                    }
                }
            }
        }
        if (count > 0) {
            logger.info("[Core] 한글 명령어 두벌식 매핑 " + count + "개 등록 (preprocess rewrite 만 활성).");
        }
        // registerRomanizationCommands() 는 호출하지 않음 — autocomplete 에 /vhxkf /wlrdjqwjdqh 같은
        // romanization 명령어가 노출되는 게 거슬려서 비활성화 (Core 1.3.0+). 사용자가 IME 끈 채로
        // 두벌식 영문을 끝까지 친 후 엔터 시에는 onPreprocess 에서 한글로 rewrite 해주므로 기능적으로는 동일.
    }

    private boolean registerMapping(String hangul) {
        String eng = HangulRomanizer.toEng(hangul).toLowerCase(Locale.ROOT);
        if (eng.isEmpty() || eng.equals(hangul)) return false;
        return romanizationToHangul.putIfAbsent(eng, hangul) == null;
    }

    /**
     * romanization 들을 진짜 Bukkit 명령어로 등록 → Brigadier 자동완성에 노출.
     * <p>이름 형태: {@code <roman>(<hangul>)} 예: {@code tmzlfwjdqh(스킬정보)}.
     * 자동완성 후보에 한글 의미가 함께 보임. 사용자는 Tab 으로 그대로 채워 넣고 엔터.
     * <p>parens 가 Brigadier 에서 거부되면 plain {@code <roman>} 으로 폴백.
     */
    private void registerRomanizationCommands() {
        CommandMap cm = Bukkit.getCommandMap();
        if (cm == null) return;
        int newCount = 0;
        for (Map.Entry<String, String> e : romanizationToHangul.entrySet()) {
            String roman = e.getKey();
            String hangul = e.getValue();
            if (registeredRomanizations.contains(roman)) continue;

            String decorated = roman + "(" + hangul + ")";
            boolean registered = false;

            // 1차: 한글 표시가 붙은 decorated 이름으로 시도
            if (cm.getCommand(decorated) == null) {
                try {
                    Command cmd = new RomanizationCommand(decorated, hangul);
                    if (cm.register("core", cmd)) registered = true;
                } catch (Throwable ignored) {
                    // parens 미지원 등 — 다음 폴백으로
                }
            }

            // 2차 폴백: parens 가 안 되면 plain roman 으로
            if (!registered && cm.getCommand(roman) == null) {
                try {
                    Command cmd = new RomanizationCommand(roman, hangul);
                    if (cm.register("core", cmd)) registered = true;
                } catch (Throwable ignored) {}
            }

            if (registered) {
                registeredRomanizations.add(roman);
                newCount++;
            }
        }
        if (newCount > 0) {
            core.getLogger().info("[Core] 두벌식 자동완성용 alias " + newCount + "개 등록.");
            syncBrigadier();
        }
    }

    /** Paper 의 CraftServer.syncCommands() 를 reflection 으로 호출해 Brigadier 트리 갱신. */
    private void syncBrigadier() {
        try {
            Object server = Bukkit.getServer();
            java.lang.reflect.Method m = server.getClass().getDeclaredMethod("syncCommands");
            m.setAccessible(true);
            m.invoke(server);
        } catch (Throwable t) {
            core.getLogger().warning("[Core] syncCommands 실패 (자동완성 갱신은 다음 접속 시 반영): "
                    + t.getMessage());
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        rebuild();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        // 두 케이스 처리:
        // 1. plain romanization (예: /tmzlfwjdqh rjdltkdsnss) — 명령어+인자 모두 한글로 rewrite
        // 2. decorated 이름 (예: /tmzlfwjdqh(스킬정보) rjdltkdsnss) — RomanizationCommand 가 인자 변환
        Player player = event.getPlayer();
        if (player == null) return;
        String msg = event.getMessage();
        if (msg == null || !msg.startsWith("/") || msg.length() < 2) return;

        int spaceIdx = msg.indexOf(' ');
        String cmdWord = (spaceIdx == -1) ? msg.substring(1) : msg.substring(1, spaceIdx);
        String rest    = (spaceIdx == -1) ? "" : msg.substring(spaceIdx);

        // decorated 형태 (parens 포함) 면 RomanizationCommand 가 처리하므로 여기선 손대지 않음
        if (cmdWord.indexOf('(') >= 0) return;

        // plain romanization 이면 명령어부분 한글 rewrite + 인자 부분도 한글 변환
        String mapped = romanizationToHangul.get(cmdWord.toLowerCase(Locale.ROOT));
        if (mapped != null) {
            String convertedRest = rest.isEmpty() ? "" : " " + HangulRomanizer.engToHangul(rest.substring(1));
            event.setMessage("/" + mapped + convertedRest);
        }
    }

    /** 두벌식 romanization 명령어 — 실행 시 인자 한글 변환 후 한글 원본 명령어로 dispatch. */
    private static final class RomanizationCommand extends Command {
        private final String hangul;

        RomanizationCommand(String name, String hangul) {
            super(name);
            this.hangul = hangul;
            setDescription("두벌식 alias for /" + hangul);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            // 인자도 두벌식 영문일 가능성이 높으므로 한글로 변환 후 dispatch
            StringBuilder full = new StringBuilder(hangul);
            for (String a : args) {
                full.append(' ').append(HangulRomanizer.engToHangul(a));
            }
            return Bukkit.dispatchCommand(sender, full.toString());
        }
    }
}
