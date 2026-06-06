package com.exit.farming.listener;

import com.exit.farming.FarmingPlugin;
import com.exit.farming.farmland.FarmlandClaimManager;
import com.exit.farming.farmland.FarmlandPolicy;
import com.exit.farming.farmland.WorldPolicyManager;
import com.exit.farming.provider.FarmlandTicketProviderImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * 경작지 관련 보호/상호작용 리스너.
 *
 * <p>v1.2.0 변경: 종이 티켓 우클릭 흐름 폐기 → 경작지 블록 아이템 배치 흐름.
 *
 * <h3>보호 동작</h3>
 * <ol>
 *   <li><b>트램플 방지</b>: FREE 가 아닌 월드에서 farmland → dirt 전환 차단 (점프/몹 밟음)</li>
 *   <li><b>파괴 방지</b>: MANAGED + claimed 인 farmland 의 BlockBreak 차단
 *       (관리자 크리에이티브는 클레임 해제 후 파괴 허용)</li>
 *   <li><b>페이드 방지</b>: 클레임된 farmland 의 BlockFadeEvent 차단 (수분 0 에서 dirt 회귀 방지)</li>
 * </ol>
 *
 * <h3>경작지 생성 — 흐름</h3>
 * <ol>
 *   <li>플레이어가 작물 상인에서 "경작지 블록" 구매 → DIRT + CMD + PDC 아이템 발급</li>
 *   <li>플레이어가 들고 우클릭으로 배치 → BlockPlaceEvent</li>
 *   <li>본 리스너가 우리 경작지 블록인지 PDC 식별:
 *     <ul>
 *       <li>MANAGED 월드: 1틱 후 DIRT → FARMLAND 전환 + 클레임 등록 + age=0</li>
 *       <li>FREE 월드: 그냥 DIRT 로 배치되게 두기 (정책상 농사 자유 → 일반 흙으로 동작)</li>
 *       <li>FORBIDDEN 월드: 배치 차단</li>
 *     </ul>
 *   </li>
 *   <li>일반 DIRT 아이템 (PDC 없음) 배치는 정책 무관 정상 처리 (그냥 흙)</li>
 * </ol>
 *
 * <h3>괭이질 차단</h3>
 * MANAGED/FORBIDDEN 월드에서 흙→farmland 직접 전환 차단. 경작지는 오직 구매한 블록으로만.
 */
public class FarmlandProtectionListener implements Listener {

    private final FarmingPlugin plugin;
    private final WorldPolicyManager policyManager;
    private final FarmlandClaimManager claimManager;
    private final FarmlandTicketProviderImpl ticketProvider;

    public FarmlandProtectionListener(FarmingPlugin plugin,
                                      WorldPolicyManager policyManager,
                                      FarmlandClaimManager claimManager,
                                      FarmlandTicketProviderImpl ticketProvider) {
        this.plugin = plugin;
        this.policyManager = policyManager;
        this.claimManager = claimManager;
        this.ticketProvider = ticketProvider;
    }

    // ═══ 1. 트램플 방지 ═══
    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND) return;

        FarmlandPolicy policy = policyManager.policyOf(block.getWorld());
        if (policy == FarmlandPolicy.FREE) return;

        if (event.getTo() == Material.DIRT) {
            event.setCancelled(true);
        }
    }

    // ═══ 2-A. BlockBreak 방지 ═══
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND) return;

        FarmlandPolicy policy = policyManager.policyOf(block.getWorld());
        if (policy != FarmlandPolicy.MANAGED) return;

        if (!claimManager.isClaimed(block)) return;

        // 소유자 본인은 파괴 가능 → 자동 unclaim + 경작지 블록 티켓으로 회수
        java.util.UUID owner = claimManager.getOwner(block);
        if (owner != null && owner.equals(event.getPlayer().getUniqueId())) {
            claimManager.unclaim(block);
            // vanilla dirt 드롭 차단 + farmland_block 티켓 1개 드롭
            event.setDropItems(false);
            ItemStack ticket = ticketProvider.createTicket(1);
            block.getWorld().dropItemNaturally(
                    block.getLocation().add(0.5, 0.5, 0.5),
                    ticket);
            event.getPlayer().sendMessage(
                    Component.text("내 경작지를 회수했습니다 (경작지 블록 1개).", NamedTextColor.GRAY));
            return;
        }

        // 관리자 크리에이티브: 클레임 해제 후 파괴 허용
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE && event.getPlayer().isOp()) {
            claimManager.unclaim(block);
            event.getPlayer().sendMessage(Component.text("클레임 해제됨.", NamedTextColor.YELLOW));
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(
                Component.text("이 경작지는 다른 플레이어의 소유라 파괴할 수 없습니다.", NamedTextColor.RED));
    }

    // ═══ 2-B. BlockFade 방지 (수분 0 → dirt 회귀) ═══
    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FARMLAND) return;

        FarmlandPolicy policy = policyManager.policyOf(block.getWorld());
        if (policy != FarmlandPolicy.MANAGED) return;

        if (claimManager.isClaimed(block)) {
            event.setCancelled(true);
        }
    }

    // ═══ 3. 경작지 블록 배치 ═══
    // BlockPlaceEvent 우선 처리. 우리 경작지 블록(PDC 태그 있는 DIRT)이면 분기.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!ticketProvider.isTicket(inHand)) {
            // 우리 경작지 블록이 아니면 패스 (일반 DIRT/잔디/괭이 등은 다른 핸들러가 처리)
            return;
        }

        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        FarmlandPolicy policy = policyManager.policyOf(placed.getWorld());

        switch (policy) {
            case FORBIDDEN -> {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "이 월드에선 경작지를 배치할 수 없습니다.", NamedTextColor.RED));
            }
            case FREE -> {
                // FREE 월드에선 그냥 일반 흙으로 배치되게 둠. (구매한 의미는 사라지지만 정책상 자유)
                player.sendMessage(Component.text(
                        "이 월드에선 농사가 자유롭습니다. 일반 흙처럼 배치되었습니다.",
                        NamedTextColor.GRAY));
            }
            case MANAGED -> {
                // 1틱 뒤에 farmland 전환 + 클레임 등록 (BlockPlace 한가운데서 setType은 충돌 우려)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (placed.getType() != Material.DIRT) return; // 그 사이 다른 변화 있었으면 skip

                    placed.setType(Material.FARMLAND, false);
                    BlockData data = placed.getBlockData();
                    // Paper 1.21+: FARMLAND 는 Farmland 인터페이스 (Ageable 아님). setMoisture 사용.
                    if (data instanceof org.bukkit.block.data.type.Farmland farm) {
                        farm.setMoisture(0);  // dry. 물뿌리개로만 적심 가능.
                        placed.setBlockData(farm, false);
                    }
                    claimManager.claim(placed, player.getUniqueId());

                    player.playSound(placed.getLocation(), Sound.ITEM_HOE_TILL, 1.0f, 1.0f);
                    player.playSound(placed.getLocation(),
                            Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.5f);
                    player.sendMessage(Component.text(
                            "경작지 등록 완료. 소유권: " + player.getName(), NamedTextColor.GREEN));
                });
            }
        }
    }

    // ═══ 4. 괭이질 차단 (MANAGED / FORBIDDEN) ═══
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHoeUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        FarmlandPolicy policy = policyManager.policyOf(block.getWorld());
        if (policy == FarmlandPolicy.FREE) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (isHoe(hand.getType()) && isTillableBlock(block.getType())) {
            event.setCancelled(true);
            if (policy == FarmlandPolicy.MANAGED) {
                player.sendMessage(Component.text(
                        "이 월드에선 작물 상인에게서 산 경작지 블록만 배치 가능합니다.",
                        NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text(
                        "이 월드에선 농사가 금지되어 있습니다.", NamedTextColor.RED));
            }
        }
    }

    // ═══ 유틸 ═══

    private static boolean isHoe(Material m) {
        return m == Material.WOODEN_HOE || m == Material.STONE_HOE
                || m == Material.IRON_HOE || m == Material.GOLDEN_HOE
                || m == Material.DIAMOND_HOE || m == Material.NETHERITE_HOE;
    }

    private static boolean isTillableBlock(Material m) {
        return m == Material.DIRT
                || m == Material.GRASS_BLOCK
                || m == Material.COARSE_DIRT
                || m == Material.ROOTED_DIRT
                || m == Material.DIRT_PATH;
    }
}
