package com.exit.customitems.lamp.enchant;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * 등록된 인챈트를 사람이 읽기 좋은 HTML 표로 dump.
 *
 * <p>출력 경로: {@code plugins/CustomItems/enchantslist.html}.
 * 다크 테마 + 카테고리별 색상 분류 + 표. 브라우저에서 열면 한글 폰트 자동 적용.
 * 서버 시작 시마다 덮어쓰기.
 *
 * <p>각 enchant 의 표시 정보(이름/도구/설명) 는 enchant key 기준으로 하드코딩 매핑.
 * 새 enchant 추가 시 {@link #describe} 의 switch 에 한 줄 추가.
 */
public class EnchantListHtmlWriter {

    private final Plugin plugin;
    private final EnchantRegistry registry;

    public EnchantListHtmlWriter(Plugin plugin, EnchantRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void writeTo(File file) {
        try {
            Files.createDirectories(file.getParentFile().toPath());
            try (Writer w = new OutputStreamWriter(
                    Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8)) {
                w.write(buildHtml());
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "enchantslist.html 저장 실패", e);
        }
    }

    private String buildHtml() {
        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>CustomItems 인챈트 목록</title>\n");
        sb.append("<style>").append(css()).append("</style>\n");
        sb.append("</head>\n<body>\n");

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        sb.append("<header><h1>CustomItems 인챈트 목록</h1>")
          .append("<small>자동 생성 — ").append(stamp).append(" · 총 ")
          .append(registry.size()).append("종</small></header>\n");

        sb.append("<section><h2 class=\"life\">생활 인챈트</h2>\n");
        writeLifeTable(sb);
        sb.append("</section>\n");

        sb.append("<section><h2 class=\"combat\">전투 인챈트</h2>\n");
        sb.append("<h3>무기 기본</h3>\n");
        writeCombatTable(sb, Group.WEAPON_BASIC);
        sb.append("<h3>무기 유니크</h3>\n");
        writeCombatTable(sb, Group.WEAPON_UNIQUE);
        sb.append("<h3>방어구 기본</h3>\n");
        writeCombatTable(sb, Group.ARMOR_BASIC);
        sb.append("<h3>방어구 세트 <span class=\"hint\">(부위마다 레벨 1씩 합산 · 최대 4부위)</span></h3>\n");
        writeCombatTable(sb, Group.ARMOR_SET);
        sb.append("</section>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void writeLifeTable(StringBuilder sb) {
        sb.append("<table class=\"life-tbl\">\n");
        sb.append("<thead><tr><th>이름</th><th>레벨</th><th>도구</th><th>상세 설명</th></tr></thead>\n<tbody>\n");
        for (CustomEnchant e : registry.getByCategory(EnchantCategory.LIFE)) {
            Row r = describe(e);
            if (r == null) continue;
            sb.append("<tr>")
              .append("<td class=\"name\">").append(esc(r.name)).append("</td>")
              .append("<td class=\"lv\">").append(esc(r.level)).append("</td>")
              .append("<td class=\"tool\">").append(esc(r.tools)).append("</td>")
              .append("<td>").append(esc(r.desc)).append("</td>")
              .append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
    }

    private void writeCombatTable(StringBuilder sb, Group group) {
        sb.append("<table class=\"combat-tbl\">\n");
        sb.append("<thead><tr><th>이름</th><th>레벨</th><th>도구</th><th>상세 설명</th></tr></thead>\n<tbody>\n");
        for (CustomEnchant e : registry.getByCategory(EnchantCategory.COMBAT)) {
            Row r = describe(e);
            if (r == null || r.group != group) continue;
            sb.append("<tr>")
              .append("<td class=\"name\">").append(esc(r.name)).append("</td>")
              .append("<td class=\"lv\">").append(esc(r.level)).append("</td>")
              .append("<td class=\"tool\">").append(esc(r.tools)).append("</td>")
              .append("<td>").append(esc(r.desc)).append("</td>")
              .append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
    }

    // ─── 도구 라벨 ────────────────────────

    private static final String T_COMMON       = "공통 (곡괭이·괭이·삽·낚싯대·도끼)";
    private static final String T_PICKAXE      = "곡괭이";
    private static final String T_HOE          = "괭이";
    private static final String T_SHOVEL       = "삽";
    private static final String T_ROD          = "낚싯대";
    private static final String T_AXE          = "도끼";
    private static final String T_MELEE        = "검·창·삼지창·철퇴";
    private static final String T_COMBAT       = "검·창·삼지창·철퇴·활";
    private static final String T_SPEAR        = "창";
    private static final String T_MACE         = "철퇴";
    private static final String T_BOW          = "활";
    private static final String T_ARMOR        = "방어구";

    // ─── 설명 매핑 ────────────────────────

    private enum Group { LIFE, WEAPON_BASIC, WEAPON_UNIQUE, ARMOR_BASIC, ARMOR_SET }

    private static class Row {
        final Group group;
        final String name;
        final String level;
        final String tools;
        final String desc;

        Row(Group group, String name, String level, String tools, String desc) {
            this.group = group;
            this.name = name;
            this.level = level;
            this.tools = tools;
            this.desc = desc;
        }
    }

    private Row describe(CustomEnchant e) {
        String key = e.getKey().getKey();
        return switch (key) {
            // ─── LIFE ───
            case "life_crop_bonemeal"   -> life("농작물 뼛가루", T_COMMON,
                    "수확/채광 시 (10~50)% 확률로 뼛가루 (1~2)개 드랍");
            case "life_exp_boost"       -> life("경험치 획득량", T_COMMON,
                    "생활 활동(수확/채광/낚시/삽질/벌목) 시 (20~50)% 확률로 경험치 구슬 (2~3)개 드랍");
            case "life_hunter"          -> life("사냥꾼", T_COMMON,
                    "동물 처치 시 (20~50)% 확률로 부산물 (양털/고기/가죽 등) (1~2)배 추가");
            case "life_smelted_ingot"   -> life("급속제련", "1~2", T_PICKAXE,
                    "광물 채굴 시 제련된 주괴 드랍 (Lv2: 주괴 +1개, 다이아/에메랄드/청금석/레드스톤/석탄/네더라이트는 광석 +1) (섬세한 손길이 있을 경우 적용 안됨)");
            case "life_adjacent_mine"   -> life("채굴의 달인", "1~2", T_PICKAXE,
                    "채굴 시 인접 블록 동시 채굴 (Lv1: 상하 / Lv2: 십자) · 야생 한정");
            case "life_explosive_mine"  -> life("인간 굴착기", "1~3", T_PICKAXE,
                    "(50/60/75)% 확률로 3×3×3 폭발 채굴 · 야생 한정");
            case "life_lucky_crop"      -> life("행운의 작물", "1~5", T_HOE,
                    "농작물 수확 시 (30/35/40/45/50)% 확률로 작물 +2 추가");
            case "life_hoe_master"      -> life("괭이의 달인", "1~2", T_HOE,
                    "시선 방향 앞쪽 (1/2)칸의 자란 작물 동시 수확 (행운의 작물 효과도 함께 적용)");
            case "life_sand_to_glass"   -> life("모래 유리화", T_SHOVEL,
                    "모래 채굴 시 유리블록 드랍");
            case "life_auto_reel"       -> life("자동 회수", "1~3", T_ROD,
                    "입질 후 (5/3/1)초 뒤 자동 회수 · 잡히는 물고기 사이즈 티어 +Lv (자동 투척과 합산, 최대 4)");
            case "life_auto_cast"       -> life("자동 투척", "1~3", T_ROD,
                    "잡은 후 (3/2/1)초 뒤 자동 투척 · 잡히는 물고기 사이즈 티어 +Lv (자동 회수와 합산, 최대 4)");
            case "life_lumber_bonus"    -> life("숙련된 벌목", "1~3", T_AXE,
                    "나무 벌목 시 (20/35/50)% 확률로 나무 추가 +1");
            case "life_tree_capacitor"  -> life("벌목의 왕", "1~2", T_AXE,
                    "연결된 나무 블럭이 (5/10)개 이하이면 한번에 전체 벌목");

            // ─── COMBAT — 무기 기본 ───
            case "attack_power"     -> wBasic("공격력", T_MELEE,
                    "공격력 증가 (+3 / +4 / +5)");
            case "lifesteal"        -> wBasic("흡혈", T_MELEE,
                    "공격 시 체력 +(0.5~1.0)칸 회복 · 쿨타임 (2~3)초");
            case "bow_attack_power" -> wBasic("활 공격력", T_BOW,
                    "화살에 +(1.5~3.0) 데미지 부여");
            case "bow_critical"     -> wBasic("활 치명타", T_BOW,
                    "화살 (30~50)% 확률로 ×2 데미지");

            // ─── COMBAT — 무기 유니크 ───
            case "giant_hunter"     -> wUnique("거인의 힘", "1~2", T_MELEE,
                    "공격 시 최대체력 비례 +(300~420)% 데미지 (28칸 기준)");
            case "feast_fury"       -> wUnique("충만한 활력", "1~2", T_MELEE,
                    "허기 7칸 이상에서 배부를수록 추가 데미지 (만복 시 +300~420%)");
            case "hunger_fury"      -> wUnique("끝없는 허기", "1~2", T_MELEE,
                    "허기 5칸 이하에서 배고플수록 추가 데미지 (1칸 시 +390~560%)");
            case "executioner"      -> wUnique("약자멸시", "1~2", T_MELEE,
                    "적 체력 50% 미만 시 +(300~420)% 데미지");
            case "heavy_strike"     -> wUnique("묵직한 일격", "1~2", T_COMBAT,
                    "공격력 +(10/15) flat");
            case "apex_breaker"     -> wUnique("정면승부", "1~2", T_COMBAT,
                    "적 체력 50% 이상 시 +(300~420)% 데미지");
            case "lethal_strike"    -> wUnique("치명타", "1~2", T_COMBAT,
                    "(25/40)% 확률로 데미지 (5/6)배 (합연산). 무기 우클릭 스킬에도 적용");
            case "rush_strike"      -> wUnique("질주의 일격", "1~2", T_SPEAR,
                    "스프린트 시 이동속도 비례 추가 데미지 (풀스프린트 +300~420%)");
            case "thunder_strike"   -> wUnique("낙뢰", "1~2", T_MACE,
                    "근접 공격 시 (40~50)% 확률로 850%의 번개 데미지 (합연산)");
            case "twin_shot"        -> wUnique("이연사", "1~2", T_BOW,
                    "확률(50 / 75)%로 화살 1발 추가 발사 — 무한 인챈트 보유 시 최소 화살 갯수 2개 필요");
            case "triple_shot"      -> wUnique("삼연사", "1~2", T_BOW,
                    "확률(35 / 55)%로 화살 2발 추가 발사 — 무한 인챈트 보유 시 최소 화살 갯수 3개 필요, 이연사와 중복 가능");
            case "quick_shot"       -> wUnique("신속 사격", "1~2", T_BOW,
                    "(80 / 90)% 차지 시간 감소");

            // ─── COMBAT — 방어구 기본 ───
            case "armor_boost"      -> aBasic("견고함", T_ARMOR,
                    "방어도 +(2~4)");
            case "vitality"         -> aBasic("활력", T_ARMOR,
                    "최대체력 +(1.0~2.0)칸");
            case "golden_heart"     -> aBasic("황금 심장", T_ARMOR,
                    "Lv 1~3 · (20/10/5)초마다 황금심장 +1칸 (최대 황금심장 갯수 = 부위수+1)");
            case "swiftness"        -> aBasic("신속", T_ARMOR,
                    "이동속도 +(5~10)%");

            // ─── COMBAT — 방어구 세트 ───
            case "saturation_keeper" -> aSet("공복의 저주", T_ARMOR,
                    "허기 (4/3/2/1)칸 이상으로 회복되지 않음 · 3부위 이상부터 이동속도 +30% 보상 (스프린트 대체)");
            // 빠른 회복 비활성화 (도깨비불로 대체)
            // case "fast_recovery"    -> aSet("빠른 회복", T_ARMOR,
            //         "마지막 피격 후 (10/8/6/4)초 뒤 자동 재생");
            case "will_o_wisp"      -> aSet("도깨비불", T_ARMOR,
                    "방어구 주위에 파란 영혼불 공전 + 공격 시 부위수 × 1.5 깡뎀 추가 (근접 · 활)");
            case "big_vitality"     -> aSet("체력은 국력", T_ARMOR,
                    "최대체력 +(4/6/8/10)칸");
            // 가시 비활성화 (아그니로 대체) — 등록 해제됐으나 키 충돌 방지용 case 유지, null 반환으로 표시 X
            // case "thorns_lamp"      -> aSet("가시", T_ARMOR,
            //         "피격 시 공격자에게 (3/6/9/12) 데미지 반사");
            case "agni"             -> aSet("아그니", T_ARMOR,
                    "착용자가 발생시킨 적의 불 데미지 +(50/100/150/200)% (화염, 발화 인챈트)");
            case "lucky"            -> aSet("행운", T_ARMOR,
                    "부착된 부위의 기본 인챈트 효과 2배 + 다른 세트 인챈트의 부위 카운트에 자동 포함");

            default -> null;
        };
    }

    private static Row life(String name, String tools, String desc) {
        return new Row(Group.LIFE, name, "-", tools, desc);
    }
    private static Row life(String name, String level, String tools, String desc) {
        return new Row(Group.LIFE, name, "Lv " + level, tools, desc);
    }
    private static Row wBasic(String name, String tools, String desc) {
        return new Row(Group.WEAPON_BASIC, name, "", tools, desc);
    }
    private static Row wUnique(String name, String level, String tools, String desc) {
        return new Row(Group.WEAPON_UNIQUE, name, "Lv " + level, tools, desc);
    }
    private static Row aBasic(String name, String tools, String desc) {
        return new Row(Group.ARMOR_BASIC, name, "", tools, desc);
    }
    private static Row aSet(String name, String tools, String desc) {
        return new Row(Group.ARMOR_SET, name, "", tools, desc);
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&'  -> sb.append("&amp;");
                case '<'  -> sb.append("&lt;");
                case '>'  -> sb.append("&gt;");
                case '"'  -> sb.append("&quot;");
                default   -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String css() {
        return """
            * { box-sizing: border-box; }
            body {
                font-family: 'Noto Sans KR', 'Malgun Gothic', system-ui, sans-serif;
                background: #1a1d23; color: #e8e8ec;
                margin: 0; padding: 24px 32px;
                line-height: 1.5;
            }
            header { margin-bottom: 32px; border-bottom: 1px solid #393d46; padding-bottom: 12px; }
            header h1 { margin: 0; font-size: 24px; color: #ffd180; }
            header small { color: #8b8f99; font-size: 12px; }
            section { margin-bottom: 36px; }
            h2 { font-size: 20px; margin: 24px 0 12px; padding-left: 12px; border-left: 4px solid; }
            h2.life   { border-color: #8aff8a; color: #8aff8a; }
            h2.combat { border-color: #ff7a7a; color: #ff7a7a; }
            h3 { font-size: 16px; margin: 18px 0 8px; color: #b8c0cc; }
            h3 .hint { font-size: 12px; color: #8b8f99; font-weight: 400; margin-left: 6px; }
            table {
                width: 100%; border-collapse: collapse;
                background: #22262e; border-radius: 6px; overflow: hidden;
                box-shadow: 0 1px 4px rgba(0,0,0,.3);
            }
            th {
                background: #2d323c; color: #ffd180;
                padding: 10px 14px; text-align: left; font-weight: 600;
                font-size: 13px; letter-spacing: .04em;
            }
            td {
                padding: 9px 14px; border-top: 1px solid #2d323c;
                font-size: 14px; vertical-align: top;
            }
            td.name { font-weight: 600; color: #f5f5f5; white-space: nowrap; }
            td.lv   { font-family: 'Consolas', monospace; color: #ffaa00; white-space: nowrap; }
            td.tool { color: #8ec5ff; white-space: nowrap; }
            .life-tbl  td.name { color: #c4ffc4; }
            .combat-tbl td.name { color: #ffd5d5; }
            tr:hover td { background: rgba(255,255,255,.03); }
            """;
    }
}
