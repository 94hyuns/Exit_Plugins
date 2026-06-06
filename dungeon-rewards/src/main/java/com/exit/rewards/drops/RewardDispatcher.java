package com.exit.rewards.drops;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.rewards.DungeonRewardsPlugin;
import com.exit.rewards.config.RewardConfig;
import com.exit.rewards.model.MobReward;
import com.exit.rewards.model.RewardItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 몹 사망 이벤트를 받아 config에 맞는 보상(돈/드롭)을 실제로 지급.
 */
public class RewardDispatcher {

    private final DungeonRewardsPlugin plugin;
    private final RewardConfig config;

    public RewardDispatcher(DungeonRewardsPlugin plugin, RewardConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * 몹 사망 시 호출. killer가 null이면 환경 사망이라 보상 지급 안 함.
     */
    public void dispatch(String mobId, Player killer, Location deathLoc) {
        MobReward reward = config.getReward(mobId);
        if (reward == null) return;  // 보상 정의 없는 몹
        if (killer == null) return;  // 플레이어가 죽인 게 아님

        // 돈 지급
        long money = reward.rollMoney();
        if (money > 0) {
            EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
            if (eco != null) {
                eco.addBalance(killer.getUniqueId(), money);
            } else {
                plugin.getLogger().warning("[DungeonRewards] EconomyProvider 미등록.");
            }
        }

        // 처치 메시지
        if (reward.messageOnKill() != null && !reward.messageOnKill().isBlank()) {
            String text = reward.messageOnKill().replace("{money}", String.valueOf(money));
            Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
            killer.sendMessage(msg);
        }

        // 아이템 드롭
        for (RewardItem item : reward.drops()) {
            ItemStack stack = item.roll();
            if (stack == null) continue;

            switch (config.getDropMode()) {
                case DROP -> deathLoc.getWorld().dropItemNaturally(deathLoc, stack);
                case GIVE -> {
                    var overflow = killer.getInventory().addItem(stack);
                    // 인벤 꽉 찼으면 발밑에 떨어뜨림
                    overflow.values().forEach(left ->
                            killer.getWorld().dropItemNaturally(killer.getLocation(), left));
                }
            }
        }
    }
}
