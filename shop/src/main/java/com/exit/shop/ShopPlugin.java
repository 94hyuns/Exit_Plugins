package com.exit.shop;

import com.exit.core.api.NpcService;
import com.exit.core.registry.ServiceRegistry;
import com.exit.shop.command.ShopCommand;
import com.exit.shop.gui.ShopButtonStyleRegistry;
import com.exit.shop.gui.ShopGUI;
import com.exit.shop.listener.ShopListener;
import com.exit.shop.model.ShopCategory;
import com.exit.shop.model.ShopItemRegistry;
import com.exit.shop.npc.ShopNPCManager;
import com.exit.shop.price.PriceManager;
import com.exit.shop.stats.TransactionLog;
import com.exit.shop.tab.ShopTabRegistry;
import com.exit.core.api.ShopStatsRecorder;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Shop 플러그인 메인 클래스.
 *
 * NMS 패킷 기반 가짜 플레이어 NPC + Chest GUI 상점.
 * ⚠ Paper 1.21.x Mojang-mapped 환경 전용.
 */
public class ShopPlugin extends JavaPlugin {

    private static ShopPlugin INSTANCE;

    private ShopItemRegistry itemRegistry;
    private ShopTabRegistry tabRegistry;
    private ShopButtonStyleRegistry buttonStyleRegistry;
    private PriceManager priceManager;
    private ShopNPCManager npcManager;
    private ShopGUI shopGUI;
    private TransactionLog transactionLog;

    public static ShopPlugin getInstance() { return INSTANCE; }

    @Override
    public void onEnable() {
        INSTANCE = this;

        // 0. 기본 config.yml을 데이터 폴더로 복사 (최초 1회)
        saveDefaultConfig();

        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");

        // 1. 아이템 레지스트리
        itemRegistry = new ShopItemRegistry();
        itemRegistry.loadFromConfig(configFile, getLogger());

        // 2. 탭 레지스트리 (config.yml의 tabs 섹션)
        tabRegistry = new ShopTabRegistry();
        tabRegistry.loadFromConfig(configFile, getLogger());

        // 2.5. 버튼 스타일 레지스트리 (config.yml의 gui 섹션)
        buttonStyleRegistry = new ShopButtonStyleRegistry();
        buttonStyleRegistry.loadFromConfig(configFile, getLogger());

        // 3. 가격 엔진
        priceManager = new PriceManager(this, itemRegistry);

        // 3.5. 거래 로그 + ServiceRegistry 등록 (Fishing 등 외부 플러그인이 fish sell 기록 가능)
        transactionLog = new TransactionLog(this);
        transactionLog.load();
        com.exit.core.registry.ServiceRegistry.register(ShopStatsRecorder.class, transactionLog);

        // 4. NPC 매니저 (아직 스폰하지 않음)
        npcManager = new ShopNPCManager(this);

        // 5. GUI 매니저
        shopGUI = new ShopGUI(itemRegistry, tabRegistry, buttonStyleRegistry, priceManager);

        // 6. 이벤트 리스너
        ShopListener shopListener = new ShopListener(this, npcManager, shopGUI, itemRegistry, priceManager, transactionLog);
        getServer().getPluginManager().registerEvents(shopListener, this);

        // 7. 명령어
        ShopCommand cmd = new ShopCommand(npcManager, itemRegistry, priceManager);
        getCommand("shop").setExecutor(cmd);
        getCommand("shop").setTabCompleter(cmd);
        com.exit.shop.command.PriceTimerCommand priceTimerCmd =
                new com.exit.shop.command.PriceTimerCommand(priceManager);
        getCommand("시세변동").setExecutor(priceTimerCmd);
        getCommand("시세변동").setTabCompleter(priceTimerCmd);

        // 8. 시세 갱신 스케줄러 (5초마다 게임 날짜 체크)
        getServer().getScheduler().runTaskTimer(this, () -> priceManager.tick(), 100L, 100L);

        // 8.6. transactionLog 자동 flush (5분마다)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> transactionLog.save(), 6000L, 6000L);

        // 9. NPC 클릭 핸들러 등록 + 첫 부팅 시 npcs.yml 마이그레이션 (Core 가 초기화된 후 — 1초 지연)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
            if (npc == null) {
                getLogger().severe("[Shop] Core NpcService 미등록 — NPC 기능 비활성화. Core 1.6.0+ 필요");
            } else {
                npc.registerClickHandler(ShopNPCManager.OWNER, (player, npcId, attack) -> {
                    if (attack) return;
                    ShopCategory cat = ShopNPCManager.fromNpcId(npcId);
                    if (cat == null) return;
                    shopListener.onNpcClick(player, cat);
                });
            }
            npcManager.loadAndSpawn(); // 마이그레이션 1회 (이후 부팅은 no-op)
            getLogger().info("[Shop] 활성화 완료! 광물 " +
                    itemRegistry.getByCategory(ShopCategory.MINERAL).size() + "종, 작물 " +
                    itemRegistry.getByCategory(ShopCategory.CROP).size() + "종, 램프 " +
                    itemRegistry.getByCategory(ShopCategory.LAMP).size() + "종, 잡화 " +
                    itemRegistry.getByCategory(ShopCategory.GENERAL).size() + "종, 던전 " +
                    itemRegistry.getByCategory(ShopCategory.DUNGEON).size() + "종, 요리 " +
                    itemRegistry.getByCategory(ShopCategory.COOKING).size() + "종 (낚시는 동적)");
        }, 60L);
    }

    @Override
    public void onDisable() {
        // Core 가 NPC 정리 담당. Shop 은 가격 + 거래로그 저장.
        if (priceManager != null) priceManager.save();
        if (transactionLog != null) transactionLog.save();
        getLogger().info("[Shop] 상점 플러그인 비활성화.");
    }

    public TransactionLog getTransactionLog() { return transactionLog; }

    public ShopItemRegistry getItemRegistry() { return itemRegistry; }
    public ShopTabRegistry getTabRegistry() { return tabRegistry; }
    public ShopButtonStyleRegistry getButtonStyleRegistry() { return buttonStyleRegistry; }
    public PriceManager getPriceManager() { return priceManager; }
    public ShopNPCManager getNpcManager() { return npcManager; }
}
