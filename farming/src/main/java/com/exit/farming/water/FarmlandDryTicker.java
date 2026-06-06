package com.exit.farming.water;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.farmland.FarmlandClaimManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 클레임된 경작지를 주기적으로 스캔해서 직접 dry 처리.
 *
 * <p><b>왜 필요한가</b>: vanilla 의 FarmBlock.tick 은
 * <ul>
 *   <li>물 인접 OR 비 → moisture=7 으로 wet 시도 (우리가 cancel)</li>
 *   <li>else → moisture 1 감소 (drying)</li>
 * </ul>
 * 즉 물 인접하면 drying 분기를 아예 진입 안 함 → 한 번 wet 된 farmland 가 영원히 7 유지.
 * 우리가 별도 task 로 직접 dry 시켜야 함.
 *
 * <p><b>로직</b>: 매 N초 마다 클레임된 farmland 전체 순회:
 * <ul>
 *   <li>위에 자라고 있는 작물 있으면 skip (우리 정책상 wet 유지)</li>
 *   <li>아니면 moisture-- (0 까지)</li>
 * </ul>
 *
 * <p>비활성 청크는 건너뜀.
 */
public class FarmlandDryTicker extends BukkitRunnable {

    private final FarmingPlugin plugin;
    private final FarmlandClaimManager claimMgr;

    public FarmlandDryTicker(FarmingPlugin plugin, FarmlandClaimManager claimMgr) {
        this.plugin = plugin;
        this.claimMgr = claimMgr;
    }

    public void start() {
        long period = plugin.getConfig().getLong("farmland-dry.tick-period-seconds", 30L) * 20L;
        runTaskTimer(plugin, period, period);
    }

    @Override
    public void run() {
        Map<String, UUID> claims = new HashMap<>(claimMgr.getAll()); // 안전 복사
        for (String key : claims.keySet()) {
            String[] parts = key.split(":");
            if (parts.length != 4) continue;
            try {
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) continue;
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                Block block = world.getBlockAt(x, y, z);
                if (block.getType() != Material.FARMLAND) continue;
                BlockData data = block.getBlockData();
                if (!(data instanceof org.bukkit.block.data.type.Farmland farm)) continue;
                int cur = farm.getMoisture();
                if (cur <= 0) continue;
                // 작물 자라는 중이면 wet 유지
                if (hasGrowingCropAbove(block)) continue;
                farm.setMoisture(cur - 1);
                block.setBlockData(farm, false);
            } catch (NumberFormatException ignored) {
                // 깨진 키 무시
            }
        }
    }

    private static boolean hasGrowingCropAbove(Block farmland) {
        Block above = farmland.getRelative(0, 1, 0);
        Material m = above.getType();
        if (m != Material.WHEAT && m != Material.CARROTS && m != Material.POTATOES
                && m != Material.BEETROOTS && m != Material.MELON_STEM
                && m != Material.PUMPKIN_STEM && m != Material.ATTACHED_MELON_STEM
                && m != Material.ATTACHED_PUMPKIN_STEM
                && m != Material.TORCHFLOWER_CROP && m != Material.PITCHER_CROP) {
            return false;
        }
        BlockData data = above.getBlockData();
        if (!(data instanceof Ageable ageable)) return false;
        return ageable.getAge() < ageable.getMaximumAge();
    }
}
