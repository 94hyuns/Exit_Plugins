package com.exit.customitems.lamp.enchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * SET 인챈트 정보 조회 명령어.
 *
 * <p>표시명(name) / 한 줄 요약(short) 은 enchants.yml 의 skills 섹션에서 읽어 오므로
 * 관리자가 yml 만 고치면 명령어 출력에 반영됨 (서버 재시작 필요).
 *
 * <p>레벨별 효과 문장은 같은 yml 의 per_level / base 같은 계수로부터 매번 동적 계산.
 */
public final class SkillCommand implements CommandExecutor, TabCompleter {

    private final EnchantConfig ec;

    public SkillCommand(EnchantConfig ec) {
        this.ec = ec;
    }

    /** 매번 yml 에서 읽어 빌드 — yml 변경 시 다음 명령 호출에 자동 반영. */
    private List<SkillInfo> buildSkills() {
        List<SkillInfo> list = new ArrayList<>();

        // ── 무기 유니크 (1~2 레벨) ──
        list.add(skill("giant_hunter", true, 2,
                "거인의 힘", "공격 시 자기 최대체력 비례 추가 데미지. (기준 28칸)",
                level -> {
                    double b = ec.readDouble("giant_hunter", "base", 180.0);
                    double p = ec.readDouble("giant_hunter", "per_level", 120.0);
                    return "기준 풀세팅(28칸)에서 +" + fmt(b + p * level) + "% 데미지";
                }));
        list.add(skill("feast_fury", true, 2,
                "충만한 활력", "허기 7칸 이상에서 배부를수록 추가 데미지.",
                level -> {
                    double b = ec.readDouble("feast_fury", "base", 180.0);
                    double p = ec.readDouble("feast_fury", "per_level", 120.0);
                    return "허기 만복(10칸)에서 최대 +" + fmt(b + p * level) + "% 데미지";
                }));
        list.add(skill("hunger_fury", true, 2,
                "끝없는 허기", "허기 5칸 이하에서 배고플수록 추가 데미지.",
                level -> {
                    List<Double> table = ec.readDoubleList("hunger_fury", "level_bonus_pct",
                            List.of(390.0, 560.0));
                    double pct = table.get(Math.min(table.size() - 1, level - 1));
                    return "허기 1칸에서 최대 +" + fmt(pct) + "% 데미지";
                }));
        list.add(skill("executioner", true, 2,
                "약자멸시", "적 체력 50% 미만일 때 추가 % 데미지. (50% 정확치는 정면승부 발동)",
                level -> {
                    double b = ec.readDouble("executioner", "base", 180.0);
                    double p = ec.readDouble("executioner", "per_level", 120.0);
                    return "적 체력 50% 미만에서 +" + fmt(b + p * level) + "% 데미지";
                }));
        list.add(skill("rush_strike", true, 2,
                "질주의 일격", "창 전용. 빠르게 이동할수록 공격에 추가 데미지.",
                level -> {
                    double b = ec.readDouble("rush_strike", "base", 150.0);
                    double p = ec.readDouble("rush_strike", "per_level", 100.0);
                    return "풀세팅(이속+40%) 스프린트 시 +" + fmt(b + p * level) + "% 데미지";
                }));
        list.add(skill("thunder_strike", true, 2,
                "낙뢰", "철퇴 전용. 일정 확률로 번개와 함께 강력한 번개 데미지 (합연산).",
                level -> {
                    double cb = ec.readDouble("thunder_strike", "chance_base", 30.0);
                    double cp = ec.readDouble("thunder_strike", "chance_per", 10.0);
                    double ratio = ec.readDouble("thunder_strike", "damage_ratio", 8.5) * 100.0;
                    return fmt(cb + cp * level) + "% 확률, " + fmt(ratio) + "%의 번개 데미지 (합연산)";
                }));
        list.add(skill("twin_shot", true, 2,
                "이연사", "활 전용. 확률로 화살 1발 추가 발사 — 무한 인챈트 보유 시 최소 화살 갯수 2개 필요.",
                level -> {
                    List<Double> chances = ec.readDoubleList("twin_shot", "chance_per_level",
                            List.of(50.0, 75.0));
                    double chance = chances.get(Math.min(chances.size() - 1, level - 1));
                    return fmt(chance) + "% 확률로 화살 1발 추가 발사";
                }));
        list.add(skill("triple_shot", true, 2,
                "삼연사", "활 전용. 확률로 화살 2발 추가 발사 — 무한 인챈트 보유 시 최소 화살 갯수 3개 필요. 이연사와 중복 가능.",
                level -> {
                    List<Double> chances = ec.readDoubleList("triple_shot", "chance_per_level",
                            List.of(35.0, 55.0));
                    double chance = chances.get(Math.min(chances.size() - 1, level - 1));
                    return fmt(chance) + "% 확률로 화살 2발 추가 발사";
                }));
        list.add(skill("quick_shot", true, 2,
                "신속 사격", "활 전용. 차지 시간 감소.",
                level -> {
                    List<Double> th = ec.readDoubleList("quick_shot", "thresholds",
                            List.of(0.20, 0.10));
                    double t = th.get(Math.min(th.size() - 1, level - 1));
                    double reduction = (1.0 - t) * 100.0;
                    return fmt(reduction) + "% 차지 시간 감소";
                }));
        list.add(skill("heavy_strike", true, 2,
                "묵직한 일격", "공격력 +10/+15 (L1/L2). 무기 종류 무관 적용 (안정딜).",
                level -> {
                    double b = ec.readDouble("heavy_strike", "base", 5.0);
                    double p = ec.readDouble("heavy_strike", "per_level", 5.0);
                    return "공격력 +" + fmt(b + p * level) + " (flat)";
                }));
        list.add(skill("apex_breaker", true, 2,
                "정면승부", "적 체력 50% 이상일 때 추가 % 데미지. (약자멸시와 거울 대칭)",
                level -> {
                    double b = ec.readDouble("apex_breaker", "base", 180.0);
                    double p = ec.readDouble("apex_breaker", "per_level", 120.0);
                    return "적 체력 50% 이상에서 +" + fmt(b + p * level) + "% 데미지";
                }));
        list.add(skill("lethal_strike", true, 2,
                "치명타", "확률(L1=25%/L2=40%)로 데미지 5/6배. 무기 우클릭 스킬에도 적용.",
                level -> {
                    List<Double> chances = ec.readDoubleList("lethal_strike", "chance_per_level",
                            List.of(25.0, 40.0));
                    List<Double> mults = ec.readDoubleList("lethal_strike", "multiplier_per_level",
                            List.of(5.0, 6.0));
                    double chance = chances.get(Math.min(chances.size() - 1, level - 1));
                    double mult = mults.get(Math.min(mults.size() - 1, level - 1));
                    return fmt(chance) + "% 확률로 데미지 " + fmt(mult) + "배 (합연산)";
                }));

        // ── 방어구 세트 (부위 카운트 = 레벨 1~4) ──
        list.add(skill("saturation_keeper", false, 4,
                "공복의 저주", "허기가 일정 칸 이상으로 회복되지 않음 (끝없는 허기 시너지). 3부위 이상에서 이동속도 +30% 보상.",
                level -> {
                    double base = ec.readDouble("saturation_keeper", "base", 5.0);
                    double per = ec.readDouble("saturation_keeper", "per_level", 1.0);
                    int capBars = (int) Math.max(0, Math.round(base - per * level));
                    String sprint = level >= 3 ? " · 이동속도 +30% 보상 (스프린트 대체)" : "";
                    return "허기 " + capBars + "칸 이상으로 회복되지 않음" + sprint;
                }));
        // 빠른 회복 비활성화 (도깨비불로 대체)
        // list.add(skill("fast_recovery", false, 4,
        //         "빠른 회복", "마지막 피격 후 일정 시간 지나면 허기/포만감 무관 자동 재생.",
        //         level -> { ... }));
        list.add(skill("will_o_wisp", false, 4,
                "도깨비불", "방어구 주위에 파란 영혼불 공전 + 공격 시 부위수 × 1.5 깡뎀 추가 (근접 · 활).",
                level -> {
                    double per = ec.readDouble("will_o_wisp", "per_level", 1.5);
                    return "공격 시 +" + fmt(per * level) + " 깡뎀 (근접/활)";
                }));
        list.add(skill("big_vitality", false, 4,
                "체력은 국력", "방어구 부위 수에 따라 최대체력 추가 증가.",
                level -> {
                    double base = ec.readDouble("big_vitality", "base", 2.0);
                    double per = ec.readDouble("big_vitality", "per_level", 2.0);
                    return "최대체력 +" + fmt(base + per * level) + "칸";
                }));
        // 가시 비활성화 (아그니로 대체)
        // list.add(skill("thorns", false, 4,
        //         "가시", "피격 시 공격자에게 반사 데미지.",
        //         level -> "피격 시 " + fmt(ec.readDouble("thorns", "per_level", 3.0) * level) + " 데미지 반사"));
        list.add(skill("agni", false, 4,
                "아그니", "착용자가 발화시킨 적의 FIRE_TICK 데미지를 부위 수에 따라 증폭.",
                level -> "발화된 적의 불 데미지 +" + fmt(ec.readDouble("agni", "per_level", 50.0) * level) + "%"));
        list.add(skill("lucky", false, 4,
                "행운", "부착된 부위의 기본 인챈트 2배 + 다른 세트 인챈트 부위 카운트에 자동 포함.",
                level -> "행운이 " + level + "개 부위에 부착됨 — 해당 부위 BASIC 효과 2배"));

        return list;
    }

    private SkillInfo skill(String id, boolean isWeapon, int maxLevel,
                            String defName, String defShort, IntFunction<String> effect) {
        String name = ec.readSkillName(id, defName);
        String shortDesc = ec.readSkillShort(id, defShort);
        return new SkillInfo(id, isWeapon, maxLevel, name, shortDesc, effect);
    }

    private SkillInfo findByName(String input) {
        String key = input.trim();
        for (SkillInfo s : buildSkills()) {
            if (s.name().equalsIgnoreCase(key)) return s;
        }
        return null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase();
        if (name.equals("skilllist") || name.equals("스킬목록")) {
            sendList(sender);
            return true;
        }
        if (name.equals("skillinfo") || name.equals("스킬정보")) {
            if (args.length == 0) {
                sender.sendMessage(Component.text("사용법: /skillinfo <스킬명>").color(NamedTextColor.YELLOW));
                return true;
            }
            String key = String.join(" ", args).trim();
            sendInfo(sender, key);
            return true;
        }
        return false;
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(Component.text("═══ 유니크/세트 인챈트 목록 ═══").color(NamedTextColor.GOLD));
        for (SkillInfo s : buildSkills()) {
            String tag = s.isWeapon() ? "[무기 유니크]" : "[방어구 세트]";
            sender.sendMessage(Component.text(" " + tag + " ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(s.name()).color(NamedTextColor.GOLD))
                    .append(Component.text(" — " + s.shortDesc()).color(NamedTextColor.WHITE)));
        }
        sender.sendMessage(Component.text("/skillinfo <스킬명> 으로 상세 효과 조회").color(NamedTextColor.GRAY));
    }

    private void sendInfo(CommandSender sender, String key) {
        SkillInfo s = findByName(key);
        if (s == null) {
            sender.sendMessage(Component.text("그런 스킬이 없습니다: " + key).color(NamedTextColor.RED));
            return;
        }
        String tag = s.isWeapon() ? "[무기 유니크]" : "[방어구 세트]";
        sender.sendMessage(Component.text("═══ " + s.name() + " " + tag + " ═══").color(NamedTextColor.GOLD));
        sender.sendMessage(Component.text(s.shortDesc()).color(NamedTextColor.WHITE));
        sender.sendMessage(Component.empty());
        String levelLabel = s.isWeapon() ? "롤 레벨" : "착용 부위 수";
        sender.sendMessage(Component.text("§7" + levelLabel + " 별 효과:"));
        for (int lvl = 1; lvl <= s.maxLevel(); lvl++) {
            sender.sendMessage(Component.text(" §e레벨 " + lvl + ": §f" + s.effectAt().apply(lvl)));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase();
        if (!name.equals("skillinfo") && !name.equals("스킬정보")) return List.of();
        if (args.length == 1) {
            String prefix = args[0];
            return buildSkills().stream()
                    .map(SkillInfo::name)
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf((int) Math.round(v));
        return String.format("%.2f", v);
    }

    private record SkillInfo(
            String id,
            boolean isWeapon,
            int maxLevel,
            String name,
            String shortDesc,
            IntFunction<String> effectAt
    ) {}
}
