package com.exit.customitems.lamp.enchant.listener;

import com.exit.core.api.CropItemProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.customitems.lamp.ToolCategory;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.life.CropBoneMealEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExpBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.life.HoeMasterEnchant;
import com.exit.customitems.lamp.enchant.impl.life.LuckyCropEnchant;
import com.exit.customitems.util.NumUtil;
import com.exit.customitems.util.RollUtil;
import com.exit.farming.crop.Crop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 농작물(Ageable) 수확 시 인챈트 처리.
 *
 * <ul>
 *   <li>농작물 뼛가루 — 모든 생활도구, stackable, 본체 + 부수 작물 모두</li>
 *   <li>행운의 작물 — 괭이 전용, Lv 1~5, 본체 + 부수 작물 각각 독립 롤 → 작물 +2 추가 드랍</li>
 *   <li>괭이의 달인 — 괭이 전용, Lv 1~2, 시선 방향 앞쪽 N칸 작물 동시 수확</li>
 * </ul>
 *
 * <p><b>괭이의 달인 동작</b> — 부수 작물 각각에 대해 {@link BlockBreakEvent} 를 명시적으로 발생시켜
 * Farming 플러그인의 농부 exp + 자동 재심기, 보호 플러그인의 영역 체크 등을 자연스럽게 통합.
 * 재진입 가드({@link #processingPositions})로 무한 루프 방지 — 부수 작물의 BlockBreakEvent 가
 * 다시 HarvestListener 로 들어와도 hoeMaster 분기는 skip, 다른 분기들(뼛가루/행운/경험치)은 정상 발동.
 */
public class HarvestListener implements Listener {

    /** 수확 대상으로 인정하는 작물 블록들. */
    private static final Set<Material> CROP_BLOCKS = EnumSet.of(
        Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
        Material.NETHER_WART, Material.COCOA
    );

    /** 작물 블록 → 드랍할 작물 아이템 매핑 (행운의 작물 보너스용). */
    private static final Map<Material, Material> CROP_ITEM = new EnumMap<>(Material.class);

    static {
        CROP_ITEM.put(Material.WHEAT,       Material.WHEAT);
        CROP_ITEM.put(Material.CARROTS,     Material.CARROT);
        CROP_ITEM.put(Material.POTATOES,    Material.POTATO);
        CROP_ITEM.put(Material.BEETROOTS,   Material.BEETROOT);
        CROP_ITEM.put(Material.NETHER_WART, Material.NETHER_WART);
        CROP_ITEM.put(Material.COCOA,       Material.COCOA_BEANS);
    }

    private final EnchantDispatcher dispatcher;
    private final NamespacedKey boneMealKey;
    private final NamespacedKey luckyCropKey;
    private final NamespacedKey hoeMasterKey;
    private final NamespacedKey expBoostKey;

    /**
     * 괭이의 달인 처리 중인 부수 작물 좌표 추적. onBreak 재진입 시 hoeMaster 분기만 skip 하여
     * 무한 루프 방지. 메인 스레드 단일 가정 — 동기화 불필요.
     */
    private final Set<Long> processingPositions = new HashSet<>();

    /**
     * Farming 플러그인 활성 여부 캐시. 핫패스(BlockBreakEvent)마다 isPluginEnabled 호출 회피.
     * Farming 이 enable/disable 되어도 서버 재시작 전까지 변하지 않으므로 안전.
     */
    private final boolean farmingEnabled;

    public HarvestListener(Plugin plugin, EnchantDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.boneMealKey  = CropBoneMealEnchant.keyOf(plugin);
        this.luckyCropKey = LuckyCropEnchant.keyOf(plugin);
        this.hoeMasterKey = HoeMasterEnchant.keyOf(plugin);
        this.expBoostKey  = ExpBoostEnchant.keyOf(plugin);
        this.farmingEnabled = Bukkit.getPluginManager().isPluginEnabled("Farming");
    }

    private static long packPos(Block b) {
        return ((long)(b.getX() & 0x3FFFFFF) << 38)
             | ((long)(b.getZ() & 0x3FFFFFF) << 12)
             | (b.getY() & 0xFFF);
    }

    // HIGHEST + ignoreCancelled=true: Land 등이 HIGH 에서 cancel 하면 우리는 자동 skip →
    // 보호 청크에서 좌클릭해도 뼛가루/행운의작물/exp 보너스 드랍 누수 없음.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!CROP_BLOCKS.contains(type)) return;

        // 자란 작물만 "수확"으로 간주.
        // Farming 활성 시: Crop.matchingMature 가 13개 커스텀 작물 모두 정확히 매칭 (mature age 가 vanilla maxAge 와 다른 9종 포함)
        // Farming 미설치 시: vanilla maxAge 체크로 폴백
        boolean mature;
        if (farmingEnabled) {
            mature = Crop.matchingMature(block) != null;
        } else {
            BlockData data = block.getBlockData();
            mature = (data instanceof Ageable a) && a.getAge() == a.getMaximumAge();
        }
        if (!mature) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean isHoe = tool != null && Tag.ITEMS_HOES.isTagged(tool.getType());

        boolean isReentry = processingPositions.contains(packPos(block));

        // 1. 농작물 뼛가루 (모든 생활도구, stackable) — 본체 + 부수 모두
        if (ToolCategory.isLifeTool(tool)) {
            for (RolledEnchant r : dispatcher.findAllInItem(tool, boneMealKey)) {
                int[] v = r.values();
                if (RollUtil.percentRoll(v[0])) {
                    int count = RollUtil.asCount(v[1]);
                    block.getWorld().dropItemNaturally(block.getLocation(),
                        new ItemStack(Material.BONE_MEAL, count));
                }
            }
        }

        // 2. 행운의 작물 (괭이 전용) — 본체 + 부수 모두, 각 작물에 독립 롤
        if (isHoe) {
            applyLuckyCrop(player, tool, block, type);
        }

        // 3. 괭이의 달인 (괭이 전용) — 재진입 시 skip (무한 루프 방지)
        //    본체에서만 발동. 부수 작물의 재진입 시 hoeMaster 다시 발동하지 않음.
        if (isHoe && !isReentry) {
            Optional<RolledEnchant> master = dispatcher.findInItem(tool, hoeMasterKey);
            if (master.isPresent()) {
                int level = (int) NumUtil.fromStored(master.get().values()[0]);
                applyHoeMaster(player, tool, block, level);
            }
        }

        // 4. 경험치 획득량 — 본체 + 부수 모두 (재진입 시에도 발동)
        ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, block.getLocation());
    }

    /** 본체 또는 부수 작물 블록에 행운의 작물 효과 적용. */
    private void applyLuckyCrop(Player player, ItemStack tool, Block block, Material type) {
        Optional<RolledEnchant> lucky = dispatcher.findInItem(tool, luckyCropKey);
        if (lucky.isEmpty()) return;

        int level = (int) NumUtil.fromStored(lucky.get().values()[0]);
        int percent = LuckyCropEnchant.levelToPercent(level);
        if (!RollUtil.percentRoll(NumUtil.toStored(percent))) return;

        ItemStack bonus = createBonusItem(block);
        if (bonus == null) return;
        block.getWorld().dropItemNaturally(block.getLocation(), bonus);
    }

    /**
     * 블록 위치의 작물에 맞는 보너스 ItemStack 생성.
     * <ul>
     *   <li>1순위: Farming 플러그인의 Crop.matchingMature → CropItemProvider.createFruit
     *       (콜리플라워/포도/토마토 등 13개 커스텀 작물 자동 지원)</li>
     *   <li>2순위: vanilla 작물 매핑 (Farming 미설치 환경 fallback)</li>
     * </ul>
     * 매칭 안 되면 null 반환.
     */
    private ItemStack createBonusItem(Block block) {
        if (farmingEnabled) {
            Crop crop = Crop.matchingMature(block);
            if (crop != null) {
                CropItemProvider provider = ServiceRegistry.get(CropItemProvider.class).orElse(null);
                if (provider != null) {
                    return provider.createFruit(crop.id(), LuckyCropEnchant.BONUS_AMOUNT, false);
                }
            }
        }
        Material item = CROP_ITEM.get(block.getType());
        if (item == null) return null;
        return new ItemStack(item, LuckyCropEnchant.BONUS_AMOUNT);
    }

    /**
     * 괭이의 달인 — 플레이어 시선 방향(N/S/E/W)으로 앞쪽 (level)칸의 작물 동시 수확.
     *
     * <p>각 부수 작물에 대해 {@link BlockBreakEvent} 를 명시적으로 발생시킨다.
     * 이를 통해 다음이 모두 자동 처리됨:
     * <ul>
     *   <li>Farming 플러그인의 CropListener — 농부 exp 부여 + 자동 재심기 (Lv10)</li>
     *   <li>HarvestListener 자기 자신 재진입 — 행운의 작물 / 경험치 획득량 / 뼛가루 분기</li>
     *   <li>다른 보호 플러그인 — 영역 보호 시 event.isCancelled() 자동 적용</li>
     * </ul>
     * 재진입 가드로 hoeMaster 분기는 skip 되어 무한 루프 없음.
     */
    private void applyHoeMaster(Player player, ItemStack tool, Block center, int level) {
        BlockFace facing = player.getFacing();
        for (int i = 1; i <= level; i++) {
            Block ahead = center.getRelative(facing, i);
            Material aheadType = ahead.getType();
            if (!CROP_BLOCKS.contains(aheadType)) continue;

            // 자란 작물만 — onBreak 와 동일하게 Farming 매칭 우선
            boolean mature;
            if (farmingEnabled) {
                mature = Crop.matchingMature(ahead) != null;
            } else {
                BlockData adata = ahead.getBlockData();
                mature = (adata instanceof Ageable a) && a.getAge() == a.getMaximumAge();
            }
            if (!mature) continue;

            long pos = packPos(ahead);
            processingPositions.add(pos);
            try {
                BlockBreakEvent sub = new BlockBreakEvent(ahead, player);
                Bukkit.getPluginManager().callEvent(sub);
                if (sub.isCancelled()) continue;

                // 다른 listener 가 setDropItems(false) 했다면 vanilla 드롭 스킵 (블록만 제거)
                if (sub.isDropItems()) {
                    ahead.breakNaturally(tool);
                } else {
                    ahead.setType(Material.AIR);
                }
            } finally {
                processingPositions.remove(pos);
            }
        }
    }
}
