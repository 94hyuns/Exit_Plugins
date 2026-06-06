package com.exit.cosmetics;

import com.exit.core.CorePlugin;
import com.exit.core.api.CosmeticProvider;
import com.exit.core.data.PlayerDataManager;
import com.exit.core.registry.ServiceRegistry;
import com.exit.cosmetics.command.CosmeticCommand;
import com.exit.cosmetics.command.ShardCommand;
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
import com.exit.cosmetics.listener.CosmeticListener;
import com.exit.cosmetics.mount.MountListener;
import com.exit.cosmetics.mount.MountManager;
import com.exit.cosmetics.mount.MountRegistry;
import com.exit.cosmetics.mount.MountRideCommand;
import com.exit.cosmetics.npc.CosmeticNpcManager;
import com.exit.cosmetics.provider.CosmeticProviderImpl;
import com.exit.cosmetics.registry.CosmeticListHtmlWriter;
import com.exit.cosmetics.registry.CosmeticRegistry;
import com.exit.cosmetics.ticket.AnimationService;
import com.exit.cosmetics.ticket.TicketManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public class CosmeticsPlugin extends JavaPlugin {

    private CosmeticRegistry registry;
    private MountRegistry mountRegistry;
    private GachaConfig gachaConfig;
    private CosmeticNpcManager npcManager;
    private ArmorHandler armorHandler;
    private WeaponHandler weaponHandler;
    private WingHandler wingHandler;
    private TrailHandler trailHandler;
    private CosmeticProvider cosmeticProvider;
    private TicketManager ticketManager;
    private AnimationService animationService;
    private MountManager mountManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // mounts.yml 데이터 폴더에 저장 (없을 때만)
        saveResource("mounts.yml", false);

        CorePlugin core = (CorePlugin) getServer().getPluginManager().getPlugin("Core");
        if (core == null) {
            getLogger().severe("Core 플러그인이 필요합니다. 비활성화합니다.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        PlayerDataManager dataManager = core.getPlayerDataManager();

        // ── 1. 카탈로그/설정 로드 ──
        registry = new CosmeticRegistry(getLogger());
        registry.load(getConfig());
        mountRegistry = new MountRegistry(getLogger(), registry);
        mountRegistry.load(new File(getDataFolder(), "mounts.yml"));
        gachaConfig = new GachaConfig(getConfig());

        // ── 2. 치장 핸들러 ──
        armorHandler = new ArmorHandler(this, registry);
        weaponHandler = new WeaponHandler(this, registry);
        wingHandler = new WingHandler(registry);
        trailHandler = new TrailHandler(this, registry);
        armorHandler.start();
        weaponHandler.start();

        // ── 3. Provider 등록 ──
        cosmeticProvider = new CosmeticProviderImpl(dataManager, registry,
                armorHandler, weaponHandler, wingHandler, trailHandler);
        ServiceRegistry.register(CosmeticProvider.class, cosmeticProvider);

        // ── 4. 서비스 ──
        GachaService gachaService = new GachaService(dataManager, registry, gachaConfig);
        ExchangeService exchangeService = new ExchangeService(dataManager, registry, gachaConfig);
        MountGachaService mountGachaService = new MountGachaService(dataManager, mountRegistry, registry,
                cosmeticProvider, gachaConfig);

        // ── 5. 뽑기권 + 연출 ──
        ticketManager = new TicketManager();
        ticketManager.load(getConfig());
        animationService = new AnimationService(this);
        animationService.load(getConfig());

        // ── 6. 탈것 시스템 ──
        mountManager = new MountManager(this);
        MountGui mountGui = new MountGui(mountRegistry, cosmeticProvider);

        // ── 7. GUI ──
        GachaMainGUI mainGUI = new GachaMainGUI(dataManager, gachaConfig);
        WardrobeGUI wardrobeGUI = new WardrobeGUI(registry, dataManager, cosmeticProvider);
        ExchangeGUI exchangeGUI = new ExchangeGUI(registry, dataManager, cosmeticProvider, gachaConfig);

        // ── 8. NPC ──
        npcManager = new CosmeticNpcManager(this);
        npcManager.loadLocation(getConfig());
        getServer().getScheduler().runTask(this, () -> npcManager.spawnIfConfigured());

        // ── 9. 리스너 ──
        CosmeticListener listener = new CosmeticListener(
                this, dataManager, registry, cosmeticProvider, npcManager,
                armorHandler, weaponHandler, wingHandler, trailHandler,
                gachaService, exchangeService,
                gachaConfig, ticketManager, animationService,
                mainGUI, wardrobeGUI, exchangeGUI,
                mountGui, mountManager, mountRegistry, mountGachaService
        );
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new MountListener(mountManager), this);

        // ── 10. 명령어 ──
        CosmeticCommand command = new CosmeticCommand(this, npcManager, cosmeticProvider, registry, ticketManager);
        Objects.requireNonNull(getCommand("치장")).setExecutor(command);
        Objects.requireNonNull(getCommand("치장")).setTabCompleter(command);

        MountRideCommand rideCommand = new MountRideCommand(mountManager, mountGui);
        Objects.requireNonNull(getCommand("ride")).setExecutor(rideCommand);

        ShardCommand shardCommand = new ShardCommand(dataManager);
        Objects.requireNonNull(getCommand("가루")).setExecutor(shardCommand);
        Objects.requireNonNull(getCommand("가루")).setTabCompleter(shardCommand);

        // 카탈로그 HTML dump — 등급 결정·기획용
        new CosmeticListHtmlWriter(this, registry)
                .writeTo(new File(getDataFolder(), "cosmeticslist.html"));

        getLogger().info("Cosmetics 플러그인 활성화 완료.");
    }

    @Override
    public void onDisable() {
        if (armorHandler != null) armorHandler.shutdownAll();
        if (weaponHandler != null) weaponHandler.shutdownAll();
        if (wingHandler != null) wingHandler.shutdownAll();
        if (trailHandler != null) trailHandler.shutdownAll();
        if (mountManager != null) mountManager.shutdownAll();
        ServiceRegistry.unregister(CosmeticProvider.class);
        getLogger().info("Cosmetics 플러그인 비활성화 완료.");
    }

    /** /치장 reload. 카탈로그 재로드. 뽑기 가격/가중치는 재시작 시 반영. */
    public void reloadAll() {
        reloadConfig();
        registry.load(getConfig());
        if (mountRegistry != null) {
            mountRegistry.load(new File(getDataFolder(), "mounts.yml"));
        }
        ticketManager.load(getConfig());
        animationService.load(getConfig());
        // NPC 이름/외형 갱신을 위해 재스폰 (위치 동일)
        if (npcManager != null) {
            npcManager.loadLocation(getConfig());
            npcManager.spawnIfConfigured();
        }
        // 카탈로그 HTML 재생성
        new CosmeticListHtmlWriter(this, registry)
                .writeTo(new File(getDataFolder(), "cosmeticslist.html"));
        getLogger().info("[Cosmetics] 카탈로그/탈것/뽑기권/연출 설정 재로드. 뽑기 가격/가중치는 재시작 시 반영.");
    }
}
