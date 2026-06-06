package com.exit.gamble.stats;

import com.exit.core.api.GambleStatsProvider;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 슬롯·복권 누적 베팅/지급 통계.
 * gamble-stats.yml 에 영구화. AdminInspect 등에서 GambleStatsProvider 로 조회.
 */
public class GambleStatsManager implements GambleStatsProvider {

    private static final String DATA_FILE = "gamble-stats.yml";

    private final Plugin plugin;
    private final File file;

    // UUID → 4개 long 카운터
    private final Map<UUID, long[]> stats = new HashMap<>();
    private static final int IDX_SLOT_BET = 0;
    private static final int IDX_SLOT_PAYOUT = 1;
    private static final int IDX_LOTTERY_BET = 2;
    private static final int IDX_LOTTERY_PAYOUT = 3;

    public GambleStatsManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), DATA_FILE);
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long[] arr = new long[4];
                arr[IDX_SLOT_BET] = root.getLong(key + ".slot-bet", 0);
                arr[IDX_SLOT_PAYOUT] = root.getLong(key + ".slot-payout", 0);
                arr[IDX_LOTTERY_BET] = root.getLong(key + ".lottery-bet", 0);
                arr[IDX_LOTTERY_PAYOUT] = root.getLong(key + ".lottery-payout", 0);
                stats.put(uuid, arr);
            } catch (IllegalArgumentException ignored) {}
        }
        plugin.getLogger().info("[GambleStats] " + stats.size() + "명 통계 로드");
    }

    public synchronized void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, long[]> e : stats.entrySet()) {
            String base = "players." + e.getKey();
            long[] arr = e.getValue();
            cfg.set(base + ".slot-bet", arr[IDX_SLOT_BET]);
            cfg.set(base + ".slot-payout", arr[IDX_SLOT_PAYOUT]);
            cfg.set(base + ".lottery-bet", arr[IDX_LOTTERY_BET]);
            cfg.set(base + ".lottery-payout", arr[IDX_LOTTERY_PAYOUT]);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[GambleStats] gamble-stats.yml 저장 실패", e);
        }
    }

    private long[] getOrCreate(UUID uuid) {
        return stats.computeIfAbsent(uuid, k -> new long[4]);
    }

    // ─── 기록 ───

    public void addSlotBet(UUID player, long amount) {
        if (amount <= 0) return;
        getOrCreate(player)[IDX_SLOT_BET] += amount;
        save();
    }

    public void addSlotPayout(UUID player, long amount) {
        if (amount <= 0) return;
        getOrCreate(player)[IDX_SLOT_PAYOUT] += amount;
        save();
    }

    public void addLotteryBet(UUID player, long amount) {
        if (amount <= 0) return;
        getOrCreate(player)[IDX_LOTTERY_BET] += amount;
        save();
    }

    public void addLotteryPayout(UUID player, long amount) {
        if (amount <= 0) return;
        getOrCreate(player)[IDX_LOTTERY_PAYOUT] += amount;
        save();
    }

    // ─── Provider ───

    @Override
    public long getSlotBetTotal(UUID player) {
        long[] arr = stats.get(player);
        return arr == null ? 0 : arr[IDX_SLOT_BET];
    }

    @Override
    public long getSlotPayoutTotal(UUID player) {
        long[] arr = stats.get(player);
        return arr == null ? 0 : arr[IDX_SLOT_PAYOUT];
    }

    @Override
    public long getLotteryBetTotal(UUID player) {
        long[] arr = stats.get(player);
        return arr == null ? 0 : arr[IDX_LOTTERY_BET];
    }

    @Override
    public long getLotteryPayoutTotal(UUID player) {
        long[] arr = stats.get(player);
        return arr == null ? 0 : arr[IDX_LOTTERY_PAYOUT];
    }
}
