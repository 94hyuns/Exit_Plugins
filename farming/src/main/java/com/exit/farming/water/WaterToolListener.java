package com.exit.farming.water;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.farming.FarmingPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * 물 도구 종합 리스너.
 *
 * <ol>
 *   <li><b>MoistureChangeEvent (전 월드)</b>: 바닐라 자동 적시기(물 인접 / 비) 차단.
 *       단, 수분 감소 방향(말리기)은 통과 — 그래야 시간 지나면 다시 적셔줘야 한다.</li>
 *   <li><b>PlayerInteractEvent</b>: 물뿌리개 우클릭 → tier 범위만큼 farmland 적심.</li>
 *   <li><b>BlockPlaceEvent</b>: 우리 스프링쿨러 BARREL 배치 → SprinklerStore 등록.</li>
 *   <li><b>BlockBreakEvent</b>: 우리 스프링쿨러 BARREL 파괴 → 등록 해제 + 등급에 맞는 아이템 드롭(원본 인벤토리 보존).</li>
 *   <li><b>PlayerInteractEvent (BARREL 우클릭)</b>: 우리 스프링쿨러는 인벤토리 열기 금지.</li>
 * </ol>
 */
public class WaterToolListener implements Listener {

    private final FarmingPlugin plugin;
    private final SprinklerStore sprinklerStore;
    private final CropTracker cropTracker;

    public WaterToolListener(FarmingPlugin plugin, SprinklerStore sprinklerStore, CropTracker cropTracker) {
        this.plugin = plugin;
        this.sprinklerStore = sprinklerStore;
        this.cropTracker = cropTracker;
    }

    // ═══ 0. 물뿌리개로 물 채우기 차단 (BUCKET → WATER_BUCKET 변환되면 PDC 소실) ═══
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (WateringCanItem.getTier(hand) != null) {
            event.setCancelled(true);
        }
    }

    // ═══ 1. 바닐라 수분 변화 처리 ═══
    // - 수분 증가 (wetting): 항상 차단 (물뿌리개/스프링쿨러 외 자동 wet 금지)
    // - 수분 감소 (drying): 위에 자라고 있는 작물 있으면 차단 → "한번 적시면 작물 다 자랄 때까지 유지"
    //                      작물 다 자랐거나 작물 없으면 drying 허용 → 다음 농사 시 다시 물 줘야 함
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        Block block = event.getBlock();
        BlockData newData = event.getNewState().getBlockData();
        BlockData curData = block.getBlockData();
        if (!(newData instanceof org.bukkit.block.data.type.Farmland newFarm)) return;
        if (!(curData instanceof org.bukkit.block.data.type.Farmland curFarm)) return;

        boolean wetting = newFarm.getMoisture() > curFarm.getMoisture();
        if (wetting) {
            event.setCancelled(true);
            return;
        }
        // drying — 위 작물이 자라는 중이면 차단
        if (hasGrowingCropAbove(block)) {
            event.setCancelled(true);
        }
    }

    /**
     * 위 블록이 자라는 중인 작물이면 true.
     * 1순위: tracker 에 등록된 우리 작물이면, 우리 mature 매칭 여부로 판단 (커스텀 mature age 정확 반영).
     * 2순위: vanilla 작물 fallback — 가장 큰 age 미만이면 자라는 중.
     */
    private boolean hasGrowingCropAbove(Block farmland) {
        Block above = farmland.getRelative(0, 1, 0);
        if (cropTracker != null) {
            com.exit.farming.crop.Crop tracked = cropTracker.get(above);
            if (tracked != null) {
                return !tracked.matchesMature(above);
            }
        }
        if (!isFarmlandCrop(above.getType())) return false;
        BlockData data = above.getBlockData();
        if (!(data instanceof Ageable ageable)) return false;
        return ageable.getAge() < ageable.getMaximumAge();
    }

    private static boolean isFarmlandCrop(Material m) {
        return m == Material.WHEAT
                || m == Material.CARROTS
                || m == Material.POTATOES
                || m == Material.BEETROOTS
                || m == Material.MELON_STEM
                || m == Material.PUMPKIN_STEM
                || m == Material.ATTACHED_MELON_STEM
                || m == Material.ATTACHED_PUMPKIN_STEM
                || m == Material.TORCHFLOWER_CROP
                || m == Material.PITCHER_CROP;
    }

    // ═══ 2. 물뿌리개 우클릭 ═══
    // LOWEST 우선순위 + ignoreCancelled=false: Land 보호 등 다른 플러그인이 cancel 하기 전에 우리가 먼저 처리.
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onWateringCanUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        WaterTier tier = WateringCanItem.getTier(hand);
        if (tier == null) return;

        // 등급 게이트 — Job 플러그인이 있으면 농부 레벨 검사. 없으면 통과 (soft depend).
        if (!hasFarmerLevelFor(player, tier)) {
            event.setCancelled(true);
            int needed = (tier == WaterTier.IRON) ? 4 : 8;
            player.sendMessage(Component.text(
                    tier.displayKor() + " 물뿌리개는 농부 Lv." + needed + " 부터 사용할 수 있습니다.",
                    NamedTextColor.RED));
            return;
        }

        // farmland 가 아닌 블록 우클릭은 우리가 관여하지 않음 (문, 상자, 레버 등 정상 동작 보장)
        if (clicked.getType() != Material.FARMLAND) return;

        event.setCancelled(true); // 물뿌리개로 farmland 클릭 시 다른 동작 차단

        Set<Block> targets = collectTargets(clicked, player, tier);
        int wetted = 0;
        for (Block b : targets) {
            if (wetFarmland(b)) wetted++;
        }

        if (wetted > 0) {
            player.playSound(clicked.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.6f, 1.4f);
            clicked.getWorld().spawnParticle(
                    Particle.SPLASH,
                    clicked.getLocation().add(0.5, 1.0, 0.5),
                    12, 0.6, 0.1, 0.6, 0.0
            );
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "[" + tier.displayKor() + " 물뿌리개] " + wetted + "칸 적심.",
                    net.kyori.adventure.text.format.NamedTextColor.AQUA));
        } else {
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "[물뿌리개] 적실 경작지가 없습니다 (이미 wet 이거나 farmland 아님).",
                    net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    /**
     * 물뿌리개 등급에 따라 적실 블록 집합을 모은다.
     *
     * <p>플레이어가 바라보는 방향(전후좌우 카디널)을 기준으로 직사각형을 만든다.
     * <ul>
     *   <li>구리 (1×3): 타겟 + 전방 2칸 = 직선 3칸</li>
     *   <li>철 (2×3):   타겟 + 전방 2칸 + 좌측 또는 우측 1열(같은 깊이) = 6칸. 우측으로 통일.</li>
     *   <li>다이아 (3×3): 타겟이 정중앙 앞열 가운데, 좌우 1칸 + 전방 2칸 = 9칸</li>
     * </ul>
     */
    private Set<Block> collectTargets(Block target, Player player, WaterTier tier) {
        Set<Block> out = new HashSet<>();
        BlockFace forward = cardinalFacing(player.getLocation().getYaw());
        BlockFace right = rotateCW(forward);

        switch (tier) {
            case COPPER -> {
                // 타겟 + 전방 2칸
                for (int f = 0; f < 3; f++) {
                    out.add(offset(target, forward, f, right, 0));
                }
            }
            case IRON -> {
                // 타겟 열 (3칸) + 우측 열 (3칸)
                for (int f = 0; f < 3; f++) {
                    out.add(offset(target, forward, f, right, 0));
                    out.add(offset(target, forward, f, right, 1));
                }
            }
            case DIAMOND -> {
                // 좌우 1칸씩 + 전방 2칸 (3×3)
                for (int f = 0; f < 3; f++) {
                    for (int s = -1; s <= 1; s++) {
                        out.add(offset(target, forward, f, right, s));
                    }
                }
            }
        }
        return out;
    }

    private static Block offset(Block from, BlockFace forward, int f, BlockFace right, int r) {
        int dx = forward.getModX() * f + right.getModX() * r;
        int dz = forward.getModZ() * f + right.getModZ() * r;
        return from.getWorld().getBlockAt(from.getX() + dx, from.getY(), from.getZ() + dz);
    }

    private static BlockFace cardinalFacing(float yaw) {
        // yaw: south=0, west=90, north=180, east=-90 ... normalize to [0,360)
        float y = ((yaw % 360) + 360) % 360;
        if (y >= 315 || y < 45) return BlockFace.SOUTH;
        if (y < 135) return BlockFace.WEST;
        if (y < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    private static BlockFace rotateCW(BlockFace f) {
        return switch (f) {
            case SOUTH -> BlockFace.WEST;
            case WEST  -> BlockFace.NORTH;
            case NORTH -> BlockFace.EAST;
            case EAST  -> BlockFace.SOUTH;
            default    -> BlockFace.EAST;
        };
    }

    private boolean wetFarmland(Block b) {
        if (b.getType() != Material.FARMLAND) return false;
        BlockData rawData = b.getBlockData();
        // Paper 1.21+: FARMLAND 는 Farmland 인터페이스 (getMoisture/setMoisture). Ageable 아님!
        if (!(rawData instanceof org.bukkit.block.data.type.Farmland farm)) return false;
        int max = farm.getMaximumMoisture();
        if (farm.getMoisture() >= max) return false; // 이미 wet
        farm.setMoisture(max);
        b.setBlockData(farm, false);
        return true;
    }

    // ═══ 3. 스프링클러 배치 (BARREL → BARRIER 변환) ═══
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprinklerPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        WaterTier tier = SprinklerItem.getTier(inHand);
        if (tier == null) return;

        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.BARREL) return;

        // BARREL → BARRIER. 서바이벌 플레이어에겐 안 보이고, ItemDisplay 의 sprinkler 모델만 표시됨.
        // BARRIER 는 서바이벌에서 파괴 불가하므로, 회수는 Interaction 엔티티 좌클릭으로 처리.
        placed.setType(Material.BARRIER, false);

        sprinklerStore.register(placed.getLocation(), tier);
        SprinklerDisplay.ensure(placed, tier);

        Player player = event.getPlayer();
        player.sendMessage(Component.text(
                tier.displayKor() + " 스프링클러를 설치했습니다. (범위: "
                        + sizeLabel(tier) + ")",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "회수: 좌클릭(공격)",
                NamedTextColor.GRAY));
        player.playSound(placed.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
    }

    private static String sizeLabel(WaterTier t) {
        return switch (t) { case COPPER -> "2×2"; case IRON -> "3×3"; case DIAMOND -> "5×5"; };
    }

    // ═══ 4. 스프링클러 회수 — Interaction 엔티티 좌클릭(공격) ═══
    // ignoreCancelled=false: Land 등이 데미지 cancel 해도 우리는 처리. Interaction 은 어차피 무적.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSprinklerAttack(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Interaction interaction)) return;
        if (!interaction.getPersistentDataContainer().has(
                WaterToolKeys.SPRINKLER_INTERACTION,
                org.bukkit.persistence.PersistentDataType.STRING)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        plugin.getLogger().info("[스프링클러] " + player.getName() + " 좌클릭 공격 감지");
        event.setCancelled(true);

        Block block = interaction.getLocation().getBlock();
        SprinklerStore.Sprinkler s = sprinklerStore.get(block.getLocation());
        if (s == null) {
            // store 에 없는 orphan interaction — entity 만 정리
            SprinklerDisplay.remove(block);
            return;
        }

        sprinklerStore.unregister(block.getLocation());
        SprinklerDisplay.remove(block);
        if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);

        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                SprinklerItem.create(s.tier())
        );

        player.sendMessage(Component.text(
                s.tier().displayKor() + " 스프링클러를 회수했습니다.",
                NamedTextColor.GRAY));
        player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.0f);
    }

    // ═══ 4-B. 크리에이티브 — BARRIER 직접 파괴 시에도 회수 ═══
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSprinklerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;

        SprinklerStore.Sprinkler s = sprinklerStore.get(block.getLocation());
        if (s == null) return;

        sprinklerStore.unregister(block.getLocation());
        SprinklerDisplay.remove(block);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(
                block.getLocation().add(0.5, 0.5, 0.5),
                SprinklerItem.create(s.tier())
        );

        Player player = event.getPlayer();
        player.sendMessage(Component.text(
                s.tier().displayKor() + " 스프링클러를 회수했습니다.",
                NamedTextColor.GRAY));
    }

    // ═══ 5. Interaction 엔티티 우클릭 — 정보 메시지 / Shift+우클릭 = 회수 ═══
    // 좌클릭(EntityDamage) 회수가 Land 보호/BARRIER 충돌로 일반 플레이어에게 막힐 수 있어
    // Shift+우클릭(PlayerInteractEntity) 으로 모든 플레이어가 확실히 회수 가능한 path 제공.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSprinklerRightClick(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Interaction interaction)) return;
        if (!interaction.getPersistentDataContainer().has(
                WaterToolKeys.SPRINKLER_INTERACTION,
                org.bukkit.persistence.PersistentDataType.STRING)) return;

        Block block = interaction.getLocation().getBlock();
        SprinklerStore.Sprinkler s = sprinklerStore.get(block.getLocation());
        if (s == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        if (player.isSneaking()) {
            // ── Shift+우클릭 = 회수 ──
            sprinklerStore.unregister(block.getLocation());
            SprinklerDisplay.remove(block);
            if (block.getType() == Material.BARRIER) block.setType(Material.AIR, false);

            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    SprinklerItem.create(s.tier())
            );
            player.sendMessage(Component.text(
                    s.tier().displayKor() + " 스프링클러를 회수했습니다.",
                    NamedTextColor.GRAY));
            player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.7f, 1.0f);
        } else {
            // ── 일반 우클릭 = 정보 ──
            player.sendMessage(Component.text(
                    "[" + s.tier().displayKor() + " 스프링클러] 작동 중. Shift+우클릭으로 회수.",
                    NamedTextColor.AQUA));
        }
    }

    /**
     * 농부 레벨이 해당 등급 물뿌리개를 사용하기에 충분한가.
     * Job 플러그인 미설치/Provider 미등록 시 true (soft depend — 게이트 미적용).
     * COPPER 는 항상 OK. IRON / DIAMOND ≥ Lv4 (2026-05-17 변경: Lv4 가 "농사의 달인" 으로
     * 두 등급 모두 허용. Lv8 은 보관함 페이지 확장 perk 로 변경).
     */
    private static boolean hasFarmerLevelFor(Player player, WaterTier tier) {
        if (tier == WaterTier.COPPER) return true;
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return true;
        int level = jobs.getLevel(player.getUniqueId(), "farmer");
        return switch (tier) {
            case IRON, DIAMOND -> level >= 4;
            default -> true;
        };
    }
}
