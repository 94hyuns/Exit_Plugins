package com.exit.customitems;

import com.exit.core.api.CustomConsumableProvider;
import com.exit.core.api.LampProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.customitems.consumable.ConsumableKeys;
import com.exit.customitems.consumable.CustomConsumableProviderImpl;
import com.exit.customitems.consumable.BigMacItem;
import com.exit.customitems.consumable.InvSaveItem;
import com.exit.customitems.consumable.InvSaveListener;
import com.exit.customitems.dummy.DummyCommand;
import com.exit.customitems.dummy.DummyKeys;
import com.exit.customitems.lamp.EquipTestCommand;
import com.exit.customitems.lamp.LampCommand;
import com.exit.customitems.lamp.LampConfig;
import com.exit.customitems.lamp.LampHandler;
import com.exit.customitems.lamp.LampItem;
import com.exit.customitems.lamp.LampKeys;
import com.exit.customitems.lamp.LampProviderImpl;
import com.exit.customitems.lamp.WildernessChecker;
import com.exit.customitems.lamp.mutation.ArmorMutationStrategy;
import com.exit.customitems.lamp.mutation.MutationApplier;
import com.exit.customitems.lamp.mutation.MutationStrategy;
import com.exit.customitems.lamp.mutation.WeaponMutationStrategy;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.EnchantRegistry;
import com.exit.customitems.lamp.enchant.EnchantRoller;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.LoreRenderer;
import com.exit.customitems.lamp.enchant.impl.combat.AgniEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ArmorBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.AttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BigVitalityEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BowAttackPowerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.BowCriticalEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.CriticalHitEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.QuickShotEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ExecutionerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.FastRecoveryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.FeastFuryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.GiantHunterEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.GoldenHeartEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.HeavyStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.HungerFuryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ApexBreakerEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LethalStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LifestealEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.LuckyEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.RushStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.SaturationKeeperEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.SwiftnessEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ThornsEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.ThunderStrikeEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.TripleShotEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.TwinShotEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.WillOWispEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.VitalityEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AdjacentMineEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AutoCastEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AutoReelEnchant;
import com.exit.customitems.lamp.enchant.impl.life.CropBoneMealEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExpBoostEnchant;
import com.exit.customitems.lamp.enchant.impl.life.HoeMasterEnchant;
import com.exit.customitems.lamp.enchant.impl.life.LuckyCropEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExplosiveMineEnchant;
import com.exit.customitems.lamp.enchant.impl.life.HunterEnchant;
import com.exit.customitems.lamp.enchant.impl.life.LumberBonusEnchant;
import com.exit.customitems.lamp.enchant.impl.life.MobLootEnchants;
import com.exit.customitems.lamp.enchant.impl.life.SandToGlassEnchant;
import com.exit.customitems.lamp.enchant.impl.life.SmeltedIngotEnchant;
import com.exit.customitems.lamp.enchant.impl.life.TreeCapacitorEnchant;
import com.exit.customitems.lamp.enchant.listener.ArmorAttributeManager;
import com.exit.customitems.lamp.enchant.listener.ArmorTickTask;
import com.exit.customitems.lamp.bulk.BulkLampCommand;
import com.exit.customitems.lamp.bulk.BulkLampKeys;
import com.exit.customitems.lamp.bulk.BulkLampListener;
import com.exit.customitems.lamp.bulk.BulkLampNPCManager;
import com.exit.customitems.lamp.enchant.listener.AgniFireListener;
import com.exit.customitems.lamp.enchant.listener.AxeListener;
import com.exit.customitems.lamp.enchant.listener.BowShootListener;
import com.exit.customitems.lamp.enchant.listener.FishingListener;
import com.exit.customitems.lamp.enchant.listener.HarvestListener;
import com.exit.customitems.lamp.enchant.listener.LastDamageTracker;
import com.exit.customitems.lamp.enchant.listener.MiningListener;
import com.exit.customitems.lamp.enchant.listener.MobKillListener;
import com.exit.customitems.lamp.enchant.listener.SaturationCapListener;
import com.exit.customitems.lamp.enchant.listener.ThornsListener;
import com.exit.customitems.lamp.enchant.listener.WeaponAttackListener;
import com.exit.customitems.util.CooldownTracker;
import com.exit.customitems.weapon.FrostmourneCommand;
import com.exit.customitems.weapon.FrostmourneItem;
import com.exit.customitems.weapon.FrostmourneSkillListener;
import com.exit.customitems.weapon.GreatSwordCommand;
import com.exit.customitems.weapon.GreatSwordItem;
import com.exit.customitems.weapon.GreatSwordSkillListener;
import com.exit.customitems.armor.ArmorKeys;
import com.exit.customitems.armor.WingedArmorCommand;
import com.exit.customitems.armor.AnubisArmorItem;
import com.exit.customitems.armor.WingedArmorItem;
import com.exit.customitems.weapon.WeaponKeys;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * CustomItems 플러그인 메인.
 * <p>구성: 램프 프레임워크(1단계) + 생활 인챈트 16종 + 효과 리스너 5종.
 * 전투 인챈트는 {@code TestAttackPowerEnchant} 만 남긴 상태이며 추후 단계에서 교체/추가한다.
 */
public class CustomItemsPlugin extends JavaPlugin {

    private LampConfig lampConfig;
    private EnchantConfig enchantConfig;
    private EnchantRegistry registry;
    private WildernessChecker wildernessChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // --- 설정 ---
        this.lampConfig = new LampConfig(this);
        lampConfig.reload();

        this.enchantConfig = new EnchantConfig(this);
        enchantConfig.reload();

        this.wildernessChecker = new WildernessChecker(lampConfig);

        // --- 레지스트리 및 인챈트 등록 ---
        this.registry = new EnchantRegistry();
        registerEnchants();

        // 등록된 인챈트 전체 목록을 plugins/CustomItems/enchantslist.html 에 dump (참조용, 브라우저로 열기)
        new com.exit.customitems.lamp.enchant.EnchantListHtmlWriter(this, registry)
                .writeTo(new java.io.File(getDataFolder(), "enchantslist.html"));

        // --- 서비스 조립 ---
        LampKeys keys = new LampKeys(this);
        LampItem lampItem = new LampItem(keys);
        EnchantStorage storage = new EnchantStorage(keys, registry);
        EnchantRoller roller = new EnchantRoller(registry, lampConfig, enchantConfig);
        LoreRenderer loreRenderer = new LoreRenderer(keys);
        EnchantDispatcher dispatcher = new EnchantDispatcher(storage);

        // --- 변성램프 조립 ---
        List<MutationStrategy> mutationStrategies = List.of(
                new WeaponMutationStrategy(registry),
                new ArmorMutationStrategy(registry, enchantConfig));
        MutationApplier mutationApplier = new MutationApplier(keys, storage, loreRenderer, mutationStrategies);
        getServer().getPluginManager().registerEvents(
                new com.exit.customitems.lamp.mutation.MutationDisplayMigrator(mutationApplier, storage), this);

        // --- 이벤트 / 명령어 ---
        PluginManager pm = getServer().getPluginManager();
        LampHandler lampHandler = new LampHandler(lampItem, roller, storage, loreRenderer, lampConfig, mutationApplier);
        pm.registerEvents(lampHandler, this);

        // --- 대량 램프 작업 (NPC + GUI) ---
        BulkLampKeys bulkKeys = new BulkLampKeys(this);
        BulkLampNPCManager bulkNpc = new BulkLampNPCManager(bulkKeys);
        pm.registerEvents(new BulkLampListener(bulkKeys, bulkNpc, lampHandler, lampItem, storage), this);
        PluginCommand bulkCmd = getCommand("lampbulk");
        if (bulkCmd != null) {
            BulkLampCommand bc = new BulkLampCommand(bulkKeys, bulkNpc, storage);
            bulkCmd.setExecutor(bc);
            bulkCmd.setTabCompleter(bc);
        } else {
            getLogger().warning("plugin.yml 에 'lampbulk' 명령어가 정의되지 않았습니다.");
        }

        pm.registerEvents(new MobKillListener(this, dispatcher), this);
        pm.registerEvents(new HarvestListener(this, dispatcher), this);
        pm.registerEvents(new MiningListener(this, dispatcher, wildernessChecker), this);
        pm.registerEvents(new com.exit.customitems.lamp.listener.NetherPortalListener(wildernessChecker), this);
        pm.registerEvents(new AxeListener(this, dispatcher), this);
        pm.registerEvents(new FishingListener(this, dispatcher), this);
        // ExpListener 제거 (2026-05-14) — 경험치 획득량 인챈트는 각 생활 리스너 내부에서 ExpOrb 드랍하도록 변경

        // --- 전투 리스너 / 스케줄러 ---
        CooldownTracker combatCooldowns = new CooldownTracker();
        LastDamageTracker damageTracker = new LastDamageTracker();
        pm.registerEvents(damageTracker, this);
        pm.registerEvents(new WeaponAttackListener(this, dispatcher, combatCooldowns, enchantConfig, storage), this);
        pm.registerEvents(new BowShootListener(this, dispatcher, enchantConfig), this);
        pm.registerEvents(new ThornsListener(this, storage, enchantConfig), this);
        pm.registerEvents(new SaturationCapListener(this, storage, enchantConfig), this);
        pm.registerEvents(new AgniFireListener(this, storage, enchantConfig), this);
        pm.registerEvents(new ArmorAttributeManager(this, storage, enchantConfig), this);
        // ArmorKeys 는 아래 갑옷 섹션에서도 재사용 — 미리 생성
        ArmorKeys armorKeys = new ArmorKeys(this);
        new ArmorTickTask(this, storage, enchantConfig, damageTracker, armorKeys).start();
        new com.exit.customitems.lamp.enchant.listener.WillOWispParticleTask(this, storage).start();

        // SET 인챈트 스킬 정보 명령어
        com.exit.customitems.lamp.enchant.SkillCommand skillCmd =
                new com.exit.customitems.lamp.enchant.SkillCommand(enchantConfig);
        for (String n : new String[]{"skilllist", "skillinfo"}) {
            PluginCommand pc = getCommand(n);
            if (pc != null) {
                pc.setExecutor(skillCmd);
                pc.setTabCompleter(skillCmd);
            } else {
                getLogger().warning("plugin.yml 에 '" + n + "' 명령어가 정의되지 않았습니다.");
            }
        }

        PluginCommand equipTestCmd = getCommand("equiptest");
        if (equipTestCmd != null) {
            EquipTestCommand etc = new EquipTestCommand(this, registry, storage, loreRenderer);
            equipTestCmd.setExecutor(etc);
            equipTestCmd.setTabCompleter(etc);
        } else {
            getLogger().warning("plugin.yml 에 'equiptest' 명령어가 정의되지 않았습니다.");
        }

        PluginCommand lampCmd = getCommand("lamp");
        if (lampCmd != null) {
            LampCommand handler = new LampCommand(lampItem);
            lampCmd.setExecutor(handler);
            lampCmd.setTabCompleter(handler);
        } else {
            getLogger().warning("plugin.yml 에 'lamp' 명령어가 정의되지 않았습니다.");
        }

        // --- 외부 플러그인 연동 ---
        // Core ServiceRegistry 에 LampProvider 등록. Shop 등이 이 경로로 램프 아이템 생성.
        ServiceRegistry.register(LampProvider.class, new LampProviderImpl(lampItem));

        // --- 소비 아이템 (인벤세이브 / 빅맥) ---
        ConsumableKeys consumableKeys = new ConsumableKeys(this);
        InvSaveItem invSaveItem = new InvSaveItem(consumableKeys);
        BigMacItem bigMacItem = new BigMacItem(consumableKeys);
        pm.registerEvents(new InvSaveListener(this, invSaveItem), this);
        ServiceRegistry.register(CustomConsumableProvider.class,
                new CustomConsumableProviderImpl(invSaveItem, bigMacItem));

        // --- 커스텀 무기 (Frostmourne) ---
        WeaponKeys weaponKeys = new WeaponKeys(this);
        FrostmourneItem frostmourneItem = new FrostmourneItem(weaponKeys);
        CooldownTracker frostmourneCooldowns = new CooldownTracker();
        pm.registerEvents(new FrostmourneSkillListener(this, frostmourneItem, frostmourneCooldowns), this);
        PluginCommand frostmourneCmd = getCommand("frostmourne");
        if (frostmourneCmd != null) {
            FrostmourneCommand fc = new FrostmourneCommand(frostmourneItem);
            frostmourneCmd.setExecutor(fc);
            frostmourneCmd.setTabCompleter(fc);
        } else {
            getLogger().warning("plugin.yml 에 'frostmourne' 명령어가 정의되지 않았습니다.");
        }

        GreatSwordItem greatSwordItem = new GreatSwordItem(weaponKeys);
        pm.registerEvents(new GreatSwordSkillListener(greatSwordItem, frostmourneCooldowns), this);
        PluginCommand greatSwordCmd = getCommand("greatsword");
        if (greatSwordCmd != null) {
            GreatSwordCommand gc = new GreatSwordCommand(greatSwordItem);
            greatSwordCmd.setExecutor(gc);
            greatSwordCmd.setTabCompleter(gc);
        } else {
            getLogger().warning("plugin.yml 에 'greatsword' 명령어가 정의되지 않았습니다.");
        }

        // --- 커스텀 갑옷 (용의 날개 — elytra + custom asset) ---
        // armorKeys 는 위에서 이미 생성됨 (ArmorTickTask 와 공유)
        WingedArmorItem wingedArmorItem = new WingedArmorItem(armorKeys);
        PluginCommand wingedArmorCmd = getCommand("wingedarmor");
        if (wingedArmorCmd != null) {
            WingedArmorCommand wc = new WingedArmorCommand(wingedArmorItem);
            wingedArmorCmd.setExecutor(wc);
            wingedArmorCmd.setTabCompleter(wc);
        } else {
            getLogger().warning("plugin.yml 에 'wingedarmor' 명령어가 정의되지 않았습니다.");
        }
        // --- 아누비스 방어구 세트 (보스2 드롭 — 투구/하의/신발) ---
        AnubisArmorItem anubisArmorItem = new AnubisArmorItem(armorKeys);
        PluginCommand anubisCmd = getCommand("anubisarmor");
        if (anubisCmd != null) {
            com.exit.customitems.armor.AnubisArmorCommand ac =
                    new com.exit.customitems.armor.AnubisArmorCommand(anubisArmorItem);
            anubisCmd.setExecutor(ac);
            anubisCmd.setTabCompleter(ac);
        } else {
            getLogger().warning("plugin.yml 에 'anubisarmor' 명령어가 정의되지 않았습니다.");
        }

        // Core WeaponProvider 등록 (DungeonRewards 등 외부에서 ItemStack 받아 사용)
        ServiceRegistry.register(com.exit.core.api.WeaponProvider.class,
                new com.exit.customitems.weapon.WeaponProviderImpl(
                        frostmourneItem, greatSwordItem, wingedArmorItem, anubisArmorItem));

        // --- 허수아비 v2 (vanilla COW 기반) + v1 legacy listener 유지 ---
        DummyKeys dummyKeys = new DummyKeys(this);
        pm.registerEvents(new com.exit.customitems.dummy.DummyDamageListener(dummyKeys), this);  // v1 legacy
        com.exit.customitems.dummy.NewDummyManager newDummyMgr =
                new com.exit.customitems.dummy.NewDummyManager(this, dummyKeys);
        newDummyMgr.start();
        pm.registerEvents(new com.exit.customitems.dummy.NewDummyListener(dummyKeys, newDummyMgr), this);
        PluginCommand dummyCmd = getCommand("dummy");
        if (dummyCmd != null) {
            DummyCommand dh = new DummyCommand(dummyKeys, newDummyMgr);
            dummyCmd.setExecutor(dh);
            dummyCmd.setTabCompleter(dh);
        } else {
            getLogger().warning("plugin.yml 에 'dummy' 명령어가 정의되지 않았습니다.");
        }

        getLogger().info("CustomItems 활성화 완료. 등록된 인챈트: " + registry.size() + "종.");
        getLogger().info("야생 월드: " + lampConfig.getWildernessWorlds());
    }

    @Override
    public void onDisable() {
        ServiceRegistry.unregister(LampProvider.class);
        ServiceRegistry.unregister(CustomConsumableProvider.class);
        getLogger().info("CustomItems 비활성화.");
    }

    private void registerEnchants() {
        // === 생활 인챈트 (15종) ===

        // 공통 - 동물/몬스터 처치 전리품
        // [TEMP] 특수 인챈트 테스트 편의를 위해 잠시 주석. 풀 크기 줄여 다른 인챈트가 잘 롤되도록.
        // registry.register(new MobLootEnchants.ChickenLoot(this));
        // registry.register(new MobLootEnchants.RabbitLoot(this));
        // registry.register(new MobLootEnchants.PigLoot(this));
        // registry.register(new MobLootEnchants.SheepLoot(this));
        // registry.register(new MobLootEnchants.CowLoot(this));
        // registry.register(new MobLootEnchants.MonsterLoot(this));

        // 공통 - 농작물/경험치/사냥꾼
        registry.register(new CropBoneMealEnchant(this));
        registry.register(new ExpBoostEnchant(this));
        registry.register(new HunterEnchant(this));

        // 곡괭이 전용
        registry.register(new SmeltedIngotEnchant(this));
        registry.register(new AdjacentMineEnchant(this));
        registry.register(new ExplosiveMineEnchant(this));

        // 괭이 전용 (2026-05-14: BonusCrop/BonusSeed 폐지 → 행운의 작물 + 괭이의 달인)
        registry.register(new LuckyCropEnchant(this));
        registry.register(new HoeMasterEnchant(this));

        // 삽 전용
        registry.register(new SandToGlassEnchant(this));

        // 낚싯대 전용
        registry.register(new AutoReelEnchant(this));
        registry.register(new AutoCastEnchant(this));

        // 도끼 전용
        registry.register(new LumberBonusEnchant(this));
        registry.register(new TreeCapacitorEnchant(this));

        // === 전투 인챈트 (14종: BASIC 6 + SET 8) — 수치는 enchants.yml ===

        EnchantConfig ec = enchantConfig;

        // 무기 기본 — 근접 공용 (2)
        registry.register(new AttackPowerEnchant(this, ec));
        registry.register(new LifestealEnchant(this, ec));
        // 근접 치명타 제거 (2026-05-12) — 데미지 안정성을 위해 BASIC 풀에서 빠짐. 활 치명타는 유지.

        // 무기 기본 — 활 전용 (2 신규)
        registry.register(new BowAttackPowerEnchant(this, ec));
        registry.register(new BowCriticalEnchant(this, ec));

        // 무기 유니크 (4 근접 + 3 공통 + 4 무기별)
        registry.register(new GiantHunterEnchant(this, ec));
        registry.register(new FeastFuryEnchant(this, ec));
        registry.register(new HungerFuryEnchant(this, ec));
        registry.register(new ExecutionerEnchant(this, ec));
        registry.register(new HeavyStrikeEnchant(this, ec));   // 묵직한 일격 — 공통, 안정딜
        registry.register(new ApexBreakerEnchant(this, ec));   // 정면승부 — 공통, HP≥50%
        registry.register(new LethalStrikeEnchant(this, ec));  // 치명타 — 공통, 확률 발동
        registry.register(new RushStrikeEnchant(this, ec));    // 창 전용
        registry.register(new ThunderStrikeEnchant(this, ec)); // 철퇴 전용
        registry.register(new TwinShotEnchant(this, ec));      // 활 전용 (이연사)
        registry.register(new TripleShotEnchant(this, ec));    // 활 전용 (삼연사)
        registry.register(new QuickShotEnchant(this, ec));     // 활 전용

        // 방어구 기본 (3 + 1 신규)
        registry.register(new ArmorBoostEnchant(this, ec));
        registry.register(new VitalityEnchant(this, ec));
        registry.register(new GoldenHeartEnchant(this, ec));
        registry.register(new SwiftnessEnchant(this, ec));     // 이동속도

        // 방어구 세트 (5 — 가시·빠른회복 비활성화, 아그니·도깨비불 도입)
        registry.register(new SaturationKeeperEnchant(this, ec));
        // registry.register(new FastRecoveryEnchant(this, ec));  // 비활성화 (도깨비불로 대체)
        registry.register(new BigVitalityEnchant(this, ec));
        // registry.register(new ThornsEnchant(this, ec));  // 비활성화 (아그니로 대체)
        registry.register(new AgniEnchant(this, ec));        // 아그니 (불 데미지 증폭)
        registry.register(new WillOWispEnchant(this, ec));   // 도깨비불 (파티클 + 깡뎀)
        registry.register(new LuckyEnchant(this, ec));       // 행운

        // SetLevelCounter 가 행운 시너지 카운트를 위해 keyOf 필요
        com.exit.customitems.lamp.enchant.SetLevelCounter.setLuckyKey(LuckyEnchant.keyOf(this));
    }

    public LampConfig getLampConfig()     { return lampConfig; }
    public EnchantRegistry getRegistry()  { return registry; }
    public WildernessChecker getWildernessChecker() { return wildernessChecker; }
}
