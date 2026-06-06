package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExpBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.life.LumberBonusEnchant;
import com.exit.customitems.lamp.enchant.impl.life.TreeCapacitorEnchant;
import com.exit.customitems.util.NumUtil;
import com.exit.customitems.util.RollUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 도끼 인챈트 2종 통합 처리.
 *
 * <ul>
 *   <li>트리 캐퍼: 채굴 블록이 원목이고 같은 종 연결 클러스터가 임계값 이상이면 한 번에 통째 벌목.
 *       (본체는 이벤트가 처리, 부수 블록은 {@link Block#breakNaturally(ItemStack)} 으로 처리해
 *        도끼의 효율/Fortune/Silk Touch 가 자연 적용된다.)</li>
 *   <li>벌목 보너스: 원목 채굴 시 Lv 별 확률로 원목 1개 추가 드랍.
 *       트리 캐퍼 발동 여부와 무관하게 본체 블록 1회분에 대해 1회 롤.</li>
 * </ul>
 *
 * 6면 인접만 클러스터로 본다 (대각선 제외).
 */
public class AxeListener implements Listener {

    private final EnchantDispatcher dispatcher;

    private final NamespacedKey kLumber;
    private final NamespacedKey kCapacitor;
    private final NamespacedKey kExpBoost;

    public AxeListener(Plugin plugin, EnchantDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.kLumber    = LumberBonusEnchant.keyOf(plugin);
        this.kCapacitor = TreeCapacitorEnchant.keyOf(plugin);
        this.kExpBoost  = ExpBoostEnchant.keyOf(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;
        if (!Tag.ITEMS_AXES.isTagged(tool.getType())) return;

        Block block = event.getBlock();
        Material type = block.getType();
        if (!Tag.LOGS.isTagged(type)) return;

        // --- 트리 캐퍼 ---
        // 현재 캐려는 블록 기준 같은 종 연결 클러스터 크기 ≤ N (= "남은 원목" 의 수) 이면
        // 클러스터를 통째 벌목. 큰 나무는 사용자가 줄여놓으면 마지막 한 방, 작은 나무는 즉시.
        Optional<RolledEnchant> cap = dispatcher.findInItem(tool, kCapacitor);
        if (cap.isPresent()) {
            int level = RollUtil.asCount(cap.get().values()[0]);
            int threshold = TreeCapacitorEnchant.levelToThreshold(level);
            List<Block> cluster = collectCluster(block, type, TreeCapacitorEnchant.CLUSTER_CAP);
            if (cluster.size() <= threshold) {
                for (Block b : cluster) {
                    if (b.equals(block)) continue;  // 본체는 이벤트가 처리
                    b.breakNaturally(tool);
                    // 경험치 획득량 — 트리 캐퍼 부수 블록 각각 독립 롤
                    ExpBoostEnchant.maybeDropExpOrbs(dispatcher, kExpBoost, player, b.getLocation());
                }
            }
        }

        // --- 벌목 보너스 ---
        Optional<RolledEnchant> bonus = dispatcher.findInItem(tool, kLumber);
        if (bonus.isPresent()) {
            int level = RollUtil.asCount(bonus.get().values()[0]);
            int percent = LumberBonusEnchant.levelToPercent(level);
            if (RollUtil.percentRoll(NumUtil.toStored(percent))) {
                block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    new ItemStack(type)
                );
            }
        }

        // 경험치 획득량 — 본체 블록 1회분
        ExpBoostEnchant.maybeDropExpOrbs(dispatcher, kExpBoost, player, block.getLocation());
    }

    /**
     * 시작 블록에서 같은 Material 인 6면 인접 블록을 BFS 로 수집. 본체 포함. cap 까지만.
     */
    private List<Block> collectCluster(Block start, Material match, int cap) {
        List<Block> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(packKey(start));

        BlockFace[] faces = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
        };

        while (!queue.isEmpty() && out.size() < cap) {
            Block cur = queue.poll();
            out.add(cur);
            for (BlockFace f : faces) {
                Block adj = cur.getRelative(f);
                if (adj.getType() != match) continue;
                long k = packKey(adj);
                if (seen.add(k)) {
                    queue.add(adj);
                }
            }
        }
        return out;
    }

    private static long packKey(Block b) {
        // 좌표를 64bit 하나로 패킹 (월드 비교는 같은 청크 내 동일 가정 — BFS 가 이웃만 따라가므로 안전).
        long x = b.getX() & 0x3FFFFFFL;
        long y = b.getY() & 0xFFFL;
        long z = b.getZ() & 0x3FFFFFFL;
        return (x << 38) | (z << 12) | y;
    }
}
