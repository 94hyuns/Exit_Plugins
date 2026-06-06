package com.exit.gamble;

import com.exit.core.api.GambleStatsProvider;
import com.exit.core.api.NpcInfo;
import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import com.exit.core.registry.ServiceRegistry;
import com.exit.gamble.stats.GambleStatsManager;
import com.exit.gamble.lottery.LotteryManager;
import com.exit.gamble.lottery.command.LotteryCommand;
import com.exit.gamble.lottery.config.LotteryConfig;
import com.exit.gamble.lottery.gui.LotteryGui;
import com.exit.gamble.lottery.listener.LotteryClickListener;
import com.exit.gamble.lottery.scheduler.LotteryScheduler;
import com.exit.gamble.slot.command.SlotCommand;
import com.exit.gamble.slot.config.SlotConfig;
import com.exit.gamble.slot.engine.ReelEngine;
import com.exit.gamble.slot.gui.SlotActionKeys;
import com.exit.gamble.slot.gui.SlotGui;
import com.exit.gamble.slot.listener.SlotClickListener;
import com.exit.gamble.slot.world.SlotMachine;
import com.exit.gamble.slot.world.SlotMachineManager;
import com.exit.gamble.slot.world.SlotMachineMirror;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ExitGamblePlugin extends JavaPlugin {

    private SlotMachineManager machineManager;
    private LotteryManager lotteryManager;
    private LotteryScheduler lotteryScheduler;
    private GambleStatsManager statsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // ─── 통계 (슬롯+복권 공용) ───
        statsManager = new GambleStatsManager(this);
        statsManager.load();
        ServiceRegistry.register(GambleStatsProvider.class, statsManager);

        // ─── Slot ───
        SlotConfig slotConfig = new SlotConfig(this);
        slotConfig.load();

        ReelEngine engine = new ReelEngine(slotConfig);
        SlotActionKeys keys = new SlotActionKeys(this);
        SlotGui slotGui = new SlotGui(slotConfig, keys);

        machineManager = new SlotMachineManager(this);
        machineManager.load();
        SlotMachineMirror mirror = new SlotMachineMirror();

        getServer().getPluginManager().registerEvents(
                new SlotClickListener(this, slotConfig, engine, slotGui, keys, mirror, machineManager, statsManager), this);

        SlotCommand slotCmd = new SlotCommand(this, slotConfig, slotGui, machineManager, mirror);
        for (String name : new String[]{
                "슬롯머신", "슬롯리로드", "슬롯테스트",
                "슬롯머신스폰", "슬롯머신제거", "슬롯머신목록",
                "슬롯머신NPC스폰", "슬롯머신NPC제거"}) {
            var pc = getCommand(name);
            if (pc != null) pc.setExecutor(slotCmd);
            else getLogger().warning("plugin.yml 에 명령어 '" + name + "' 없음");
        }

        // ─── Lottery ───
        LotteryConfig lotteryConfig = new LotteryConfig(this);
        lotteryConfig.load();

        lotteryManager = new LotteryManager(this, lotteryConfig, statsManager);
        lotteryManager.load();

        LotteryGui lotteryGui = new LotteryGui(this, lotteryManager);
        getServer().getPluginManager().registerEvents(
                new LotteryClickListener(lotteryGui, lotteryManager), this);

        LotteryCommand lotteryCmd = new LotteryCommand(lotteryManager, lotteryGui);
        for (String name : new String[]{
                "복권", "복권구매", "복권내역", "복권상태", "복권결과", "복권추첨",
                "복권NPC스폰", "복권NPC제거"}) {
            var pc = getCommand(name);
            if (pc != null) pc.setExecutor(lotteryCmd);
            else getLogger().warning("plugin.yml 에 명령어 '" + name + "' 없음");
        }

        lotteryScheduler = new LotteryScheduler(this, lotteryManager);
        lotteryScheduler.start();

        // ─── NPC 클릭 핸들러 (slot_ + lottery_ prefix 분기) ───
        Bukkit.getScheduler().runTaskLater(this, () -> {
            NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
            if (npc == null) {
                getLogger().warning("[ExitGamble] Core NpcService 미등록 — NPC 클릭 비활성화 (Core 1.6.0+ 필요)");
                return;
            }
            npc.registerClickHandler(SlotMachineManager.NPC_OWNER, (player, npcId, attack) -> {
                if (attack) return;
                if (npcId.startsWith("slot_")) {
                    machineManager.findByNpcId(npcId).ifPresentOrElse(
                            (SlotMachine m) -> slotCmd.enterFromNpc(player, m),
                            () -> getLogger().warning("[ExitGamble] 알 수 없는 slot npcId: " + npcId)
                    );
                } else if (npcId.startsWith("lottery_")) {
                    lotteryGui.open(player);
                } else {
                    getLogger().warning("[ExitGamble] 알 수 없는 gamble npcId prefix: " + npcId);
                }
            });
            getLogger().info("[ExitGamble] NPC 클릭 핸들러 등록 완료 (slot_ + lottery_)");
        }, 30L);

        // ─── NPC 이름 enforce (매 부팅) ───
        // Core 의 NPC 로드 후 실행. owner=gamble NPC 의 displayName 이 사용자 입력 id 와
        // 다르면 (예: 옛 1.6.0 의 displayName 미저장 버그로 "NPC" 가 된 케이스) 자동 복구.
        Bukkit.getScheduler().runTaskLater(this, this::enforceGambleNpcNames, 80L);

        getLogger().info("ExitGamble enabled (slot + lottery + NPC)");
    }

    private void enforceGambleNpcNames() {
        NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
        if (npc == null) return;
        int fixed = 0;
        for (NpcInfo info : npc.getByOwner(SlotMachineManager.NPC_OWNER)) {
            String expected = SlotMachineManager.stripNpcPrefix(info.id());
            if (expected == null) continue;
            if (expected.equals(info.displayName())) continue;
            String skin = info.skinOwner() == null ? "Steve" : info.skinOwner();
            npc.remove(SlotMachineManager.NPC_OWNER, info.id());
            boolean ok = npc.spawn(new NpcSpawnSpec(
                    SlotMachineManager.NPC_OWNER, info.id(),
                    info.location(), expected, skin, true));
            if (ok) {
                fixed++;
                getLogger().info("[ExitGamble] NPC '" + expected + "' 이름 복구 (이전: " + info.displayName() + ")");
            }
        }
        if (fixed > 0) {
            getLogger().info("[ExitGamble] " + fixed + "개 NPC 이름 자동 복구 완료");
        }
    }

    @Override
    public void onDisable() {
        if (lotteryScheduler != null) lotteryScheduler.stop();
        if (lotteryManager != null) lotteryManager.save();
        if (machineManager != null) machineManager.save();
        if (statsManager != null) statsManager.save();
        ServiceRegistry.unregister(GambleStatsProvider.class);
        getLogger().info("ExitGamble disabled");
    }
}
