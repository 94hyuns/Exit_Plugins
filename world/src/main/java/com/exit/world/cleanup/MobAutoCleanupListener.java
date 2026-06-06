package com.exit.world.cleanup;

import com.exit.world.manager.WorldConfig;
import com.exit.world.manager.WorldManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Set;

/**
 * worlds.yml 의 auto-cleanup-on-empty: true 인 월드가 빈 상태로 10초 지속되면
 * 해당 월드의 모든 MythicMobs 액티브 mob 을 제거.
 *
 * MM 이 청크 언로드 시 mob 데이터를 안전하게 직렬화 못하면, 재방문 시 vanilla
 * 엔티티로 잔존하면서 AI/스킬 잃음 (스켈레톤이 가만히 서있는 버그).
 * 빈 월드에서 일괄 정리하면 다음 입장 시 spawner 가 새 mob 을 깨끗하게 생성.
 */
public class MobAutoCleanupListener implements Listener {

    private static final long CLEANUP_DELAY_TICKS = 200L; // 10초

    /**
     * 자동 청소 면제 MythicMobs internal name 화이트리스트.
     * 사용자가 직접 설치한 utility mob 들 — auto-cleanup 대상에서 제외.
     * (cooking pack 의 요리 솥 등 — 던전 내 영구 배치를 의도한 mob)
     */
    private static final Set<String> CLEANUP_EXEMPT_TYPES = Set.of(
            "cooking_pot",
            "cooking_pot_parts",
            "cookingpot_splash"
    );

    private final JavaPlugin plugin;
    private final WorldManager worldManager;

    public MobAutoCleanupListener(JavaPlugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        scheduleCheck(event.getFrom().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scheduleCheck(event.getPlayer().getWorld().getName());
    }

    private void scheduleCheck(String worldName) {
        WorldConfig wc = findByWorldName(worldName);
        if (wc == null || !wc.isAutoCleanupOnEmpty()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return;
            if (!w.getPlayers().isEmpty()) return; // 누가 다시 들어옴 → 정리 취소
            cleanupMobs(w);
        }, CLEANUP_DELAY_TICKS);
    }

    private WorldConfig findByWorldName(String worldName) {
        for (WorldConfig wc : worldManager.getAllWorlds()) {
            if (wc.getWorldName().equals(worldName)) return wc;
        }
        return null;
    }

    private void cleanupMobs(World w) {
        try {
            MythicBukkit mm = MythicBukkit.inst();
            if (mm == null) return;
            int killed = 0;
            // ConcurrentModificationException 회피용 스냅샷
            for (ActiveMob am : new ArrayList<>(mm.getMobManager().getActiveMobs())) {
                if (am.getEntity() == null) continue;
                Entity e = am.getEntity().getBukkitEntity();
                if (e == null || !e.getWorld().equals(w)) continue;
                // 화이트리스트 mob 은 보존 (사용자가 의도적으로 설치한 utility mob)
                String type = am.getType() != null ? am.getType().getInternalName() : null;
                if (type != null && CLEANUP_EXEMPT_TYPES.contains(type)) continue;
                am.remove();
                killed++;
            }
            plugin.getLogger().info("[World] " + w.getName() + " auto-cleanup: MM mob "
                    + killed + "개 정리 (월드 비어있음)");
        } catch (Throwable t) {
            plugin.getLogger().warning("[World] auto-cleanup 에러: " + t.getMessage());
        }
    }
}
