package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.armor.AnubisArmorItem;
import com.exit.customitems.armor.ArmorKeys;
import com.exit.customitems.lamp.enchant.EnchantConfig;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.SetLevelCounter;
import com.exit.customitems.lamp.enchant.impl.combat.FastRecoveryEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.GoldenHeartEnchant;
import com.exit.customitems.lamp.enchant.impl.combat.SaturationKeeperEnchant;
import com.exit.customitems.util.NumUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 매 1초(20틱)마다 모든 온라인 플레이어를 순회하며 PASSIVE 방어구 효과를 처리.
 *
 * <ul>
 *   <li>황금 심장 (BASIC): 부위별 (interval, cap) 값 사용. 가장 짧은 주기 / 가장 큰 캡 채택.</li>
 *   <li>빠른 회복 (SET): 부위 카운트 = 레벨. delay = base - per_level × level.</li>
 *   <li>포만 유지 (SET): 부위 카운트 = 레벨. threshold = base + per_level × level.</li>
 * </ul>
 */
public class ArmorTickTask extends BukkitRunnable {

    private final Plugin plugin;
    private final EnchantStorage storage;
    private final EnchantConfig ec;
    private final LastDamageTracker damageTracker;
    private final ArmorKeys armorKeys;

    private final NamespacedKey kGoldenHeart;
    private final NamespacedKey kFastRecovery;
    private final NamespacedKey kSaturationKeeper;

    /** player → 마지막 황금심장 발동 시각(ms). */
    private final Map<UUID, Long> lastGoldenHeart = new HashMap<>();

    public ArmorTickTask(Plugin plugin, EnchantStorage storage, EnchantConfig ec,
                         LastDamageTracker damageTracker, ArmorKeys armorKeys) {
        this.plugin = plugin;
        this.storage = storage;
        this.ec = ec;
        this.damageTracker = damageTracker;
        this.armorKeys = armorKeys;

        this.kGoldenHeart      = GoldenHeartEnchant.keyOf(plugin);
        this.kFastRecovery     = FastRecoveryEnchant.keyOf(plugin);
        this.kSaturationKeeper = SaturationKeeperEnchant.keyOf(plugin);
    }

    public void start() {
        runTaskTimer(plugin, 40L, 20L);  // 2초 후 시작, 1초 주기
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isDead()) continue;
            processPlayer(player, now);
        }
    }

    private void processPlayer(Player player, long now) {
        // 황금 심장 (BASIC) — 부위별 레벨 수집 → 최고 레벨로 주기 결정, 부위 수로 캡 결정
        int gh_maxLevel = 0;
        int gh_pieces = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null) continue;
            for (RolledEnchant r : storage.load(armor)) {
                if (r.enchant().getKey().equals(kGoldenHeart) && r.values().length >= 1) {
                    int level = (int) NumUtil.fromStored(r.values()[0]);
                    gh_maxLevel = Math.max(gh_maxLevel, level);
                    gh_pieces++;
                    break;  // 한 부위에 황금심장 줄이 여러 개여도 부위 카운트는 1만
                }
            }
        }

        if (gh_maxLevel > 0) {
            long intervalMs = GoldenHeartEnchant.levelToInterval(gh_maxLevel) * 1000L;
            // 캡(칸) → 마크 내부 단위(×2). 1칸 = absorption 2.0.
            int capCells = GoldenHeartEnchant.piecesToCap(gh_pieces);
            double capInternal = capCells * 2.0;
            long lastGrant = lastGoldenHeart.getOrDefault(player.getUniqueId(), 0L);
            // 외부 흡수 효과(황금사과 등)가 캡을 이미 채우거나 넘긴 경우 충전 안 함 — 외부 효과 보호.
            if (now - lastGrant >= intervalMs && player.getAbsorptionAmount() < capInternal) {
                // 1틱당 +1칸 (= 마크 내부 +2.0). 단, 캡을 절대 넘지 않게 클램프.
                player.setAbsorptionAmount(Math.min(capInternal, player.getAbsorptionAmount() + 2.0));
                lastGoldenHeart.put(player.getUniqueId(), now);
            }
        }

        // 빠른 회복 (SET) — 부위 카운트 = level
        int frLevel = SetLevelCounter.countOnArmor(player, kFastRecovery, storage);
        if (frLevel > 0 && player.getAttribute(Attribute.MAX_HEALTH) != null) {
            double base = ec.readDouble("fast_recovery", "base", 12.0);
            double perLevel = ec.readDouble("fast_recovery", "per_level", 2.0);
            double delaySec = Math.max(1.0, base - perLevel * frLevel);
            long delayMs = (long) (delaySec * 1000L);
            long lastDmg = damageTracker.getLastDamage(player.getUniqueId());
            if (now - lastDmg >= delayMs) {
                double maxHp = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                if (player.getHealth() < maxHp) {
                    player.setHealth(Math.min(maxHp, player.getHealth() + 0.5));
                }
            }
        }

        // 포만 유지 (SET) — 부위 카운트 = level. 공복의 분노 시너지: 허기를 낮게 캡.
        // cap = (base - per × level) 칸. 4부위 = 1칸 캡. food = cap × 2.
        int skLevel = SetLevelCounter.countOnArmor(player, kSaturationKeeper, storage);
        if (skLevel > 0) {
            if (skLevel >= 5) {
                // L5: 허기 1칸(food=2) 강제 고정. 음식·exhaustion 으로 변화 안 함.
                if (player.getFoodLevel() != 2) {
                    player.setFoodLevel(2);
                }
                player.setSaturation(0.0f);
                player.setExhaustion(0.0f);  // 누적 exhaustion 도 매 tick 리셋
            } else {
                double base = ec.readDouble("saturation_keeper", "base", 5.0);
                double perLevel = ec.readDouble("saturation_keeper", "per_level", 1.0);
                int capBars = Math.max(0, (int) Math.round(base - perLevel * skLevel));
                int capFood = capBars * 2;
                if (player.getFoodLevel() > capFood) {
                    player.setFoodLevel(capFood);
                    player.setSaturation(0.0f);  // 포만도(saturation)도 즉시 0 — 음식 효과 무력화
                }
            }
        }

        // 아누비스 세트 — 부위별 buff 목록 집계. 같은 buff 등장 횟수 = 레벨.
        // 한 부위가 여러 buff 가질 수 있음 (50/30/20 확률). 다른 부위의 같은 buff 와 합산.
        EnumMap<AnubisArmorItem.Buff, Integer> buffCounts = new EnumMap<>(AnubisArmorItem.Buff.class);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || !armor.hasItemMeta()) continue;
            var meta = armor.getItemMeta();
            String t = meta.getPersistentDataContainer().get(armorKeys.type, PersistentDataType.STRING);
            if (!AnubisArmorItem.HELMET_TYPE_ID.equals(t)
                    && !AnubisArmorItem.LEGGINGS_TYPE_ID.equals(t)
                    && !AnubisArmorItem.BOOTS_TYPE_ID.equals(t)) continue;
            String buffStr = meta.getPersistentDataContainer().get(armorKeys.anubisBuff, PersistentDataType.STRING);
            if (buffStr == null || buffStr.isEmpty()) continue;
            for (String token : buffStr.split(",")) {
                AnubisArmorItem.Buff buff = AnubisArmorItem.Buff.fromString(token.trim());
                if (buff != null) buffCounts.merge(buff, 1, Integer::sum);
            }
        }
        for (var entry : buffCounts.entrySet()) {
            int amplifier = entry.getValue() - 1;  // Lv1 = amplifier 0
            // duration 40틱 (2초) — 1초 주기 task 라 갱신 보장. ambient=true, particles=false, icon=true.
            player.addPotionEffect(new PotionEffect(entry.getKey().effect, 40, amplifier, true, false, true));
        }
    }

    public void clearPlayer(UUID uuid) {
        lastGoldenHeart.remove(uuid);
    }
}
