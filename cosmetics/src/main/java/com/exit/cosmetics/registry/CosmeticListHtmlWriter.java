package com.exit.cosmetics.registry;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.model.CosmeticType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;

/**
 * 등록된 치장 카탈로그를 사람이 읽기 좋은 HTML 표로 dump.
 *
 * <p>출력 경로: {@code plugins/Cosmetics/cosmeticslist.html}.
 * EnchantListHtmlWriter 와 같은 다크 테마. 서버 시작 + 리로드 시 덮어쓰기.
 * 등급 결정·기획용 — 한 화면에서 모든 치장의 외형 정보 비교 가능.
 */
public class CosmeticListHtmlWriter {

    private final Plugin plugin;
    private final CosmeticRegistry registry;

    public CosmeticListHtmlWriter(Plugin plugin, CosmeticRegistry registry) {
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
            plugin.getLogger().log(Level.WARNING, "cosmeticslist.html 저장 실패", e);
        }
    }

    private String buildHtml() {
        StringBuilder sb = new StringBuilder(16384);
        sb.append("<!DOCTYPE html>\n<html lang=\"ko\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<title>Cosmetics 치장 목록</title>\n");
        sb.append("<style>").append(css()).append("</style>\n");
        sb.append("</head>\n<body>\n");

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        int total = 0;
        for (CosmeticType t : CosmeticType.values()) {
            total += registry.getByType(t).size();
        }
        sb.append("<header><h1>Cosmetics 치장 목록</h1>")
          .append("<small>자동 생성 — ").append(stamp).append(" · 총 ")
          .append(total).append("종 · 등급 결정용</small></header>\n");

        // 등급 요약
        int common = registry.getByRarity(CosmeticRarity.COMMON).size();
        int rare = registry.getByRarity(CosmeticRarity.RARE).size();
        int unique = registry.getByRarity(CosmeticRarity.UNIQUE).size();
        int legendary = registry.getByRarity(CosmeticRarity.LEGENDARY).size();
        sb.append("<div class=\"summary\">")
          .append("<span class=\"r-common\">COMMON ").append(common).append("</span>")
          .append("<span class=\"r-rare\">RARE ").append(rare).append("</span>")
          .append("<span class=\"r-unique\">UNIQUE ").append(unique).append("</span>")
          .append("<span class=\"r-legendary\">LEGENDARY ").append(legendary).append("</span>")
          .append("</div>\n");

        writeSection(sb, "헬멧 (HAT)",    "hat",    CosmeticType.HAT);
        writeSection(sb, "흉갑 (CHEST)",  "chest",  CosmeticType.CHEST);
        writeSection(sb, "각반 (LEGS)",   "legs",   CosmeticType.LEGS);
        writeSection(sb, "신발 (FEET)",   "feet",   CosmeticType.FEET);
        writeSection(sb, "무기 (WEAPON)", "weapon", CosmeticType.WEAPON);
        writeSection(sb, "날개 (WING)",   "wing",   CosmeticType.WING);
        writeSection(sb, "트레일 (TRAIL)","trail",  CosmeticType.TRAIL);
        writeSection(sb, "탈것 (MOUNT)",  "mount",  CosmeticType.MOUNT);

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void writeSection(StringBuilder sb, String title, String cssClass, CosmeticType type) {
        List<CosmeticDefinition> items = registry.getByType(type);
        if (items.isEmpty()) return;
        sb.append("<section><h2 class=\"").append(cssClass).append("\">")
          .append(title).append(" <span class=\"count\">").append(items.size()).append("</span></h2>\n");

        sb.append("<table>\n");
        sb.append("<thead><tr>")
          .append("<th>ID</th>")
          .append("<th>표시 이름</th>")
          .append("<th>등급</th>")
          .append("<th>설명</th>")
          .append("<th>base_item</th>")
          .append("<th>model_data</th>")
          .append("<th>asset_id</th>")
          .append("<th>applicable_to</th>")
          .append("</tr></thead>\n<tbody>\n");

        for (CosmeticDefinition d : items) {
            sb.append("<tr>");
            sb.append("<td class=\"id\">").append(esc(d.getId())).append("</td>");
            sb.append("<td class=\"name\">").append(renderColored(d.getDisplayName())).append("</td>");
            sb.append("<td class=\"rarity ").append(rarityCss(d.getRarity())).append("\">")
              .append(d.getRarity().name()).append("</td>");
            sb.append("<td class=\"desc\">").append(renderColored(d.getDescription())).append("</td>");
            sb.append("<td class=\"mat\">").append(esc(d.getBaseItem().name())).append("</td>");
            sb.append("<td class=\"cmd\">").append(d.getModelData() > 0 ? String.valueOf(d.getModelData()) : "-").append("</td>");
            sb.append("<td class=\"asset\">").append(d.getAssetId() != null ? esc(d.getAssetId()) : "-").append("</td>");
            sb.append("<td class=\"appl\">").append(applicableSummary(d)).append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</tbody></table>\n");
        sb.append("</section>\n");
    }

    private String applicableSummary(CosmeticDefinition d) {
        if (d.getApplicableTo().isEmpty()) return "<span class=\"appl-all\">전체</span>";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var m : d.getApplicableTo()) {
            if (!first) sb.append(", ");
            sb.append(m.name());
            first = false;
        }
        return esc(sb.toString());
    }

    private static String rarityCss(CosmeticRarity r) {
        return switch (r) {
            case COMMON    -> "r-common";
            case RARE      -> "r-rare";
            case UNIQUE    -> "r-unique";
            case LEGENDARY -> "r-legendary";
        };
    }

    /** §X 컬러 코드를 HTML span 색상으로 렌더. */
    private static String renderColored(String src) {
        if (src == null || src.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(src.length() + 64);
        boolean spanOpen = false;
        int i = 0;
        while (i < src.length()) {
            char c = src.charAt(i);
            if (c == '§' && i + 1 < src.length()) {
                char code = Character.toLowerCase(src.charAt(i + 1));
                String color = mcColor(code);
                if (spanOpen) { sb.append("</span>"); spanOpen = false; }
                if (color != null) {
                    sb.append("<span style=\"color:").append(color).append("\">");
                    spanOpen = true;
                }
                i += 2;
            } else {
                switch (c) {
                    case '&'  -> sb.append("&amp;");
                    case '<'  -> sb.append("&lt;");
                    case '>'  -> sb.append("&gt;");
                    case '"'  -> sb.append("&quot;");
                    default   -> sb.append(c);
                }
                i++;
            }
        }
        if (spanOpen) sb.append("</span>");
        return sb.toString();
    }

    private static String mcColor(char code) {
        return switch (code) {
            case '0' -> "#000000";
            case '1' -> "#0000AA";
            case '2' -> "#00AA00";
            case '3' -> "#00AAAA";
            case '4' -> "#AA0000";
            case '5' -> "#AA00AA";
            case '6' -> "#FFAA00";
            case '7' -> "#AAAAAA";
            case '8' -> "#555555";
            case '9' -> "#5555FF";
            case 'a' -> "#55FF55";
            case 'b' -> "#55FFFF";
            case 'c' -> "#FF5555";
            case 'd' -> "#FF55FF";
            case 'e' -> "#FFFF55";
            case 'f' -> "#FFFFFF";
            default  -> null; // k/l/m/n/o/r — 포맷 코드, 색 변경 X
        };
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
            header { margin-bottom: 16px; border-bottom: 1px solid #393d46; padding-bottom: 12px; }
            header h1 { margin: 0; font-size: 24px; color: #ffd180; }
            header small { color: #8b8f99; font-size: 12px; }
            .summary { margin: 12px 0 32px; display: flex; gap: 8px; }
            .summary span {
                display: inline-block; padding: 4px 12px; border-radius: 4px;
                font-size: 13px; font-weight: 600;
            }
            section { margin-bottom: 36px; }
            h2 {
                font-size: 18px; margin: 24px 0 12px;
                padding-left: 12px; border-left: 4px solid;
            }
            h2 .count {
                font-size: 12px; color: #8b8f99; font-weight: 400; margin-left: 8px;
            }
            h2.hat    { border-color: #ffd180; color: #ffd180; }
            h2.chest  { border-color: #ff8a8a; color: #ff8a8a; }
            h2.legs   { border-color: #ffa07a; color: #ffa07a; }
            h2.feet   { border-color: #d7a4ff; color: #d7a4ff; }
            h2.weapon { border-color: #ff7a7a; color: #ff7a7a; }
            h2.wing   { border-color: #8aff8a; color: #8aff8a; }
            h2.trail  { border-color: #8ec5ff; color: #8ec5ff; }
            h2.mount  { border-color: #ffd700; color: #ffd700; }
            table {
                width: 100%; border-collapse: collapse;
                background: #22262e; border-radius: 6px; overflow: hidden;
                box-shadow: 0 1px 4px rgba(0,0,0,.3);
            }
            th {
                background: #2d323c; color: #ffd180;
                padding: 8px 10px; text-align: left; font-weight: 600;
                font-size: 12px; letter-spacing: .04em;
            }
            td {
                padding: 7px 10px; border-top: 1px solid #2d323c;
                font-size: 13px; vertical-align: top;
            }
            td.id    { font-family: 'Consolas', monospace; color: #b8c0cc; white-space: nowrap; }
            td.name  { font-weight: 600; color: #f5f5f5; white-space: nowrap; }
            td.desc  { color: #c8ccd2; font-size: 12px; }
            td.mat   { font-family: 'Consolas', monospace; color: #8ec5ff; font-size: 11px; white-space: nowrap; }
            td.cmd   { font-family: 'Consolas', monospace; color: #ffaa00; text-align: right; }
            td.asset { font-family: 'Consolas', monospace; color: #c4ffc4; font-size: 11px; }
            td.appl  { font-family: 'Consolas', monospace; color: #888; font-size: 10px; line-height: 1.4; }
            td.rarity { font-weight: 700; font-size: 11px; white-space: nowrap; }
            .r-common    { background: #4b525a; color: #d8dde2; }
            .r-rare      { background: #2a4d7f; color: #8ec5ff; }
            .r-unique    { background: #6e2a7f; color: #e8a0ff; }
            .r-legendary { background: #7f5a1a; color: #ffd180; }
            td.r-common    { background: rgba(75,82,90,.3); }
            td.r-rare      { background: rgba(42,77,127,.3); }
            td.r-unique    { background: rgba(110,42,127,.3); }
            td.r-legendary { background: rgba(127,90,26,.3); }
            .appl-all { color: #888; font-style: italic; }
            tr:hover td { background: rgba(255,255,255,.03); }
            """;
    }
}
