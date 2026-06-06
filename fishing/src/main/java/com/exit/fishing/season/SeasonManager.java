package com.exit.fishing.season;

import com.exit.fishing.FishingPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

/**
 * 서버 전역 계절 상태 관리.
 *
 * - 현재 계절: plugins/Fishing/state.yml 에 저장 (Core DB와 분리 - 서버 스테이트성 데이터)
 * - 주기: config.yml 의 season.cycle-minutes 값 (분 단위)
 * - 리로드 시 기존 태스크 취소 → 새 주기로 재스케줄
 * - 브로드캐스트: 계절 변경 시 전체 채팅에 알림 (옵션)
 */
public class SeasonManager {

    private final FishingPlugin plugin;
    private final File stateFile;

    private Season current = Season.SPRING;
    private BukkitTask task;

    public SeasonManager(FishingPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
    }

    public void load() {
        if (!stateFile.exists()) {
            save();
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(stateFile);
        String name = yml.getString("season", "SPRING");
        try {
            current = Season.valueOf(name);
        } catch (IllegalArgumentException ex) {
            current = Season.SPRING;
            plugin.getLogger().warning("state.yml의 season 값이 잘못됐음. SPRING으로 초기화.");
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("season", current.name());
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("state.yml 저장 실패: " + e.getMessage());
        }
    }

    public Season current() {
        return current;
    }

    /** 관리자 수동 설정 */
    public void set(Season season) {
        this.current = season;
        save();
    }

    /** config.yml 기준으로 태스크 재시작. 처음 시작할 때도 이 함수 호출. */
    public void restartTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        int minutes = plugin.getConfig().getInt("season.cycle-minutes", 10);
        if (minutes < 1) minutes = 1;
        long ticks = minutes * 60L * 20L;

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, ticks, ticks);
        plugin.getLogger().info("계절 순환 주기: " + minutes + "분");
    }

    private void tick() {
        current = current.next();
        save();
        if (plugin.getConfig().getBoolean("season.broadcast-on-change", true)) {
            String prefix = plugin.getConfig().getString("prefix", "&6[ &fserver &6]");
            Component msg = LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(prefix + " &7계절이 시간에 따라 &f" + current.korean() + "&7으로 변동 되었습니다");
            Bukkit.broadcast(msg);
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        save();
    }
}
