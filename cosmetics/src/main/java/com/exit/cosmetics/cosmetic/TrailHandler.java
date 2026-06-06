package com.exit.cosmetics.cosmetic;

import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.registry.CosmeticRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 트레일 핸들러. 플레이어별로 BukkitRunnable을 돌려서 주기적으로 발밑에 파티클을 스폰한다.
 *
 * <p>성능 고려: 플레이어가 정지 상태일 때도 계속 파티클을 뿌리면 낭비되므로,
 * 최근 위치 대비 일정 거리 이상 이동했을 때만 스폰.
 */
public class TrailHandler {

    private static final double MIN_MOVE_DISTANCE_SQ = 0.05 * 0.05;

    private final JavaPlugin plugin;
    private final CosmeticRegistry registry;

    /** 플레이어별 현재 장착 트레일 ID. */
    private final Map<UUID, String> equipped = new HashMap<>();
    /** 플레이어별 파티클 스폰 태스크. */
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    /** 플레이어별 직전 스폰 위치. 이동 감지용. */
    private final Map<UUID, Location> lastLocations = new HashMap<>();

    public TrailHandler(JavaPlugin plugin, CosmeticRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void apply(Player wearer, CosmeticDefinition definition) {
        if (definition == null || definition.getType() != CosmeticType.TRAIL) return;

        clear(wearer); // 기존 태스크 정리
        equipped.put(wearer.getUniqueId(), definition.getId());
        startTask(wearer, definition);
    }

    public void clear(Player wearer) {
        UUID uuid = wearer.getUniqueId();
        BukkitTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
        equipped.remove(uuid);
        lastLocations.remove(uuid);
    }

    /** Join/Respawn/WorldChange 시 호출. 트레일은 단순히 태스크만 재시작하면 됨. */
    public void resend(Player wearer) {
        String id = equipped.get(wearer.getUniqueId());
        if (id == null) return;
        CosmeticDefinition def = registry.get(id);
        if (def == null) return;

        BukkitTask old = tasks.remove(wearer.getUniqueId());
        if (old != null) old.cancel();
        startTask(wearer, def);
    }

    public boolean isEquipped(UUID uuid) {
        return equipped.containsKey(uuid);
    }

    public String getEquippedId(UUID uuid) {
        return equipped.get(uuid);
    }

    public void shutdownAll() {
        for (BukkitTask t : tasks.values()) if (t != null) t.cancel();
        tasks.clear();
        equipped.clear();
        lastLocations.clear();
    }

    // ─── 내부 ───

    private void startTask(Player wearer, CosmeticDefinition def) {
        UUID uuid = wearer.getUniqueId();
        int interval = Math.max(1, def.getIntervalTicks());
        int count = Math.max(1, def.getParticleCount());

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) {
                    cancel();
                    tasks.remove(uuid);
                    return;
                }
                // 장착 해제 시 태스크가 clear에서 cancel되지만 방어적으로 체크
                if (!equipped.containsKey(uuid)) {
                    cancel();
                    return;
                }

                Location current = p.getLocation();
                Location last = lastLocations.get(uuid);
                if (last != null && last.getWorld() == current.getWorld()
                        && last.distanceSquared(current) < MIN_MOVE_DISTANCE_SQ) {
                    return; // 정지 상태 — 파티클 스킵
                }
                lastLocations.put(uuid, current.clone());

                Location particleLoc = current.clone().add(0, 0.1, 0);
                p.getWorld().spawnParticle(def.getParticle(), particleLoc,
                        count, 0.2, 0.05, 0.2, 0);
            }
        }.runTaskTimer(plugin, interval, interval);

        tasks.put(uuid, task);
    }
}
