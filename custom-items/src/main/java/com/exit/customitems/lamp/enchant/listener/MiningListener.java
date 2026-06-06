package com.exit.customitems.lamp.enchant.listener;

import com.exit.core.registry.ServiceRegistry;
import com.exit.customitems.lamp.WildernessChecker;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AdjacentMineEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExpBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExplosiveMineEnchant;
import com.exit.customitems.lamp.enchant.impl.life.SandToGlassEnchant;
import com.exit.customitems.lamp.enchant.impl.life.SmeltedIngotEnchant;
import com.exit.customitems.util.NumUtil;
import com.exit.customitems.util.RollUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.RayTraceResult;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 곡괭이/삽 인챈트 4종 통합 처리.
 *
 * <p><b>공통 블록 처리 규칙:</b> 주변 채굴/폭발 채굴로 부수 파괴되는 블록에도
 * 주괴 드랍 인챈트가 적용된다. 즉 본체뿐 아니라 부수 광석도 주괴로 드랍.
 * ({@link #breakOneBlock} 을 통해 일관 처리)
 *
 * <p><b>축 계산 규칙:</b> 주변 채굴 Lv.1/Lv.2, 폭발 채굴 모두 플레이어가 클릭한 블록면을
 * 기준으로 시선 평면의 두 축({@link SightAxes}) 을 구해서 사용한다.
 * <ul>
 *   <li>주변 채굴 Lv.1: axisA ±1 (본체 포함 3칸 기둥)</li>
 *   <li>주변 채굴 Lv.2: axisA ±1 + axisB ±1 (본체 포함 5칸 십자)</li>
 *   <li>폭발 채굴: 시선 평면 3x3 × 깊이 3 = 27칸 큐브</li>
 * </ul>
 *
 * <p><b>중복 방지:</b> 폭발 채굴이 성공 발동하면 주변 채굴은 스킵.
 */
public class MiningListener implements Listener {

    /** 제련 가능 광석 → 주괴 매핑. */
    private static final Map<Material, Material> SMELTABLE = new EnumMap<>(Material.class);
    static {
        SMELTABLE.put(Material.IRON_ORE,             Material.IRON_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_IRON_ORE,   Material.IRON_INGOT);
        SMELTABLE.put(Material.GOLD_ORE,             Material.GOLD_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_GOLD_ORE,   Material.GOLD_INGOT);
        SMELTABLE.put(Material.COPPER_ORE,           Material.COPPER_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELTABLE.put(Material.ANCIENT_DEBRIS,       Material.NETHERITE_SCRAP);
    }

    /** 주괴 드랍 Lv2 보너스 대상 — 주괴 변환 없이 그 광석의 드랍 자체를 +1 추가하는 광석. */
    private static final Map<Material, Material> BONUS_ORE = new EnumMap<>(Material.class);
    static {
        BONUS_ORE.put(Material.DIAMOND_ORE,             Material.DIAMOND);
        BONUS_ORE.put(Material.DEEPSLATE_DIAMOND_ORE,   Material.DIAMOND);
        BONUS_ORE.put(Material.EMERALD_ORE,             Material.EMERALD);
        BONUS_ORE.put(Material.DEEPSLATE_EMERALD_ORE,   Material.EMERALD);
        BONUS_ORE.put(Material.LAPIS_ORE,               Material.LAPIS_LAZULI);
        BONUS_ORE.put(Material.DEEPSLATE_LAPIS_ORE,     Material.LAPIS_LAZULI);
        BONUS_ORE.put(Material.REDSTONE_ORE,            Material.REDSTONE);
        BONUS_ORE.put(Material.DEEPSLATE_REDSTONE_ORE,  Material.REDSTONE);
        BONUS_ORE.put(Material.NETHER_QUARTZ_ORE,       Material.QUARTZ);
        BONUS_ORE.put(Material.COAL_ORE,                Material.COAL);
        BONUS_ORE.put(Material.DEEPSLATE_COAL_ORE,      Material.COAL);
    }

    private final Plugin plugin;
    private final EnchantDispatcher dispatcher;
    private final WildernessChecker wilderness;

    private final NamespacedKey smeltedKey;
    private final NamespacedKey adjacentKey;
    private final NamespacedKey explosiveKey;
    private final NamespacedKey sandGlassKey;
    private final NamespacedKey expBoostKey;

    public MiningListener(Plugin plugin, EnchantDispatcher dispatcher, WildernessChecker wilderness) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.wilderness = wilderness;
        this.smeltedKey    = SmeltedIngotEnchant.keyOf(plugin);
        this.adjacentKey   = AdjacentMineEnchant.keyOf(plugin);
        this.explosiveKey  = ExplosiveMineEnchant.keyOf(plugin);
        this.sandGlassKey  = SandToGlassEnchant.keyOf(plugin);
        this.expBoostKey   = ExpBoostEnchant.keyOf(plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        // --- 삽 인챈트 ---
        if (Tag.ITEMS_SHOVELS.isTagged(tool.getType())) {
            if ((blockType == Material.SAND || blockType == Material.RED_SAND)
                && dispatcher.findInItem(tool, sandGlassKey).isPresent()) {
                event.setDropItems(false);
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.GLASS));
            }
            // 경험치 획득량 — 삽질 1회분
            ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, block.getLocation());
            return;
        }

        // --- 곡괭이 인챈트 ---
        if (!Tag.ITEMS_PICKAXES.isTagged(tool.getType())) return;

        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // 주괴 드랍 인챈트 Lv 읽기 (없으면 0, 섬세한 손길과 배타)
        int smeltLv = 0;
        if (!silkTouch) {
            Optional<RolledEnchant> smelt = dispatcher.findInItem(tool, smeltedKey);
            if (smelt.isPresent()) smeltLv = RollUtil.asCount(smelt.get().values()[0]);
        }

        // 본체 블록에 주괴 드랍 / 광석 보너스 적용
        if (smeltLv >= 1 && SMELTABLE.containsKey(blockType) && event.isDropItems()) {
            // 제련 광석 → 주괴 N개 (Lv1=1, Lv2=2)
            event.setDropItems(false);
            Material ingot = SMELTABLE.get(blockType);
            int count = (smeltLv >= 2) ? 2 : 1;
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(ingot, count));
            spawnExpOrb(block.getLocation(), 1);
        } else if (smeltLv >= 2 && BONUS_ORE.containsKey(blockType) && event.isDropItems()) {
            // 다이아/에메랄드/라피스/레드스톤/석탄/네더쿼츠 — vanilla(Fortune 포함) 드랍 + 광석 1개 추가
            block.getWorld().dropItemNaturally(block.getLocation(),
                    new ItemStack(BONUS_ORE.get(blockType)));
        }

        // 야생 한정 부수 채굴
        if (!wilderness.isWilderness(block.getLocation())) return;

        boolean exploded = false;
        Optional<RolledEnchant> explosive = dispatcher.findInItem(tool, explosiveKey);
        if (explosive.isPresent()) {
            int level = RollUtil.asCount(explosive.get().values()[0]);
            int percent = ExplosiveMineEnchant.levelToPercent(level);
            if (RollUtil.percentRoll(NumUtil.toStored(percent))) {
                applyExplosiveMine(player, block, tool, smeltLv);
                exploded = true;
            }
        }

        if (!exploded) {
            Optional<RolledEnchant> adjacent = dispatcher.findInItem(tool, adjacentKey);
            if (adjacent.isPresent()) {
                int level = RollUtil.asCount(adjacent.get().values()[0]);
                applyAdjacentMine(player, block, tool, level, smeltLv);
            }
        }

        // 경험치 획득량 — 본체 블록 (폭발/주변 채굴 부수 블록은 breakOneBlock 에서 각각 독립 롤)
        ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, block.getLocation());
    }

    // --- 시선 축 계산 (주변/폭발 채굴 공통) ---

    private record SightAxes(BlockFace normal, BlockFace axisA, BlockFace axisB) {}

    private SightAxes computeSightAxes(Player player) {
        RayTraceResult ray = player.rayTraceBlocks(6.0);
        BlockFace normal = (ray != null && ray.getHitBlockFace() != null)
            ? ray.getHitBlockFace() : BlockFace.UP;
        BlockFace axisA;
        BlockFace axisB;
        switch (normal) {
            case UP, DOWN     -> { axisA = BlockFace.NORTH; axisB = BlockFace.EAST;  }
            case NORTH, SOUTH -> { axisA = BlockFace.UP;    axisB = BlockFace.EAST;  }
            case EAST, WEST   -> { axisA = BlockFace.UP;    axisB = BlockFace.NORTH; }
            default           -> { axisA = BlockFace.NORTH; axisB = BlockFace.EAST;  }
        }
        return new SightAxes(normal, axisA, axisB);
    }

    // --- 주변 채굴 (시선 평면 기반) ---

    /**
     * Lv.1: axisA ±1 (본체 포함 3칸 기둥). 벽 캐면 상하 2칸, 바닥/천장 캐면 북남 2칸.
     * Lv.2: axisA ±1 + axisB ±1 (본체 포함 5칸 십자).
     */
    private void applyAdjacentMine(Player player, Block center, ItemStack tool,
                                   int level, int smeltLv) {
        SightAxes axes = computeSightAxes(player);
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // axisA ±1
        breakOneBlock(center.getRelative(axes.axisA),  tool, smeltLv, silkTouch, player);
        breakOneBlock(center.getRelative(axes.axisA.getOppositeFace()), tool, smeltLv, silkTouch, player);

        if (level >= 2) {
            // axisB ±1
            breakOneBlock(center.getRelative(axes.axisB), tool, smeltLv, silkTouch, player);
            breakOneBlock(center.getRelative(axes.axisB.getOppositeFace()), tool, smeltLv, silkTouch, player);
        }
    }

    // --- 폭발 채굴 (3x3x3 시선 큐브) ---

    /**
     * 시선 평면 3x3 × 깊이 3 = 27블록. 본체 포함.
     * 깊이 축은 normal 의 반대 방향 (플레이어가 바라본 블록 내부 방향).
     */
    private void applyExplosiveMine(Player player, Block center, ItemStack tool,
                                    int smeltLv) {
        SightAxes axes = computeSightAxes(player);
        BlockFace depth = axes.normal.getOppositeFace();
        boolean silkTouch = tool.containsEnchantment(Enchantment.SILK_TOUCH);

        for (int d = 0; d < 3; d++) {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (d == 0 && a == 0 && b == 0) continue;  // 본체는 이벤트가 처리
                    Block target = center.getRelative(
                        depth.getModX() * d + axes.axisA.getModX() * a + axes.axisB.getModX() * b,
                        depth.getModY() * d + axes.axisA.getModY() * a + axes.axisB.getModY() * b,
                        depth.getModZ() * d + axes.axisA.getModZ() * a + axes.axisB.getModZ() * b
                    );
                    breakOneBlock(target, tool, smeltLv, silkTouch, player);
                }
            }
        }
    }

    // --- 공통 블록 처리: 주괴 드랍 판정 포함 ---

    /**
     * 블록 하나를 적절히 파괴한다.
     * 주괴 드랍 인챈트가 활성 상태이고 제련 대상 광석이면 → 블록을 AIR로 바꾸고 주괴 드롭.
     * 그 외에는 bukkit {@link Block#breakNaturally(ItemStack)} 으로 바닐라 드롭.
     *
     * <p>기반암/배리어/커맨드블록/구조블록/강화심층암 등 바닐라 파괴 불가 블록은 무시.
     * 판정 기준: {@code Material.getHardness() < 0}.
     */
    private void breakOneBlock(Block block, ItemStack tool, int smeltLv,
                               boolean silkTouch, Player player) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return;
        if (type.getHardness() < 0) return;  // 기반암/배리어/커맨드블록 등 파괴 불가

        if (smeltLv >= 1 && SMELTABLE.containsKey(type)) {
            // 제련 광석 → 주괴 N개 (Lv1=1, Lv2=2)
            Material ingot = SMELTABLE.get(type);
            int count = (smeltLv >= 2) ? 2 : 1;
            block.setType(Material.AIR);
            block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(ingot, count));
            spawnExpOrb(block.getLocation(), 1);
            grantMinerExp(player, type, silkTouch);
            ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, block.getLocation());
            return;
        }

        // 다이아/에메랄드 등 — vanilla(Fortune 포함) 드랍 후 Lv2 면 광석 +1 추가
        Location loc = block.getLocation();
        boolean bonusOre = (smeltLv >= 2) && BONUS_ORE.containsKey(type);
        Material bonusItem = bonusOre ? BONUS_ORE.get(type) : null;

        block.breakNaturally(tool);
        if (bonusOre) {
            loc.getWorld().dropItemNaturally(loc, new ItemStack(bonusItem));
        }
        grantMinerExp(player, type, silkTouch);
        // 경험치 획득량 — 부수 채굴 블록 각각 독립 롤 (폭발 채굴 27 블록 / 주변 채굴 2~4 블록)
        ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, loc);
    }

    /** Job 광부 EXP 위임. silk-touch 이거나 Job 미설치/미등록 시 no-op. */
    private void grantMinerExp(Player player, Material type, boolean silkTouch) {
        if (silkTouch || player == null) return;
        try {
            ServiceRegistry.get(com.exit.job.api.MiningExpHook.class)
                    .ifPresent(hook -> hook.grantOreBreak(player.getUniqueId(), type));
        } catch (NoClassDefFoundError e) {
            // Job 플러그인 미설치 — 조용히 무시
        }
    }

    private void spawnExpOrb(Location location, int amount) {
        if (amount <= 0) return;
        location.getWorld().spawn(location, org.bukkit.entity.ExperienceOrb.class,
            orb -> orb.setExperience(amount));
    }
}
