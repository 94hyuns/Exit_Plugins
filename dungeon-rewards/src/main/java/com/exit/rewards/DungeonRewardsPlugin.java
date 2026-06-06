package com.exit.rewards;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.rewards.config.RewardConfig;
import com.exit.rewards.drops.PlayerDamageTracker;
import com.exit.rewards.drops.RewardDispatcher;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class DungeonRewardsPlugin extends JavaPlugin implements Listener {

    private RewardConfig rewardConfig;
    private RewardDispatcher dispatcher;
    private PlayerDamageTracker damageTracker;

    @Override
    public void onEnable() {
        rewardConfig = new RewardConfig(this);
        rewardConfig.load();

        dispatcher = new RewardDispatcher(this, rewardConfig);
        damageTracker = new PlayerDamageTracker();

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(damageTracker, this);

        getLogger().info("DungeonRewards 플러그인 활성화됨.");
    }

    @Override
    public void onDisable() {
        getLogger().info("DungeonRewards 플러그인 비활성화됨.");
    }

    /**
     * MythicMobs 커스텀 몹 사망 이벤트.
     *
     * killall / auto-cleanup / 시간만료 despawn 등으로 죽인 경우는 보상 X.
     * 오직 "사망 직전 250ms 안에 player 가 데미지를 입혔다"만 보상 발급.
     *
     * Bukkit 의 getKiller() / lastDamageCause 는 5초 동안 마지막 player 데미지를
     * 캐시하므로 그것만 보면 cleanup 으로 죽여도 player kill 로 잘못 판정됨.
     * PlayerDamageTracker 가 직접 시각 추적으로 좁은 윈도우 검증.
     */
    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        String mobId = event.getMobType().getInternalName();
        var entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity living)) return;
        if (!living.isDead()) return;

        if (!damageTracker.wasKilledByPlayer(entity.getUniqueId())) return;
        if (!(event.getKiller() instanceof Player killer)) return;

        dispatcher.dispatch(mobId, killer, entity.getLocation());
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("dungeonrewards")) return false;

        if (args.length == 0) {
            sender.sendMessage(Component.text("/dungeonrewards <reload|list>").color(NamedTextColor.YELLOW));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                rewardConfig.load();
                sender.sendMessage(Component.text("[DungeonRewards] 설정 재로드 완료. "
                        + rewardConfig.allRewards().size() + "개 몹 보상 등록됨.")
                        .color(NamedTextColor.GREEN));
            }
            case "list" -> {
                sender.sendMessage(Component.text("등록된 몹 보상:").color(NamedTextColor.GOLD));
                rewardConfig.allRewards().forEach(r ->
                        sender.sendMessage(Component.text("  - " + r.mobId()
                                        + " (돈: " + r.minMoney() + "~" + r.maxMoney()
                                        + ", 드롭: " + r.drops().size() + "종)")
                                .color(NamedTextColor.WHITE))
                );
            }
            default -> sender.sendMessage(Component.text("알 수 없는 하위 명령어: " + args[0])
                    .color(NamedTextColor.RED));
        }
        return true;
    }

    public RewardConfig getRewardConfig() {
        return rewardConfig;
    }

    /** Core의 EconomyProvider 조회 편의 메서드 */
    public EconomyProvider getEconomy() {
        return ServiceRegistry.get(EconomyProvider.class)
                .orElseThrow(() -> new IllegalStateException("EconomyProvider 미등록. Economy 플러그인 확인."));
    }
}
