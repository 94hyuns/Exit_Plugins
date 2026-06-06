package com.exit.cosmetics.ticket;

import com.exit.cosmetics.gacha.GachaResult;
import com.exit.cosmetics.model.CosmeticRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * 뽑기권 사용 시의 연출 관리. Title API로 화면 중앙에 멘트 표시.
 *
 * <p>흐름: 플레이어가 뽑기권 우클릭 → play() 호출 → 지정된 틱만큼 멘트 순환 → 결과 표시.
 *
 * <p>동시 다발 방지: 같은 플레이어가 연출 중에 또 뽑기권을 쓰면 {@link #isPlaying}로 차단.
 */
public class AnimationService {

    private final JavaPlugin plugin;

    private int durationTicks = 40;      // 2초
    private int resultDurationTicks = 60; // 3초
    private List<String> rollingMessages = List.of("§e상품 맞추는 중...");
    private String rollingSubtitle = "§7행운의 주사위가 굴러간다";
    private Sound rollingSound = Sound.BLOCK_NOTE_BLOCK_BELL;
    private Sound resultSound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
    private Sound legendarySound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
    private Sound duplicateSound = Sound.BLOCK_AMETHYST_BLOCK_CHIME;

    private final Set<UUID> playingSet = new HashSet<>();
    private final Random random = new Random();

    public AnimationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(FileConfiguration config) {
        durationTicks = config.getInt("animation.duration_ticks", 40);
        resultDurationTicks = config.getInt("animation.result_duration_ticks", 60);

        List<String> msgs = config.getStringList("animation.rolling_messages");
        if (!msgs.isEmpty()) rollingMessages = msgs;
        rollingSubtitle = config.getString("animation.rolling_subtitle", rollingSubtitle);

        rollingSound = parseSound(config.getString("animation.rolling_sound"), rollingSound);
        resultSound = parseSound(config.getString("animation.result_sound"), resultSound);
        legendarySound = parseSound(config.getString("animation.legendary_sound"), legendarySound);
        duplicateSound = parseSound(config.getString("animation.duplicate_sound"), duplicateSound);
    }

    public boolean isPlaying(UUID uuid) {
        return playingSet.contains(uuid);
    }

    /**
     * 뽑기 연출 시작.
     *
     * @param player    대상 플레이어
     * @param drawer    연출 종료 후 실제 뽑기를 수행할 콜백. GachaResult 반환.
     * @param onDone    연출+뽑기 완료 후 호출. null 가능.
     */
    public void play(Player player, java.util.function.Supplier<GachaResult> drawer, Consumer<GachaResult> onDone) {
        UUID uuid = player.getUniqueId();
        if (!playingSet.add(uuid)) return; // 중복 실행 방지

        // 멘트 순환 (4틱 간격으로 변경)
        final int changeInterval = 8;
        BukkitRunnable rollingTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    playingSet.remove(uuid);
                    return;
                }
                if (ticks >= durationTicks) {
                    cancel();
                    showResult(player, drawer, onDone);
                    return;
                }
                if (ticks % changeInterval == 0) {
                    String msg = rollingMessages.get(random.nextInt(rollingMessages.size()));
                    Title title = Title.title(
                            Component.text(msg).decoration(TextDecoration.ITALIC, false),
                            Component.text(rollingSubtitle).decoration(TextDecoration.ITALIC, false),
                            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(500), Duration.ofMillis(50))
                    );
                    player.showTitle(title);
                    player.playSound(player.getLocation(), rollingSound, 1.0f, 1.0f + (float) random.nextDouble() * 0.5f);
                }
                ticks++;
            }
        };
        rollingTask.runTaskTimer(plugin, 0L, 1L);
    }

    private void showResult(Player player, java.util.function.Supplier<GachaResult> drawer, Consumer<GachaResult> onDone) {
        UUID uuid = player.getUniqueId();
        try {
            GachaResult result = drawer.get();
            if (result == null) {
                playingSet.remove(uuid);
                return;
            }

            Component mainLine;
            Component subLine;
            Sound sound;

            if (result.duplicate()) {
                mainLine = Component.text("§7[중복]")
                        .append(Component.text(" " + result.definition().getDisplayName()));
                subLine = Component.text("§b+" + result.shardsGained() + " 가루").decoration(TextDecoration.ITALIC, false);
                sound = duplicateSound;
            } else {
                CosmeticRarity rar = result.definition().getRarity();
                String prefix = switch (rar) {
                    case LEGENDARY -> "§6★ 전설 ★ ";
                    case UNIQUE -> "§d✦ 유니크 ✦ ";
                    default -> "§e획득! ";
                };
                mainLine = Component.text(prefix + result.definition().getDisplayName()).decoration(TextDecoration.ITALIC, false);
                subLine = Component.text("§7" + rar.getDisplayName()).decoration(TextDecoration.ITALIC, false);
                sound = (rar == CosmeticRarity.LEGENDARY || rar == CosmeticRarity.UNIQUE) ? legendarySound : resultSound;
            }

            Title title = Title.title(mainLine, subLine,
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(resultDurationTicks * 50L), Duration.ofMillis(500)));
            player.showTitle(title);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);

            if (!result.duplicate()) {
                player.sendMessage(Component.text("[획득] ", NamedTextColor.GREEN)
                        .append(Component.text(result.definition().getDisplayName() + " §7(" + result.definition().getRarity().getDisplayName() + "§7)")));
            } else {
                player.sendMessage(Component.text("[중복] ", NamedTextColor.GRAY)
                        .append(Component.text(result.definition().getDisplayName() + " §7→ §b" + result.shardsGained() + " 가루")));
            }

            if (onDone != null) onDone.accept(result);
        } finally {
            playingSet.remove(uuid);
        }
    }

    private Sound parseSound(String name, Sound defaultSound) {
        if (name == null) return defaultSound;
        try {
            return Sound.valueOf(name.toUpperCase().replace('.', '_'));
        } catch (IllegalArgumentException e) {
            return defaultSound;
        }
    }
}
