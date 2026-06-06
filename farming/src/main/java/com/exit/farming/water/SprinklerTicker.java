package com.exit.farming.water;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 스프링쿨러 주기 작동.
 *
 * <p>틱 주기 동안 모든 sprinklers.yml 항목을 순회한다:
 * <ol>
 *   <li>그 위치의 실제 블록이 BARREL 인지 확인 (아니면 자동 unregister — 누군가 덮어썼거나 청크가 손상됐을 때 안전)</li>
 *   <li>주변 3×3 (중앙=스프링클러 자신) farmland 블록을 찾아 적심.
 *       단, 위 작물이 mature 면 skip — 사용자가 수확 후 재심기 했을 때만 자연 dry 후 다시 wet.</li>
 *   <li>물 파티클 약간 출력 (시각 피드백)</li>
 * </ol>
 *
 * <p>비활성 청크는 건드리지 않음 (로드된 청크만). 적심은 같은 Y plane 만 (수직 확장 없음).
 */
public class SprinklerTicker extends BukkitRunnable {

    private final FarmingPlugin plugin;
    private final SprinklerStore store;
    private final CropTracker cropTracker;

    public SprinklerTicker(FarmingPlugin plugin, SprinklerStore store, CropTracker cropTracker) {
        this.plugin = plugin;
        this.store = store;
        this.cropTracker = cropTracker;
    }

    public void start() {
        long period = plugin.getConfig().getLong("sprinkler.tick-period-seconds", 5L) * 20L;
        runTaskTimer(plugin, period, period);
    }

    @Override
    public void run() {
        List<SprinklerStore.Sprinkler> stale = new ArrayList<>();
        for (SprinklerStore.Sprinkler s : store.all()) {
            try {
                if (!s.loc().getWorld().isChunkLoaded(s.loc().getBlockX() >> 4, s.loc().getBlockZ() >> 4)) {
                    continue; // 비활성 청크 — 다음 틱에 처리
                }
                Block self = s.loc().getBlock();
                if (self.getType() == Material.BARREL) {
                    self.setType(Material.BARRIER, false);
                } else if (self.getType() != Material.BARRIER) {
                    plugin.getLogger().warning("스프링클러 stale @ " + s.key()
                            + " (실제 블록=" + self.getType() + ")");
                    stale.add(s);
                    safe(() -> SprinklerDisplay.remove(self));
                    continue;
                }

                // 파티클 먼저 — entity/wet 가 실패해도 시각적 확인은 보장
                self.getWorld().spawnParticle(
                        Particle.SPLASH,
                        self.getLocation().add(0.5, 1.1, 0.5),
                        8, 0.4, 0.05, 0.4, 0.0
                );

                safe(() -> SprinklerDisplay.ensure(self, s.tier()));
                safe(() -> wetAround(self));
            } catch (Throwable t) {
                plugin.getLogger().warning("스프링클러 tick 예외 @ " + s.key() + ": " + t);
            }
        }
        for (SprinklerStore.Sprinkler dead : stale) {
            store.unregister(dead.loc());
            plugin.getLogger().info("스프링클러 위치 정리: " + dead.key() + " (블록 없음).");
        }
    }

    private interface ThrowingRun { void run() throws Throwable; }
    private void safe(ThrowingRun r) {
        try { r.run(); } catch (Throwable t) {
            plugin.getLogger().warning("스프링클러 sub-op 실패: " + t);
        }
    }

    /**
     * 철제 스프링클러 3×3 적심. 중앙 = 스프링클러 자신, 주변 8칸 farmland 가 대상.
     *
     * <p>수직 Y 범위 ±1 — sprinkler 가 farmland 위에 배치되든(by-1 에 farmland) farmland 와 같은
     * 평면에 있든(by 에 farmland) 둘 다 커버. 없으면 sprinkler 가 빈 공기에 물 뿌리고 끝남.
     */
    private void wetAround(Block sprinkler) {
        int bx = sprinkler.getX(), by = sprinkler.getY(), bz = sprinkler.getZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // 중앙은 스프링클러 자신
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = sprinkler.getWorld().getBlockAt(bx + dx, by + dy, bz + dz);
                    wetIfFarmland(b);
                }
            }
        }
    }

    private void wetIfFarmland(Block b) {
        if (b.getType() != Material.FARMLAND) return;
        BlockData data = b.getBlockData();
        // Paper 1.21+: FARMLAND 는 Farmland 인터페이스, Ageable 아님
        if (!(data instanceof org.bukkit.block.data.type.Farmland farm)) return;
        int max = farm.getMaximumMoisture();
        if (farm.getMoisture() >= max) return;
        // 위 작물이 다 자란 상태면 재적심 안 함 — 사용자가 수확 후 재심기 했을 때만 다시 wet.
        if (isMatureCropAbove(b)) return;
        farm.setMoisture(max);
        b.setBlockData(farm, false);
    }

    /**
     * farmland 위 블록이 mature 작물이면 true.
     * 1순위: tracker 등록된 우리 작물 → 우리 mature 매칭.
     * 2순위: 바닐라 Ageable fallback (age == maxAge).
     * 빈 블록 / 작물 아님 / seedling 은 모두 false (= 적심 허용).
     */
    private boolean isMatureCropAbove(Block farmland) {
        Block above = farmland.getRelative(0, 1, 0);
        if (cropTracker != null) {
            Crop tracked = cropTracker.get(above);
            if (tracked != null) {
                return tracked.matchesMature(above);
            }
        }
        BlockData data = above.getBlockData();
        if (!(data instanceof Ageable ageable)) return false;
        return ageable.getAge() >= ageable.getMaximumAge();
    }
}
