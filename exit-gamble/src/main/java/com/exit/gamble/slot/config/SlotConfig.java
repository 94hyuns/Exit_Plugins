package com.exit.gamble.slot.config;

import com.exit.gamble.slot.symbol.SlotSymbol;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SlotConfig {

    private final Plugin plugin;
    private List<Long> bets = List.of(100L, 1000L, 10000L);
    private final Map<String, SlotSymbol> symbols = new LinkedHashMap<>();
    private String jackpotSymbolId = "SEVEN";
    private double nearMissProbability = 0.5;
    private double twoMatchCompletionProbability = 0.015;
    private int tickInterval = 2;
    private int reel1StopTick = 30;
    private int reel2StopTick = 45;
    private int reel3StopTick = 60;

    public SlotConfig(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        var root = plugin.getConfig().getConfigurationSection("slot");
        if (root == null) {
            plugin.getLogger().warning("config.yml에 slot 섹션이 없습니다. 기본값 사용");
            loadDefaultSymbols();
            return;
        }

        List<Integer> betInts = root.getIntegerList("bets");
        if (betInts != null && !betInts.isEmpty()) {
            List<Long> parsed = new ArrayList<>(betInts.size());
            for (int b : betInts) parsed.add((long) b);
            this.bets = Collections.unmodifiableList(parsed);
        }

        symbols.clear();
        ConfigurationSection symSec = root.getConfigurationSection("symbols");
        if (symSec != null) {
            boolean usingPercent = false;
            int percentSum = 0;
            for (String id : symSec.getKeys(false)) {
                ConfigurationSection s = symSec.getConfigurationSection(id);
                if (s == null) continue;

                // 'percent' 가 있으면 우선 사용. 없으면 'weight' fallback.
                // percent 는 단일 심볼 등장 확률(%) — 모두 합이 100 이어야 함.
                int weight;
                if (s.isSet("percent")) {
                    int pct = s.getInt("percent", 1);
                    weight = Math.max(1, pct);
                    usingPercent = true;
                    percentSum += pct;
                } else {
                    weight = s.getInt("weight", 1);
                }

                int payout3 = s.getInt("payout3", 1);
                String matName = s.getString("material", "STONE");
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    plugin.getLogger().log(Level.WARNING,
                            "심볼 " + id + " material 알 수 없음: " + matName + " → STONE 사용");
                    mat = Material.STONE;
                }
                symbols.put(id, new SlotSymbol(id, id, mat, weight, payout3));
            }
            if (usingPercent && percentSum != 100) {
                plugin.getLogger().warning(
                        "[Slot] percent 합계가 100 이 아님 (" + percentSum + "). " +
                        "정확한 단일 확률을 원하면 합을 100 으로 맞추세요. " +
                        "(현재는 상대 가중치로 정규화되어 동작은 함)");
            }
        }
        if (symbols.isEmpty()) loadDefaultSymbols();

        this.jackpotSymbolId = root.getString("jackpot-symbol", "SEVEN");
        this.nearMissProbability = Math.max(0.0, Math.min(1.0,
                root.getDouble("near-miss-probability", 0.5)));
        this.twoMatchCompletionProbability = Math.max(0.0, Math.min(1.0,
                root.getDouble("two-match-completion-probability", 0.015)));

        ConfigurationSection reel = root.getConfigurationSection("reel");
        if (reel != null) {
            this.tickInterval = Math.max(1, reel.getInt("tick-interval", 2));
            this.reel1StopTick = Math.max(1, reel.getInt("reel1-stop-tick", 30));
            this.reel2StopTick = Math.max(reel1StopTick + 1, reel.getInt("reel2-stop-tick", 45));
            this.reel3StopTick = Math.max(reel2StopTick + 1, reel.getInt("reel3-stop-tick", 60));
        }
    }

    private void loadDefaultSymbols() {
        symbols.clear();
        symbols.put("CHERRY",  new SlotSymbol("CHERRY",  "CHERRY",  Material.SWEET_BERRIES, 30, 2));
        symbols.put("LEMON",   new SlotSymbol("LEMON",   "LEMON",   Material.GLOW_BERRIES,  25, 3));
        symbols.put("BELL",    new SlotSymbol("BELL",    "BELL",    Material.BELL,          18, 5));
        symbols.put("STAR",    new SlotSymbol("STAR",    "STAR",    Material.NETHER_STAR,   12, 10));
        symbols.put("DIAMOND", new SlotSymbol("DIAMOND", "DIAMOND", Material.DIAMOND,        8, 25));
        symbols.put("SEVEN",   new SlotSymbol("SEVEN",   "SEVEN",   Material.GOLD_INGOT,     2, 100));
    }

    public List<Long> bets() { return bets; }
    public List<SlotSymbol> symbols() { return List.copyOf(symbols.values()); }
    public String jackpotSymbolId() { return jackpotSymbolId; }
    public double nearMissProbability() { return nearMissProbability; }
    public double twoMatchCompletionProbability() { return twoMatchCompletionProbability; }
    public int tickInterval() { return tickInterval; }
    public int reel1StopTick() { return reel1StopTick; }
    public int reel2StopTick() { return reel2StopTick; }
    public int reel3StopTick() { return reel3StopTick; }
}
