package com.exit.job.listener;

import com.exit.job.gui.JobDetailGUI;
import com.exit.job.gui.JobOverviewGUI;
import com.exit.job.manager.JobConfigManager;
import com.exit.job.manager.JobManager;
import com.exit.job.model.JobType;
import com.exit.job.perk.PerkApplyManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 이벤트 라우팅:
 *  - 입장: 데이터 로드 + 능력 적용
 *  - 퇴장: flush + cache 제거 + 능력 제거 (Attribute 누수 방지)
 *  - GUI 클릭: action tag 기반 분기
 *  - 광석 채광: 허용 월드 + 비-실크터치 일 때 EXP 부여,
 *    그리고 ore_drop_x2_chance / ore_to_block_chance / 주괴 인챈트 결과를 반영해
 *    드롭을 직접 가공 (LOWEST priority)
 *  - 용암/익사: lava_immunity / water_breathing 보강용 데미지 캔슬
 */
public class JobListener implements Listener {

    /** custom-items 의 인챈트 PDC 키. */
    private static final NamespacedKey LAMP_ENCHANTS_KEY =
            NamespacedKey.fromString("customitems:lamp_enchants");
    private static final String SMELT_ENCHANT_PREFIX = "customitems:life_smelted_ingot";

    /**
     * 주괴 변환 매핑. custom-items 의 SMELTABLE 과 동일하게 유지.
     * Job 이 LOWEST 에서 본체 블록 드롭을 가공할 때 동일 결과를 산출하기 위해 복제.
     * 부수 채굴 (주변/폭발) 의 주괴 변환은 custom-items 측 breakOneBlock 이 그대로 처리.
     */
    private static final EnumMap<Material, Material> SMELTABLE = new EnumMap<>(Material.class);
    static {
        SMELTABLE.put(Material.IRON_ORE,             Material.IRON_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_IRON_ORE,   Material.IRON_INGOT);
        SMELTABLE.put(Material.GOLD_ORE,             Material.GOLD_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_GOLD_ORE,   Material.GOLD_INGOT);
        SMELTABLE.put(Material.COPPER_ORE,           Material.COPPER_INGOT);
        SMELTABLE.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELTABLE.put(Material.ANCIENT_DEBRIS,       Material.NETHERITE_SCRAP);
    }

    private final JobManager jobManager;
    private final JobConfigManager configManager;
    private final JobOverviewGUI overviewGUI;
    private final JobDetailGUI detailGUI;
    private final PerkApplyManager perkApplyManager;

    public JobListener(JobManager jobManager, JobConfigManager configManager,
                       JobOverviewGUI overviewGUI, JobDetailGUI detailGUI,
                       PerkApplyManager perkApplyManager) {
        this.jobManager = jobManager;
        this.configManager = configManager;
        this.overviewGUI = overviewGUI;
        this.detailGUI = detailGUI;
        this.perkApplyManager = perkApplyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        jobManager.loadOrInit(event.getPlayer().getUniqueId());
        perkApplyManager.applyAll(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        perkApplyManager.removeAll(event.getPlayer());
        jobManager.unload(event.getPlayer().getUniqueId());
    }

    /**
     * 광석 채광 처리 (LOWEST). custom-items 의 HIGH 보다 먼저 동작해서
     * setDropItems(false) + 직접 드롭 → custom-items 는 isDropItems() 가드로
     * 본체 변환을 스킵 (부수 채굴/삽 인챈트는 그대로 동작).
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (!configManager.miningAllowedWorlds().contains(event.getBlock().getWorld().getName())) return;

        Block block = event.getBlock();
        Material oreType = block.getType();
        Integer expConfigured = configManager.miningOreExp().get(oreType);
        if (expConfigured == null || expConfigured <= 0) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean silkTouch = tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH);

        // EXP 부여 (silk touch 만 제외, 손 채광 포함). exp-multiplier 적용.
        if (!silkTouch) {
            int exp = configManager.applyMiningExpMultiplier(expConfigured);
            jobManager.addExp(player.getUniqueId(), JobType.MINER, exp);
        }

        int minerLevel = jobManager.getLevel(player.getUniqueId(), JobType.MINER);
        boolean smelt = !silkTouch && hasSmeltEnchant(tool) && SMELTABLE.containsKey(oreType);
        boolean blockChance = minerLevel >= 8
                && configManager.miningOreToBlock().containsKey(oreType)
                && ThreadLocalRandom.current().nextDouble() < configManager.miningOreToBlockChance();
        boolean x2Chance = minerLevel >= 4
                && ThreadLocalRandom.current().nextDouble() < configManager.miningOreDropX2Chance();

        // 아무 가공도 필요 없으면 vanilla / custom-items 에 위임
        if (!smelt && !blockChance && !x2Chance) return;

        // 평가 순서: vanilla → smelt → block → x2
        List<ItemStack> finalDrops;
        if (blockChance) {
            // ore_to_block 매핑이 있는 광석에만 발동 (ANCIENT_DEBRIS 매핑 없음 → 자동 제외)
            finalDrops = new ArrayList<>();
            finalDrops.add(new ItemStack(configManager.miningOreToBlock().get(oreType)));
        } else if (smelt) {
            finalDrops = new ArrayList<>();
            finalDrops.add(new ItemStack(SMELTABLE.get(oreType)));
        } else {
            // vanilla 드롭 (silk touch / fortune 반영). tool 이 null 이면 손 → 빈 컬렉션 반환.
            Collection<ItemStack> vanilla = (tool == null)
                    ? block.getDrops()
                    : block.getDrops(tool, player);
            finalDrops = new ArrayList<>(vanilla);
        }

        if (x2Chance && !finalDrops.isEmpty()) {
            int orig = finalDrops.size();
            for (int i = 0; i < orig; i++) {
                finalDrops.add(finalDrops.get(i).clone());
            }
        }

        event.setDropItems(false);
        for (ItemStack stack : finalDrops) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;
            block.getWorld().dropItemNaturally(block.getLocation(), stack);
        }
    }

    /** 곡괭이 PDC 의 customitems:lamp_enchants 에 주괴 드랍 인챈트가 있는지. */
    private boolean hasSmeltEnchant(ItemStack tool) {
        if (tool == null || !tool.hasItemMeta() || LAMP_ENCHANTS_KEY == null) return false;
        PersistentDataContainer pdc = tool.getItemMeta().getPersistentDataContainer();
        String raw = pdc.get(LAMP_ENCHANTS_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return false;
        for (String entry : raw.split(";")) {
            // 항목 형식: "customitems:life_smelted_ingot|" (값 없음) 또는 "...|v1,v2"
            int sep = entry.indexOf('|');
            String enchantKey = sep < 0 ? entry : entry.substring(0, sep);
            if (SMELT_ENCHANT_PREFIX.equals(enchantKey)) return true;
        }
        return false;
    }

    /**
     * lava_immunity / water_breathing 보강. PotionEffect 가 우선 막아주지만
     * 환경에 따라 한 틱 새는 경우가 있어 데미지 자체를 캔슬.
     */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        switch (event.getCause()) {
            case LAVA, FIRE, FIRE_TICK, HOT_FLOOR -> {
                if (jobManager.getLevel(uuid, JobType.MINER) >= 10) event.setCancelled(true);
            }
            case DROWNING -> {
                if (jobManager.getLevel(uuid, JobType.FISHER) >= 10) event.setCancelled(true);
            }
            default -> {}
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID viewer = player.getUniqueId();
        boolean inOverview = overviewGUI.isViewing(viewer);
        boolean inDetail = detailGUI.getContext(viewer) != null;

        // 우리 Job GUI 가 열려 있지 않으면 완전히 무시.
        // (제작대/모루/플레이어 인벤 제작칸 등 vanilla 인벤토리 동작 보호)
        if (!inOverview && !inDetail) return;

        // 여기서부터는 Job GUI 컨텍스트. 모든 클릭 cancel + action 디스패치.
        event.setCancelled(true);

        Optional<String> actionOpt = readAction(event.getCurrentItem());
        if (actionOpt.isEmpty()) return;

        String action = actionOpt.get();
        if (action.startsWith("OPEN_DETAIL|")) {
            String jobId = action.substring("OPEN_DETAIL|".length());
            JobType type = JobType.fromId(jobId);
            if (type != null) {
                UUID target = overviewGUI.getTarget(viewer);
                detailGUI.open(player, target, type);
            }
            return;
        }
        if ("BACK".equals(action)) {
            JobDetailGUI.Context ctx = detailGUI.getContext(viewer);
            if (ctx != null) {
                overviewGUI.open(player, ctx.target());
            }
            return;
        }
        if ("CLOSE".equals(action)) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        overviewGUI.close(uuid);
        detailGUI.close(uuid);
    }

    private Optional<String> readAction(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        var pdc = stack.getItemMeta().getPersistentDataContainer();
        if (pdc.has(JobOverviewGUI.ACTION_KEY, PersistentDataType.STRING)) {
            return Optional.ofNullable(pdc.get(JobOverviewGUI.ACTION_KEY, PersistentDataType.STRING));
        }
        return Optional.empty();
    }
}
