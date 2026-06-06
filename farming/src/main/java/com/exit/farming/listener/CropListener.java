package com.exit.farming.listener;

import com.exit.core.api.JobProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.farming.FarmingPlugin;
import com.exit.farming.crop.Crop;
import com.exit.farming.farmland.FarmlandClaimManager;
import com.exit.farming.farmland.FarmlandPolicy;
import com.exit.farming.farmland.WorldPolicyManager;
import com.exit.farming.item.CropItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import com.exit.farming.storage.CropStorageManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 작물 라이프사이클 이벤트.
 *
 * - PlayerInteractEvent (RIGHT_CLICK, 경작지 + 우리 씨앗) → 씨앗 소비 + Crop.applySeedling()
 * - BlockGrowEvent → seedling 매칭 시 cancel + applyMature()
 * - BlockBreakEvent → 드랍 재정의 (행운 반영)
 * - PlayerInteractEvent (RIGHT_CLICK, repeatable mature) → 열매 드랍 + seedling 리셋
 *
 * 월드 정책 연동:
 *   - FORBIDDEN 월드: 씨앗 심기/우클릭 수확 모두 차단
 *   - MANAGED 월드: 클레임된 경작지 위에서만 씨앗 심기 허용
 *   - FREE 월드: 제약 없음
 */
public class CropListener implements Listener {

    private final FarmingPlugin plugin;
    private final WorldPolicyManager policyManager;
    private final FarmlandClaimManager claimManager;
    private final com.exit.farming.water.CropTracker tracker;

    public CropListener(FarmingPlugin plugin,
                        WorldPolicyManager policyManager,
                        FarmlandClaimManager claimManager,
                        com.exit.farming.water.CropTracker tracker) {
        this.plugin = plugin;
        this.policyManager = policyManager;
        this.claimManager = claimManager;
        this.tracker = tracker;
    }

    // ===== 1) 씨앗 배치 or 우클릭 수확 =====
    // HIGHEST + ignoreCancelled=true: Land 등 다른 플러그인이 cancel 한 뒤에 우리가 처리.
    // 이렇게 안 하면 Land 가 cancel 해도 우리가 먼저 manual drop 해서 아이템 복사 발생.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();

        FarmlandPolicy policy = policyManager.policyOf(block.getWorld());

        // (A) 손에 든 것이 우리 씨앗 → 씨앗 배치
        ItemStack inHand = player.getInventory().getItemInMainHand();
        Crop ourSeed = CropItem.identifySeed(inHand);
        if (ourSeed != null) {
            // 월드 정책 체크
            if (policy == FarmlandPolicy.FORBIDDEN) {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "이 월드에선 농사가 금지되어 있습니다.", NamedTextColor.RED));
                return;
            }

            // 경작지(farmland) 위클릭 시에만. 위 블록이 비어있어야 설치 가능.
            // ⚠ AIR / CAVE_AIR / VOID_AIR 모두 허용해야 함 — `.isAir()` 가 셋 다 매칭.
            //   `== Material.AIR` 만 체크하면 동굴 천장 아래 / 자연 생성 청크 등의 farmland 위가
            //   CAVE_AIR 일 때 silently return → vanilla 도 막힘 → 씨앗만 돌아오는 버그.
            if (block.getType() != Material.FARMLAND) return;
            Block aboveCheck = block.getRelative(0, 1, 0);
            if (!aboveCheck.getType().isAir()) return;

            // MANAGED 월드에선 클레임된 경작지 위에서만
            if (policy == FarmlandPolicy.MANAGED && !claimManager.isClaimed(block)) {
                event.setCancelled(true);
                player.sendMessage(Component.text(
                        "클레임되지 않은 경작지엔 심을 수 없습니다. 경작지 티켓으로 등록하세요.",
                        NamedTextColor.YELLOW));
                return;
            }

            event.setCancelled(true);
            Block target = block.getRelative(0, 1, 0);
            ourSeed.applySeedling(target);
            tracker.put(target, ourSeed);  // 위치 추적 — 성장/본밀 시 이 매핑으로 올바른 mature 적용

            // 씨앗 1개 차감 (크리에이티브는 무소비)
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                inHand.setAmount(inHand.getAmount() - 1);
            }
            player.playSound(player.getLocation(), Sound.ITEM_CROP_PLANT, 1.0f, 1.0f);
            return;
        }

        // (B) 우클릭 수확 - 블록이 repeatable mature 여야 함
        Crop mature = Crop.matchingMature(block);
        if (mature == null || !mature.repeatable()) return;

        // FORBIDDEN 월드에선 우클릭 수확도 차단
        if (policy == FarmlandPolicy.FORBIDDEN) {
            event.setCancelled(true);
            return;
        }

        // 청크 권한 체크는 Land 플러그인에 위임 (priority HIGHEST + ignoreCancelled=true 로 Land 가 먼저 cancel)

        event.setCancelled(true);
        int fortune = playerFortune(player);

        // 단일 칸 수확 + 농부 EXP
        // (이전 phase 에 있던 괭이 정면 추가 수확은 제거됨 — Lv6/Lv10 perk 가
        //  '보관함 자동수집 범위 업그레이드' 로 의미 변경됨. CropStorageListener 의
        //  periodic range collect task 가 새 효과를 담당.)
        harvestOne(block, mature, fortune);
        grantFarmerExp(player);
        player.playSound(block.getLocation(), Sound.BLOCK_CROP_BREAK, 1.0f, 1.2f);
    }

    // ===== 2) 성장 이벤트 가로채기 =====
    // 두 가지 차단:
    //   (1) 우리 mature 블록의 자가 자람 (예: 옥수수 mature=POTATOES[6] → vanilla 가 [7]=크랜베리mature 로 자람)
    //   (2) 우리 seedling 블록 → mature 로 즉시 전환
    //
    // 핵심: BlockGrowEvent 시점에 block.getBlockData() 가 Paper 1.21+ 에서 이미 +1 갱신된 상태일 수 있음.
    //       그래서 매칭은 event.getNewState() 의 age - 1 (= 진짜 현재 age) 로 해야 정확함.
    @EventHandler
    public void onGrow(BlockGrowEvent event) {
        Block block = event.getBlock();

        // ── 0순위: dry farmland 위면 자람 차단 (물 안 뿌리면 자라지 않음) ──
        // vanilla 는 moisture=0 farmland 위에서도 (느리게) BlockGrowEvent 를 fire 한다.
        // 우리는 그 이벤트를 받아 즉시 mature 로 전환하므로, dry 상태에서도 결국 자라버린다 → 버그.
        // 우리 작물(트래커 등록 또는 우리 매핑 material) 이면 dry 시 event cancel + return.
        if (isFarmlandDryBelow(block) && isOurCrop(block)) {
            event.setCancelled(true);
            return;
        }

        // ── 1순위: 트래커 조회 (가장 정확) ──
        Crop tracked = tracker.get(block);
        if (tracked != null) {
            event.setCancelled(true);  // vanilla 자람 차단
            if (tracked.matchesMature(block)) return;  // 이미 mature 면 유지
            // seedling → mature
            final Crop target = tracked;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (block.getType() == target.seedlingMaterial()
                        || block.getType() == target.matureMaterial()) {
                    target.applyMature(block);
                }
            });
            return;
        }

        // ── 2순위: 트래커에 없는 야생 작물 fallback (age 기반) ──
        org.bukkit.block.data.BlockData newData = event.getNewState().getBlockData();
        if (!(newData instanceof org.bukkit.block.data.Ageable newAge)) return;
        int oldAge = newAge.getAge() - 1;
        if (oldAge < 0) return;
        Material mat = block.getType();

        // 우리 mature → 자가 자람 차단
        for (Crop c : Crop.values()) {
            if (c.matureMaterial() == mat && c.matureAge() == oldAge) {
                event.setCancelled(true);
                return;
            }
        }
        // 우리 seedling → mature 전환
        Crop seedling = null;
        for (Crop c : Crop.values()) {
            if (c.seedlingMaterial() == mat && c.seedlingAge() == oldAge) {
                seedling = c;
                break;
            }
        }
        if (seedling == null) return;
        event.setCancelled(true);
        final Crop target = seedling;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() == target.seedlingMaterial()) {
                target.applyMature(block);
            }
        });
    }

    // ===== 2-B) 본밀 처리 =====
    // - 우리 seedling 에 본밀 → 본밀 1개 소비 + 즉시 mature
    // - 우리 mature 에 본밀 → cancel (본밀 안 닳음, 잘못 자람 방지)
    //
    // 트래커 우선 매칭 — block.getBlockData() 의 age 가 본밀로 이미 갱신됐을 수 있어 age 매칭은 위험.
    @EventHandler
    public void onFertilize(org.bukkit.event.block.BlockFertilizeEvent event) {
        Block block = event.getBlock();
        Crop tracked = tracker.get(block);
        if (tracked == null) return;  // 야생 / 트래커 외 → 본밀 동작 vanilla 그대로

        // mature 상태면 본밀 효과 차단 (자가 자람 방지)
        if (tracked.matchesMature(block)) {
            event.setCancelled(true);
            return;
        }

        // seedling 상태 → 본밀 수동 소비 + 즉시 mature
        event.setCancelled(true);
        final Crop target = tracked;
        Player player = event.getPlayer();
        if (player != null && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            consumeBoneMeal(player);
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() == target.seedlingMaterial()
                    || block.getType() == target.matureMaterial()) {
                target.applyMature(block);
            }
        });
    }

    /** 플레이어 손(주/보조)의 본밀 1개 차감. 둘 다 없으면 noop. */
    private static void consumeBoneMeal(Player p) {
        var inv = p.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (main != null && main.getType() == Material.BONE_MEAL && main.getAmount() > 0) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() == Material.BONE_MEAL && off.getAmount() > 0) {
            off.setAmount(off.getAmount() - 1);
        }
    }

    // ===== 3) 블록 파괴 =====
    // HIGHEST + ignoreCancelled=true: Land 등이 NORMAL/HIGH 에서 cancel 하면 우리는 자동 skip →
    // manual drop 안 일어남 → 아이템 복사 버그 자연 해소. 권한 체크는 Land 에게 위임.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Crop.isCropMaterial(block.getType())) return;

        Player player = event.getPlayer();
        int fortune = playerFortune(player);

        // 트래커 우선 — 위치 기반 정확 매칭
        Crop tracked = tracker.get(block);
        if (tracked != null) {
            event.setDropItems(false);
            if (tracked.matchesMature(block)) {
                dropFruit(block, tracked, fortune, true);
                dropSeed(block, tracked, fortune);
                grantFarmerExp(player);
                tryAutoReplant(player, block, tracked);  // 농부 Lv10 인간 파종기
            } else {
                dropSeed(block, tracked, fortune);
            }
            tracker.remove(block);  // 위치 추적 정리
            return;
        }

        // Mature 매칭 우선 (열매 + 씨앗 드랍 + 농부 EXP) — 트래커에 없는 야생 작물 fallback
        Crop matureCrop = Crop.matchingMature(block);
        if (matureCrop != null) {
            event.setDropItems(false);
            dropFruit(block, matureCrop, fortune, true);
            dropSeed(block, matureCrop, fortune);
            grantFarmerExp(player);
            tryAutoReplant(player, block, matureCrop);  // 농부 Lv10 인간 파종기
            return;
        }

        // Seedling 매칭 (씨앗만 드랍)
        Crop seedlingCrop = Crop.matchingSeedling(block);
        if (seedlingCrop != null) {
            event.setDropItems(false);
            dropSeed(block, seedlingCrop, fortune);
            return;
        }

        // 우리 매핑에 없는 age — 바닐라 드랍 그대로 두기.
        // (예전에 vanilla-suppress 옵션으로 일괄 차단했으나, 이건 일반 블록 드랍까지
        //  영향 줄 수 있어서 제거. 바닐라 작물 봉인은 VanillaSuppressListener 의
        //  BlockPlace 차단으로 충분히 처리됨.)
    }

    // ===== 유틸 =====

    /** 아래 블록이 farmland 이고 moisture 가 0(완전 dry) 이면 true. */
    private static boolean isFarmlandDryBelow(Block crop) {
        Block below = crop.getRelative(0, -1, 0);
        if (below.getType() != org.bukkit.Material.FARMLAND) return false;
        org.bukkit.block.data.BlockData data = below.getBlockData();
        if (!(data instanceof org.bukkit.block.data.type.Farmland farm)) return false;
        return farm.getMoisture() == 0;
    }

    /** 우리 시스템(트래커 또는 우리 Crop 매핑 material) 작물이면 true. */
    private boolean isOurCrop(Block block) {
        if (tracker.get(block) != null) return true;
        Material mat = block.getType();
        for (Crop c : Crop.values()) {
            if (c.seedlingMaterial() == mat || c.matureMaterial() == mat) return true;
        }
        return false;
    }

    private int playerFortune(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return 0;
        return tool.getEnchantmentLevel(Enchantment.FORTUNE);
    }

    private void dropFruit(Block block, Crop crop, int fortune, boolean allowPremium) {
        int base = Math.max(1, plugin.getConfig().getInt("drop.fruit-base", 3));
        int count = 1 + ThreadLocalRandom.current().nextInt(base + fortune);
        boolean premium = false;
        if (allowPremium) {
            int oneIn = Math.max(1, plugin.getConfig().getInt("premium.chance-one-in", 1000));
            premium = ThreadLocalRandom.current().nextInt(oneIn) == 0;
        }
        var loc = block.getLocation().add(0.5, 0.3, 0.5);
        block.getWorld().dropItemNaturally(loc, CropItem.createFruit(crop, count, premium));
    }

    private void dropSeed(Block block, Crop crop, int fortune) {
        int base = Math.max(1, plugin.getConfig().getInt("drop.seeds-base", 4));
        // fortune 0 기준 평균 ≈ 0.25개 (1/2/3/4 롤 → 0/0/0/1 floor). 인플레 방지용 25% 축소.
        int count = (1 + ThreadLocalRandom.current().nextInt(base + fortune)) / 4;
        if (count <= 0) return;
        var loc = block.getLocation().add(0.5, 0.3, 0.5);
        block.getWorld().dropItemNaturally(loc, CropItem.createSeed(crop, count));
    }

    /** 한 칸 수확 (열매 드랍 + seedling 리셋). 광역 수확 공통 처리용. */
    private void harvestOne(Block block, Crop mature, int fortune) {
        dropFruit(block, mature, fortune, true);
        mature.applySeedling(block);
        tracker.put(block, mature);  // seedling 리셋 후에도 다음 사이클 추적 유지
    }

    /** 작물 1칸 수확당 농부 EXP 부여. Job 미설치 시 no-op. */
    private void grantFarmerExp(Player player) {
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return;
        int exp = Math.max(0, plugin.getConfig().getInt("job-exp-per-harvest", 8));
        if (exp <= 0) return;
        jobs.addExp(player.getUniqueId(), "farmer", exp);
    }

    // ===== 농부 Lv10 인간 파종기 =====
    // 괭이로 mature 작물을 수확하면 같은 자리에 동일 씨앗 자동 재심기.
    // 씨앗 차감 우선순위: 보관함 → 인벤토리. 둘 다 없으면 skip.
    // 크리에이티브는 차감 면제. 미래 괭이 범위 인챈트와는 BlockBreakEvent 단위로
    // 자동 호환 (각 타일이 개별 event 를 fire 하는 방식 전제).
    private void tryAutoReplant(Player player, Block block, Crop crop) {
        JobProvider jobs = ServiceRegistry.get(JobProvider.class).orElse(null);
        if (jobs == null) return;
        if (jobs.getLevel(player.getUniqueId(), "farmer") < 10) return;

        // 플레이어가 보관함 GUI 에서 자동 재심기 OFF 한 상태면 skip
        com.exit.farming.storage.CropStorageManager storage = plugin.getCropStorageManager();
        if (storage != null && !storage.isAutoReplant(player.getUniqueId())) return;

        // 괭이 한정
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !Tag.ITEMS_HOES.isTagged(tool.getType())) return;

        boolean creative = player.getGameMode() == org.bukkit.GameMode.CREATIVE;

        // 씨앗 차감 (크리에이티브 제외) — 보관함 먼저, 없으면 인벤토리
        if (!creative && !consumeSeed(player, crop)) return;

        // 10 tick (0.5초) 후 재심기 — 괭이 수확 모션과 겹치지 않도록 약간의 텀.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getRelative(0, -1, 0).getType() != Material.FARMLAND) return;
            if (!block.getType().isAir()) return;  // 누가 채워놨으면 skip
            crop.applySeedling(block);
            tracker.put(block, crop);
        }, 10L);
    }

    /** 보관함 → 인벤토리 순으로 동일 작물 씨앗 1개 차감. 성공 시 true. */
    private boolean consumeSeed(Player player, Crop crop) {
        CropStorageManager storage = plugin.getCropStorageManager();
        if (storage != null) {
            ItemStack[] slots = storage.load(player.getUniqueId());
            for (int i = 0; i < slots.length; i++) {
                ItemStack s = slots[i];
                if (s == null || s.getType().isAir()) continue;
                if (CropItem.identifySeed(s) != crop) continue;
                int amt = s.getAmount();
                if (amt <= 1) slots[i] = null;
                else s.setAmount(amt - 1);
                storage.save(player.getUniqueId(), slots);
                return true;
            }
        }

        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.getType().isAir()) continue;
            if (CropItem.identifySeed(s) != crop) continue;
            int amt = s.getAmount();
            if (amt <= 1) inv.setItem(i, null);
            else s.setAmount(amt - 1);
            return true;
        }
        return false;
    }
}
