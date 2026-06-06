package com.exit.cosmetics.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.exit.core.api.CosmeticProvider;
import com.exit.core.api.EconomyProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.core.registry.ServiceRegistry;
import com.exit.cosmetics.cosmetic.ArmorHandler;
import com.exit.cosmetics.cosmetic.TrailHandler;
import com.exit.cosmetics.cosmetic.WeaponHandler;
import com.exit.cosmetics.cosmetic.WingHandler;
import com.exit.cosmetics.gacha.ExchangeService;
import com.exit.cosmetics.gacha.GachaConfig;
import com.exit.cosmetics.gacha.GachaService;
import com.exit.cosmetics.gacha.MountGachaService;
import com.exit.cosmetics.gui.ExchangeGUI;
import com.exit.cosmetics.gui.GachaMainGUI;
import com.exit.cosmetics.gui.MountGui;
import com.exit.cosmetics.gui.WardrobeGUI;
import com.exit.cosmetics.mount.MountDefinition;
import com.exit.cosmetics.mount.MountManager;
import com.exit.cosmetics.mount.MountRegistry;
import com.exit.cosmetics.model.CosmeticDefinition;
import com.exit.cosmetics.model.CosmeticRarity;
import com.exit.cosmetics.model.CosmeticType;
import com.exit.cosmetics.npc.CosmeticNpcManager;
import com.exit.cosmetics.registry.CosmeticRegistry;
import com.exit.cosmetics.ticket.AnimationService;
import com.exit.cosmetics.ticket.TicketManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 치장 플러그인 통합 이벤트 리스너.
 */
public class CosmeticListener implements Listener {

    private final JavaPlugin plugin;
    private final PlayerDataManager dataManager;
    private final CosmeticRegistry registry;
    private final CosmeticProvider provider;
    private final CosmeticNpcManager npcManager;
    private final ArmorHandler armorHandler;
    private final WeaponHandler weaponHandler;
    private final WingHandler wingHandler;
    private final TrailHandler trailHandler;
    private final GachaService gachaService;
    private final ExchangeService exchangeService;
    private final GachaConfig gachaConfig;
    private final TicketManager ticketManager;
    private final AnimationService animationService;
    private final GachaMainGUI mainGUI;
    private final WardrobeGUI wardrobeGUI;
    private final ExchangeGUI exchangeGUI;
    private final MountGui mountGui;
    private final MountManager mountManager;
    private final MountRegistry mountRegistry;
    private final MountGachaService mountGachaService;

    public CosmeticListener(JavaPlugin plugin, PlayerDataManager dataManager, CosmeticRegistry registry,
                            CosmeticProvider provider, CosmeticNpcManager npcManager,
                            ArmorHandler armorHandler, WeaponHandler weaponHandler,
                            WingHandler wingHandler, TrailHandler trailHandler,
                            GachaService gachaService, ExchangeService exchangeService,
                            GachaConfig gachaConfig, TicketManager ticketManager,
                            AnimationService animationService,
                            GachaMainGUI mainGUI, WardrobeGUI wardrobeGUI, ExchangeGUI exchangeGUI,
                            MountGui mountGui, MountManager mountManager, MountRegistry mountRegistry,
                            MountGachaService mountGachaService) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.registry = registry;
        this.provider = provider;
        this.npcManager = npcManager;
        this.armorHandler = armorHandler;
        this.weaponHandler = weaponHandler;
        this.wingHandler = wingHandler;
        this.trailHandler = trailHandler;
        this.gachaService = gachaService;
        this.exchangeService = exchangeService;
        this.gachaConfig = gachaConfig;
        this.ticketManager = ticketManager;
        this.animationService = animationService;
        this.mainGUI = mainGUI;
        this.wardrobeGUI = wardrobeGUI;
        this.exchangeGUI = exchangeGUI;
        this.mountGui = mountGui;
        this.mountManager = mountManager;
        this.mountRegistry = mountRegistry;
        this.mountGachaService = mountGachaService;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 접속/월드이동/리스폰/종료 — 치장 재적용 / 원복
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) reapplyAll(event.getPlayer());
        }, 20L);
    }

    /**
     * 리소스팩 로드 완료 시점에 한 번 더 재적용.
     * <p>접속 직후 {@link #onJoin} 의 +20L reapplyAll 은 클라이언트가 아직 리소스팩을
     * 다운로드 중일 때 실행될 수 있다. 그 시점에 갑옷의 {@code asset_id} 를 cosmetic 으로
     * 바꿔도 클라이언트가 모델을 모르므로 기본 모델로 표시되고, 이후 팩이 로드돼도
     * 인벤토리 아이템 렌더링은 자동 갱신되지 않아 "치장이 풀린 것처럼" 보임.
     * <p>팩 로드 완료(SUCCESSFULLY_LOADED) 시점에 sync 한 번 더 → 갑옷 asset_id 가 다시
     * 클라이언트로 push 되어 올바른 모델 렌더링.
     */
    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) return;
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) reapplyAll(p);
        }, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) reapplyAll(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) reapplyAll(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 무기/방어구 스킨은 실제 ItemStack 을 수정하므로 종료 전 원복 필수.
        // 서버 저장 시 원본 데이터로 저장되도록 보장.
        weaponHandler.revertHand(event.getPlayer());
        armorHandler.revertAll(event.getPlayer());
    }

    /** 접속/월드이동/리스폰 후 DB에 장착된 모든 치장을 각 핸들러에 재적용. */
    private void reapplyAll(Player player) {
        Map<String, String> equipped = dataManager.getAllEquippedCosmetics(player.getUniqueId());
        for (Map.Entry<String, String> e : equipped.entrySet()) {
            CosmeticType type = CosmeticType.fromString(e.getKey());
            CosmeticDefinition def = registry.get(e.getValue());
            if (type == null || def == null) continue;
            switch (type) {
                case HAT, CHEST, LEGS, FEET -> armorHandler.apply(player, def);
                case WEAPON -> weaponHandler.apply(player, def);
                case WING -> wingHandler.apply(player, def);
                case TRAIL -> trailHandler.apply(player, def);
                case MOUNT -> { /* 탈것은 자동 재소환하지 않음 */ }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 방어구 슬롯 변경 감지 — applicable_to 조건 재평가용
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        // 플레이어가 방어구를 갈아입으면 applicable_to 조건이 달라질 수 있으므로 재평가.
        CosmeticType type = mapSlot(event.getSlotType());
        if (type == null) return;
        // 1틱 지연: 이벤트 처리 시점엔 getInventory().get*() 가 아직 이전 값을 반환할 수 있음.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) armorHandler.refreshSlot(event.getPlayer(), type);
        });
    }

    private CosmeticType mapSlot(PlayerArmorChangeEvent.SlotType slot) {
        return switch (slot) {
            case HEAD -> CosmeticType.HAT;
            case CHEST -> CosmeticType.CHEST;
            case LEGS -> CosmeticType.LEGS;
            case FEET -> CosmeticType.FEET;
        };
    }

    // ═══════════════════════════════════════════════════════════════════
    // NPC 우클릭
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!npcManager.isCosmeticNpc(event.getRightClicked())) return;
        event.setCancelled(true);
        mainGUI.open(event.getPlayer());
        event.getPlayer().playSound(event.getPlayer().getLocation(),
                Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 뽑기권 우클릭
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // 우클릭만 처리 (좌클릭 / 피지컬 제외)
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        // 주손에 들린 것만 처리 (보조손 이벤트는 중복이므로 무시)
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (!TicketManager.isTicket(item)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();

        // 연출 중 중복 실행 차단
        if (animationService.isPlaying(player.getUniqueId())) {
            player.sendMessage(Component.text("이미 뽑기가 진행 중입니다.").color(NamedTextColor.YELLOW));
            return;
        }

        // 종류 식별 후 풀 비어있는지 검증
        TicketManager.Kind kind = TicketManager.identify(item);
        if (kind == TicketManager.Kind.MOUNT) {
            if (mountRegistry.getAll().isEmpty()) {
                player.sendMessage(Component.text("뽑기에 사용할 탈것이 없습니다. 관리자에게 문의하세요.")
                        .color(NamedTextColor.RED));
                return;
            }
        } else {
            if (registry.getAll().isEmpty()) {
                player.sendMessage(Component.text("뽑기에 사용할 치장이 없습니다. 관리자에게 문의하세요.")
                        .color(NamedTextColor.RED));
                return;
            }
        }

        // 뽑기권 1장 소비
        if (!ticketManager.consumeOne(player, EquipmentSlot.HAND)) return;

        // 연출 시작 → 완료 시점에 실제 추첨/지급 (종류별 분기)
        UUID uuid = player.getUniqueId();
        if (kind == TicketManager.Kind.MOUNT) {
            animationService.play(player, () -> mountGachaService.drawAndApply(uuid), null);
        } else {
            animationService.play(player, () -> gachaService.drawAndApply(uuid), null);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 무기 스킨 드롭/사망 원복
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        ItemStack stack = dropped.getItemStack();
        boolean changed = false;
        if (WeaponHandler.isApplied(stack)) {
            weaponHandler.revertStack(stack);
            changed = true;
        }
        if (ArmorHandler.isApplied(stack)) {
            armorHandler.revertStack(stack);
            changed = true;
        }
        if (changed) dropped.setItemStack(stack);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // 사망 시 드롭되는 모든 아이템 원복 (다른 플레이어가 주워도 스킨 흔적 없도록)
        for (ItemStack stack : event.getDrops()) {
            if (WeaponHandler.isApplied(stack)) weaponHandler.revertStack(stack);
            if (ArmorHandler.isApplied(stack)) armorHandler.revertStack(stack);
        }
        // keepInventory true인 경우에도 주손/방어구 원복 (리스폰 후 재적용은 reapplyAll에서)
        weaponHandler.revertHand(event.getEntity());
        armorHandler.revertAll(event.getEntity());
    }

    // ═══════════════════════════════════════════════════════════════════
    // GUI 클릭 라우팅
    // ═══════════════════════════════════════════════════════════════════

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (mainGUI.isViewing(uuid)) {
            event.setCancelled(true);
            handleMainClick(player, event.getCurrentItem());
        } else if (wardrobeGUI.isViewing(uuid)) {
            event.setCancelled(true);
            handleWardrobeClick(player, event.getCurrentItem());
        } else if (exchangeGUI.isViewing(uuid)) {
            event.setCancelled(true);
            handleExchangeClick(player, event.getCurrentItem());
        } else if (mountGui.isViewing(uuid)) {
            event.setCancelled(true);
            handleMountClick(player, event.getCurrentItem());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();
        mainGUI.close(uuid);
        wardrobeGUI.close(uuid);
        exchangeGUI.close(uuid);
        mountGui.close(uuid);
    }

    // ─── 메인 GUI 처리 ───

    private void handleMainClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Optional<String> actionOpt = mainGUI.getAction(clicked);
        if (actionOpt.isEmpty()) return;

        switch (actionOpt.get()) {
            case "BUY_TICKET_1" -> buyTicket(player, 1, gachaConfig.getSinglePrice(), TicketManager.Kind.COSMETIC);
            case "BUY_TICKET_10" -> buyTicket(player, 10, gachaConfig.getTenPrice(), TicketManager.Kind.COSMETIC);
            case "BUY_MOUNT_TICKET_1" -> buyTicket(player, 1, gachaConfig.getMountSinglePrice(), TicketManager.Kind.MOUNT);
            case "BUY_MOUNT_TICKET_10" -> buyTicket(player, 10, gachaConfig.getMountTenPrice(), TicketManager.Kind.MOUNT);
            case "OPEN_WARDROBE" -> {
                closeAllViewers(player.getUniqueId());
                wardrobeGUI.open(player);
            }
            case "OPEN_EXCHANGE" -> {
                closeAllViewers(player.getUniqueId());
                exchangeGUI.open(player);
            }
        }
    }

    private void buyTicket(Player player, int amount, long price, TicketManager.Kind kind) {
        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            player.sendMessage(Component.text("경제 시스템을 불러올 수 없습니다.").color(NamedTextColor.RED));
            return;
        }

        if (!eco.subtractBalance(player.getUniqueId(), price)) {
            long balance = Math.max(0, eco.getBalance(player.getUniqueId()));
            player.sendMessage(Component.text("잔액이 부족합니다. 현재 잔액: " + String.format("%,d", balance) + "w")
                    .color(NamedTextColor.RED));
            return;
        }

        ItemStack ticket = (kind == TicketManager.Kind.MOUNT)
                ? ticketManager.createMountTicket(amount)
                : ticketManager.createTicket(amount);
        // 인벤토리 공간 체크
        var leftover = player.getInventory().addItem(ticket);
        if (!leftover.isEmpty()) {
            // 공간 부족 → 바닥에 드롭
            for (ItemStack left : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
            player.sendMessage(Component.text("인벤토리가 가득 차서 일부 뽑기권을 바닥에 떨어뜨렸습니다.")
                    .color(NamedTextColor.YELLOW));
        }

        player.sendMessage(Component.text("뽑기권 " + amount + "장 구매 완료. (" + String.format("%,d", price) + "w)")
                .color(NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // 메인 GUI 갱신 (잔액 표시)
        mainGUI.open(player);
    }

    // ─── 옷장 처리 ───

    private void handleWardrobeClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Optional<String> actionOpt = wardrobeGUI.getAction(clicked);
        if (actionOpt.isEmpty()) return;

        String action = actionOpt.get();
        String payload = wardrobeGUI.getPayload(clicked).orElse(null);

        switch (action) {
            case "BACK" -> {
                closeAllViewers(player.getUniqueId());
                mainGUI.open(player);
            }
            case "TAB" -> {
                if (payload != null) {
                    CosmeticType t = CosmeticType.fromString(payload);
                    if (t != null) wardrobeGUI.open(player, t);
                }
            }
            case "EQUIP" -> {
                if (payload != null && provider.equipCosmetic(player.getUniqueId(), payload)) {
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
                    wardrobeGUI.open(player);
                }
            }
            case "UNEQUIP" -> {
                if (payload != null && provider.unequipCosmetic(player.getUniqueId(), payload)) {
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 0.9f);
                    wardrobeGUI.open(player);
                }
            }
            case "PAGE_PREV" -> wardrobeGUI.openPage(player, -1);
            case "PAGE_NEXT" -> wardrobeGUI.openPage(player, +1);
        }
    }

    // ─── 교환소 처리 ───

    private void handleExchangeClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Optional<String> actionOpt = exchangeGUI.getAction(clicked);
        if (actionOpt.isEmpty()) return;

        String action = actionOpt.get();
        String payload = exchangeGUI.getPayload(clicked).orElse(null);

        switch (action) {
            case "BACK" -> {
                closeAllViewers(player.getUniqueId());
                mainGUI.open(player);
            }
            case "TAB" -> {
                if (payload != null) {
                    CosmeticRarity r = CosmeticRarity.fromString(payload);
                    if (r != null) exchangeGUI.open(player, r);
                }
            }
            case "EXCHANGE" -> {
                if (payload == null) return;
                ExchangeService.Result result = exchangeService.exchange(player.getUniqueId(), payload);
                switch (result) {
                    case SUCCESS -> {
                        CosmeticDefinition def = registry.get(payload);
                        player.sendMessage(Component.text("[교환] ", NamedTextColor.AQUA)
                                .append(Component.text((def != null ? def.getDisplayName() : payload) + " 획득!")));
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                        exchangeGUI.open(player);
                    }
                    case NOT_ENOUGH_SHARDS ->
                            player.sendMessage(Component.text("가루가 부족합니다.").color(NamedTextColor.RED));
                    case ALREADY_OWNED ->
                            player.sendMessage(Component.text("이미 보유 중입니다.").color(NamedTextColor.YELLOW));
                    case UNKNOWN_COSMETIC ->
                            player.sendMessage(Component.text("존재하지 않는 치장입니다.").color(NamedTextColor.RED));
                    case NOT_EXCHANGEABLE ->
                            player.sendMessage(Component.text("이 항목은 가루로 교환할 수 없습니다 (탈것은 탈것 뽑기권으로만).")
                                    .color(NamedTextColor.RED));
                    case FAILED ->
                            player.sendMessage(Component.text("교환에 실패했습니다.").color(NamedTextColor.RED));
                }
            }
        }
    }

    private void closeAllViewers(UUID uuid) {
        mainGUI.close(uuid);
        wardrobeGUI.close(uuid);
        exchangeGUI.close(uuid);
        mountGui.close(uuid);
    }

    // ─── 탈것 GUI 처리 ───

    private void handleMountClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        Optional<String> actionOpt = mountGui.getAction(clicked);
        if (actionOpt.isEmpty()) return;

        String action = actionOpt.get();
        String payload = mountGui.getPayload(clicked).orElse(null);
        UUID uuid = player.getUniqueId();

        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "SUMMON" -> {
                if (payload == null) return;
                MountDefinition def = mountRegistry.get(payload);
                if (def == null) {
                    player.sendMessage(Component.text("존재하지 않는 탈것입니다.").color(NamedTextColor.RED));
                    return;
                }
                if (!provider.hasCosmetic(uuid, def.getOwnershipKey())) {
                    player.sendMessage(Component.text("보유 중이지 않은 탈것입니다.").color(NamedTextColor.RED));
                    return;
                }
                player.closeInventory();
                mountManager.summon(player, def);
            }
            case "DESPAWN" -> {
                if (mountManager.despawn(uuid)) {
                    player.sendMessage(Component.text("탈것을 해제했습니다.").color(NamedTextColor.AQUA));
                }
                MountDefinition active = mountManager.getActiveDefinition(uuid);
                mountGui.open(player, active != null ? active.getId() : null);
            }
        }
    }
}
