package com.exit.customitems.lamp.enchant.listener;

import com.exit.customitems.lamp.enchant.EnchantDispatcher;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AutoCastEnchant;
import com.exit.customitems.lamp.enchant.impl.life.AutoReelEnchant;
import com.exit.customitems.lamp.enchant.impl.life.ExpBoostEnchant;
import com.exit.customitems.util.RollUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 오토릴 인챈트. State.BITE 이벤트 시점에 레벨별 딜레이 후 자동 리트리브.
 *
 * <p>동작:
 * <ol>
 *   <li>BITE 순간, 플레이어 손의 낚싯대에 오토릴이 붙어있는지 체크</li>
 *   <li>레벨에 따른 딜레이(5/3/1초) 후 스케줄러로 아래 실행:</li>
 *   <li>바닐라 낚시 루트 테이블(FISHING)에서 아이템 뽑아 플레이어 방향으로 드롭</li>
 *   <li>경험치 1~6 드롭, 낚싯대 내구도 1 감소 (Unbreaking 고려), 사운드 재생, 훅 제거</li>
 * </ol>
 *
 * <p>중간에 플레이어가 수동 리트리브하면 훅이 먼저 사라지므로
 * 스케줄러 실행 시점의 {@code hook.isValid()} 체크로 중복 방지.
 */
public class FishingListener implements Listener {

    private final Plugin plugin;
    private final EnchantDispatcher dispatcher;
    private final NamespacedKey autoReelKey;
    private final NamespacedKey autoCastKey;
    private final NamespacedKey expBoostKey;

    public FishingListener(Plugin plugin, EnchantDispatcher dispatcher) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.autoReelKey = AutoReelEnchant.keyOf(plugin);
        this.autoCastKey = AutoCastEnchant.keyOf(plugin);
        this.expBoostKey = ExpBoostEnchant.keyOf(plugin);
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        PlayerFishEvent.State state = event.getState();
        Player player = event.getPlayer();

        if (state == PlayerFishEvent.State.BITE) {
            RodSlot slot = findRod(player);
            if (slot == null) return;

            Optional<RolledEnchant> reel = dispatcher.findInItem(slot.rod, autoReelKey);
            if (reel.isEmpty()) return;

            int level = RollUtil.asCount(reel.get().values()[0]);
            int delayTicks = AutoReelEnchant.levelToDelayTicks(level);

            FishHook hook = event.getHook();
            // reelLv 를 람다 캡처해서 autoReel 로 전달 (사이즈 티어 계산용)
            final int reelLv = level;
            Bukkit.getScheduler().runTaskLater(plugin, () -> autoReel(player, hook, reelLv), delayTicks);
            return;
        }

        // 경험치 획득량 — 낚시 성공 시 1회 발동 (CAUGHT_FISH 만)
        if (state == PlayerFishEvent.State.CAUGHT_FISH) {
            ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, player.getLocation());
        }

        // 오토캐스트: CAUGHT_FISH / FAILED_ATTEMPT / IN_GROUND 등 훅이 정리될 시점에
        // 다시 자동으로 낚싯대를 던진다. (오토릴이 hook.remove() 한 후에는
        // 이벤트가 추가로 발생하지 않으므로, 오토릴 흐름 내에서도 별도로 트리거 필요)
        if (state == PlayerFishEvent.State.CAUGHT_FISH
                || state == PlayerFishEvent.State.CAUGHT_ENTITY
                || state == PlayerFishEvent.State.FAILED_ATTEMPT
                || state == PlayerFishEvent.State.IN_GROUND
                || state == PlayerFishEvent.State.REEL_IN) {
            tryScheduleAutoCast(player);
        }
    }

    private void tryScheduleAutoCast(Player player) {
        RodSlot slot = findRod(player);
        if (slot == null) {
            if (debugAutoCast) plugin.getLogger().info("[AutoCast] " + player.getName() + " no rod in hand at trigger");
            return;
        }

        Optional<RolledEnchant> cast = dispatcher.findInItem(slot.rod, autoCastKey);
        if (cast.isEmpty()) {
            if (debugAutoCast) plugin.getLogger().info("[AutoCast] " + player.getName() + " rod has no autocast enchant");
            return;
        }

        int level = RollUtil.asCount(cast.get().values()[0]);
        int delayTicks = AutoCastEnchant.levelToDelayTicks(level);
        if (debugAutoCast) plugin.getLogger().info("[AutoCast] scheduling for " + player.getName() + " lv" + level + " in " + delayTicks + "t");

        Bukkit.getScheduler().runTaskLater(plugin, () -> autoCast(player), delayTicks);
    }

    private void autoCast(Player player) {
        if (!player.isOnline()) return;
        RodSlot slot = findRod(player);
        if (slot == null) {
            if (debugAutoCast) plugin.getLogger().info("[AutoCast] " + player.getName() + " no rod at execution time");
            return;
        }

        // 이미 활성 훅이 있으면 (수동 캐스팅함) 무시
        for (FishHook fh : player.getWorld().getEntitiesByClass(FishHook.class)) {
            if (!fh.isValid() || fh.isDead()) continue;
            if (fh.getShooter() instanceof Player owner
                    && owner.getUniqueId().equals(player.getUniqueId())) {
                if (debugAutoCast) plugin.getLogger().info("[AutoCast] " + player.getName() + " already has active hook, skip");
                return;
            }
        }

        // Paper 1.21.x 는 Bukkit World.spawn(FishHook.class) 를 명시적으로 거부 (IllegalArgumentException).
        // 따라서 NMS 리플렉션으로 진짜 vanilla FishingHook(Player, Level, luck, lure) 생성자를 호출해야 함.
        // 이 생성자는 player.fishing 필드 + 회전 기반 초기 속도까지 한 번에 세팅함.
        boolean ok = nmsCast(player);
        if (!ok) {
            // NMS 실패 시 안전하게 종료 (Bukkit 폴백은 1.21.x 에서 무조건 예외 발생)
            return;
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f,
                0.4f / (ThreadLocalRandom.current().nextFloat() * 0.4f + 0.8f));

        // 던질 때도 내구도 살짝 (vanilla 는 cast 자체로 내구도 1 소모)
        damageRod(slot.rod);
    }

    /** [debug] 임시 로그 토글. 안정화 후 false 로. */
    private static final boolean debugAutoCast = true;

    /** NMS 리플렉션으로 진짜 vanilla FishingHook 캐스트. 실패 시 false. */
    private boolean nmsCast(Player player) {
        try {
            // 1. CraftPlayer → ServerPlayer
            Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
            // 2. CraftWorld → ServerLevel (Paper API stable: CraftWorld.getHandle() 항상 존재)
            Object craftWorld = player.getWorld();
            Object serverLevel = craftWorld.getClass().getMethod("getHandle").invoke(craftWorld);

            // 3. FishingHook 클래스 + Level + Player NMS 클래스 찾기
            Class<?> fishingHookClass = Class.forName("net.minecraft.world.entity.projectile.FishingHook");
            Class<?> playerNmsClass = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
            // 4. constructor: (Player, Level, int, int)  — luck, lure 0 으로
            Object hook = fishingHookClass
                    .getConstructor(playerNmsClass, levelClass, int.class, int.class)
                    .newInstance(serverPlayer, serverLevel, 0, 0);
            // 5. serverLevel.addFreshEntity(hook) — Level 또는 ServerLevel 어디든 있음
            Class<?> entityClass = Class.forName("net.minecraft.world.entity.Entity");
            serverLevel.getClass().getMethod("addFreshEntity", entityClass).invoke(serverLevel, hook);

            if (debugAutoCast) plugin.getLogger().info("[AutoCast] NMS spawn OK for " + player.getName());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[AutoCast] NMS reflection failed: "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
            return false;
        }
    }

    private void autoReel(Player player, FishHook hook, int reelLv) {
        if (!hook.isValid() || hook.isDead()) return;
        if (!player.isOnline()) { hook.remove(); return; }

        // 딜레이 중 플레이어가 낚싯대를 놓거나 바꿨을 수도 있음
        RodSlot slot = findRod(player);
        if (slot == null) { hook.remove(); return; }

        // === 잡힐 물고기 결정 ===
        // 사이즈 티어: 오토릴 레벨 + 오토캐스트 레벨 (없으면 0). cap 4.
        int castLv = dispatcher.findInItem(slot.rod, autoCastKey)
                .map(e -> RollUtil.asCount(e.values()[0]))
                .orElse(0);
        int maxTier = Math.min(4, reelLv + castLv);
        if (debugAutoCast) plugin.getLogger().info("[AutoReel] " + player.getName()
                + " reelLv=" + reelLv + " castLv=" + castLv + " maxTier=" + maxTier);

        Collection<ItemStack> loot = new java.util.ArrayList<>();

        // 1순위: Fishing 플러그인의 FishProvider 가 있으면 커스텀 물고기 생성
        com.exit.core.api.FishProvider fishProvider = com.exit.core.registry.ServiceRegistry
                .get(com.exit.core.api.FishProvider.class).orElse(null);
        ItemStack customFish = null;
        if (fishProvider != null) {
            customFish = fishProvider.rollFish(maxTier);
            if (customFish != null) {
                loot.add(customFish);
                if (debugAutoCast) plugin.getLogger().info("[AutoReel] custom fish rolled OK");
            } else {
                plugin.getLogger().warning("[AutoReel] FishProvider.rollFish returned null — fishing 풀 비어있음?");
            }
        } else {
            plugin.getLogger().warning("[AutoReel] FishProvider 미등록 → vanilla 폴백 사용. fishing 플러그인이 최신 버전인지 확인.");
            // 폴백: Fishing 플러그인 없으면 vanilla 루트 테이블
            LootTable table = LootTables.FISHING.getLootTable();
            if (table != null) {
                int luck = slot.rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
                LootContext context = new LootContext.Builder(hook.getLocation())
                        .killer(player)
                        .lootedEntity(hook)
                        .luck((float) luck)
                        .build();
                try {
                    loot.addAll(table.populateLoot(new Random(), context));
                } catch (Exception e) {
                    plugin.getLogger().warning("오토릴: 낚시 루트 테이블 조회 실패 - " + e.getMessage());
                    hook.remove();
                    return;
                }
            }
        }

        Location hookLoc = hook.getLocation();
        Location playerLoc = player.getLocation();

        for (ItemStack item : loot) {
            if (item == null || item.getType().isAir()) continue;
            Item dropped = hook.getWorld().dropItem(hookLoc, item);
            dropped.setVelocity(velocityToPlayer(hookLoc, playerLoc));
            dropped.setPickupDelay(5);
        }

        // 커스텀 물고기 잡았을 때 fishing 플러그인 포맷의 채팅 메시지 (cm/g/등급)
        if (customFish != null && fishProvider != null) {
            fishProvider.sendCatchMessage(player, customFish);
        }

        // Job 어부 EXP 위임 — premium 여부 검사
        try {
            boolean premium = false;
            if (customFish != null) {
                try { premium = com.exit.fishing.item.FishItem.isPremium(customFish); }
                catch (NoClassDefFoundError ignored) { /* fishing 플러그인 없음 — false 유지 */ }
            }
            final boolean p = premium;
            com.exit.core.registry.ServiceRegistry.get(com.exit.job.api.FishingExpHook.class)
                    .ifPresent(hook2 -> hook2.grantCatch(player.getUniqueId(), p));
        } catch (NoClassDefFoundError ignored) {
            // Job 플러그인 미설치 — 무시
        }

        // 경험치
        int exp = RollUtil.range(1, 6);
        hook.getWorld().spawn(playerLoc, ExperienceOrb.class, orb -> orb.setExperience(exp));

        // 경험치 획득량 — 오토릴은 PlayerFishEvent 를 우회하므로 직접 호출
        ExpBoostEnchant.maybeDropExpOrbs(dispatcher, expBoostKey, player, playerLoc);

        // 내구도 감소
        damageRod(slot.rod);

        // 사운드
        hook.getWorld().playSound(hookLoc, Sound.ENTITY_FISHING_BOBBER_RETRIEVE,
            1.0f, 0.4f + (float) Math.random() * 0.4f);

        hook.remove();

        // 오토릴 회수 후에도 오토캐스트 발동 (PlayerFishEvent 가 발생하지 않으므로 직접 호출)
        tryScheduleAutoCast(player);
    }

    private Vector velocityToPlayer(Location from, Location to) {
        Vector v = to.toVector().subtract(from.toVector());
        double dist = v.length();
        if (dist <= 0.001) return new Vector(0, 0.2, 0);
        v.multiply(0.1);
        v.setY(v.getY() + Math.sqrt(dist) * 0.08);
        return v;
    }

    private void damageRod(ItemStack rod) {
        if (!(rod.getItemMeta() instanceof Damageable dmg)) return;
        int unbreaking = rod.getEnchantmentLevel(Enchantment.UNBREAKING);
        // 바닐라 Unbreaking 근사: (1 / (unbreaking + 1)) 확률로 내구 소모
        if (ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) return;

        int newDamage = dmg.getDamage() + 1;
        short maxDur = rod.getType().getMaxDurability();
        if (maxDur > 0 && newDamage >= maxDur) {
            rod.setAmount(0);
            return;
        }
        dmg.setDamage(newDamage);
        rod.setItemMeta(dmg);
    }

    /** 플레이어 양손에서 낚싯대 검색. */
    private RodSlot findRod(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.FISHING_ROD) {
            return new RodSlot(main, EquipmentSlot.HAND);
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == Material.FISHING_ROD) {
            return new RodSlot(off, EquipmentSlot.OFF_HAND);
        }
        return null;
    }

    private record RodSlot(ItemStack rod, EquipmentSlot slot) {}
}
