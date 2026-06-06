package com.exit.farming.listener;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import com.exit.farming.item.CropItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 바닐라 작물(밀/당근/감자/비트) 서버 전역 봉인 + 잡초 드랍 변환.
 *
 * <h3>차단</h3>
 * - 플레이어가 바닐라 씨앗/당근/감자 들고 우클릭 설치 시 차단 메시지
 * - 디스펜서가 바닐라 씨앗 뿜어 심는 것도 차단
 *
 * <h3>드랍 변환 (잡초 → 우리 wheat_seed)</h3>
 * 잔디/큰풀/고사리 부수면 바닐라가 가끔 WHEAT_SEEDS 를 드랍하는데,
 * 그 시점에 우리 wheat_seed 로 교체. 무료 시작용 시드 공급원.
 * 다른 12작물은 여전히 상점에서만 구매 가능.
 *
 * config.yml의 vanilla-suppress: true 일 때만 활성.
 */
public class VanillaSuppressListener implements Listener {

    private final FarmingPlugin plugin;

    public VanillaSuppressListener(FarmingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("vanilla-suppress", true)) return;

        Material placed = event.getBlockPlaced().getType();
        if (placed != Material.WHEAT && placed != Material.CARROTS
                && placed != Material.POTATOES && placed != Material.BEETROOTS) {
            return;
        }

        // 우리 씨앗은 CropListener 가 직접 처리하므로 이 BlockPlace는 취소
        var inHand = event.getItemInHand();
        if (CropItem.isOurSeed(inHand)) {
            event.setCancelled(true);
            return;
        }

        // 바닐라 씨앗/작물
        if (CropItem.isVanillaSeed(inHand)) {
            event.setCancelled(true);
            Player p = event.getPlayer();
            p.sendMessage(Component.text(
                    "바닐라 작물은 이 서버에서 심을 수 없습니다. 전용 씨앗을 사용하세요.",
                    NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDispense(BlockDispenseEvent event) {
        if (!plugin.getConfig().getBoolean("vanilla-suppress", true)) return;
        var item = event.getItem();
        if (item == null) return;
        Material m = item.getType();
        if (m == Material.WHEAT_SEEDS || m == Material.CARROT
                || m == Material.POTATO || m == Material.BEETROOT_SEEDS) {
            if (CropItem.isVanillaSeed(item)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 잡초 부수면 나오는 바닐라 WHEAT_SEEDS → 우리 wheat_seed 교체.
     *
     * BlockDropItemEvent 는 BlockBreakEvent 직후 드랍이 결정될 때 발동.
     * 드랍 리스트(items)를 순회하며 바닐라 밀씨앗을 찾아 ItemStack 을 우리 씨앗으로 교체.
     */
    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("vanilla-suppress", true)) return;

        Material brokenType = event.getBlockState().getType();
        if (!isGrassLike(brokenType)) return;

        for (Item dropEntity : event.getItems()) {
            ItemStack stack = dropEntity.getItemStack();
            if (stack.getType() != Material.WHEAT_SEEDS) continue;
            // 바닐라 씨앗만 교체. 누군가 명령어로 PDC 박힌 우리 씨앗을 드랍해놨다면 그대로 둠.
            if (!CropItem.isVanillaSeed(stack)) continue;

            int amount = stack.getAmount();
            ItemStack ourSeed = CropItem.createSeed(Crop.byId("wheat"), amount);
            if (ourSeed != null) {
                dropEntity.setItemStack(ourSeed);
            }
        }
    }

    private static boolean isGrassLike(Material m) {
        // 1.21 에서 GRASS → SHORT_GRASS 로 리네임됨
        return m == Material.SHORT_GRASS
                || m == Material.TALL_GRASS
                || m == Material.FERN
                || m == Material.LARGE_FERN;
    }
}
