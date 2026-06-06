package com.exit.world.listener;

import com.exit.world.manager.DungeonEntry;
import com.exit.world.manager.DungeonRegistry;
import com.exit.world.manager.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * dungeons.yml 의 time-limit-sec 가 설정된 던전에 입장한 플레이어를
 * N초 후 마을 (worlds.yml village) 으로 자동 텔포.
 *
 * - 플레이어가 자발적으로 떠나면 타이머 취소
 * - 로그아웃 시 타이머 취소 (재접속 시 다시 들어가야 재카운트)
 * - 동일 월드 재입장 시 기존 타이머 취소 후 새로 시작
 */
public class DungeonTimeLimitListener implements Listener {

    private final JavaPlugin plugin;
    private final DungeonRegistry registry;
    private final WorldManager worldManager;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public DungeonTimeLimitListener(JavaPlugin plugin, DungeonRegistry registry, WorldManager worldManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // 이전 월드 떠남 → 기존 타이머 취소
        cancelTask(player.getUniqueId());
        // 새 월드가 time-limit 던전이면 타이머 시작
        scheduleIfTimeLimited(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelTask(event.getPlayer().getUniqueId());
    }

    private void scheduleIfTimeLimited(Player player) {
        String worldName = player.getWorld().getName();
        DungeonEntry entry = registry.findByWorldName(worldName);
        if (entry == null || entry.timeLimitSec() <= 0) return;

        int sec = entry.timeLimitSec();
        UUID uuid = player.getUniqueId();

        player.sendMessage(Component.text("⏱ 이 던전은 " + (sec / 60) + "분 후 자동으로 마을로 이동됩니다.")
                .color(NamedTextColor.YELLOW));

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;
            if (!p.getWorld().getName().equals(worldName)) return;  // 이미 떠남
            p.sendMessage(Component.text("⏱ 시간 만료! 마을로 이동합니다.")
                    .color(NamedTextColor.RED));
            worldManager.teleportPlayer(p, "village");
            tasks.remove(uuid);
        }, sec * 20L);

        tasks.put(uuid, task);
    }

    private void cancelTask(UUID uuid) {
        BukkitTask t = tasks.remove(uuid);
        if (t != null) t.cancel();
    }
}
