package com.exit.world.boss;

import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import com.exit.world.manager.WorldConfig;
import com.exit.world.manager.WorldManager;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * 보스 아레나 시스템 오케스트레이터.
 *
 * 책임:
 * - 플레이어 입장 시 첫 사람이면 COUNTDOWN 시작 → 보스 spawn → COMBAT
 * - 보스 사망 시 보상 분배 (월드 내 플레이어 균등) → LOCKOUT 10초 → IDLE (단 cooldown 활성)
 * - 월드 전원 퇴장 시 (COMBAT 중) 보스 despawn → LOCKOUT 10초 → IDLE
 * - 관리자 명령으로 cooldown 즉시 해제
 *
 * 1.21+ MythicMobs API: MythicBukkit.inst() 진입점.
 */
public class BossArenaManager {

    private static final Random RNG = new Random();

    private final JavaPlugin plugin;
    private final WorldManager worldManager;
    private final Map<String, BossArenaInstance> arenas = new HashMap<>();
    /** worldName → arenaKey 역인덱스 */
    private final Map<String, String> worldToArena = new HashMap<>();

    public BossArenaManager(JavaPlugin plugin, WorldManager worldManager) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        // cooldown 종료 시점 자동 spawn 폴링 — 5초마다 모든 IDLE 아레나 점검
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCooldownChecks, 100L, 100L);
    }

    public void register(BossArenaConfig cfg) {
        arenas.put(cfg.arenaKey(), new BossArenaInstance(cfg));
        worldToArena.put(cfg.worldName(), cfg.arenaKey());
        plugin.getLogger().info("[BossArena] 등록: " + cfg.arenaKey()
                + " (world=" + cfg.worldName() + ", boss=" + cfg.bossMobName() + ")");
    }

    public Optional<BossArenaInstance> byKey(String key) {
        return Optional.ofNullable(arenas.get(key));
    }

    public java.util.Collection<BossArenaInstance> allInstances() {
        return arenas.values();
    }

    /** 엔티티 UUID 가 추적 중인 보스인지 확인 후 해당 인스턴스 반환. */
    public Optional<BossArenaInstance> findByBossUuid(java.util.UUID uuid) {
        for (BossArenaInstance inst : arenas.values()) {
            if (uuid.equals(inst.bossUuid())) return Optional.of(inst);
        }
        return Optional.empty();
    }

    public Optional<BossArenaInstance> byWorld(String worldName) {
        String key = worldToArena.get(worldName);
        return key == null ? Optional.empty() : Optional.ofNullable(arenas.get(key));
    }

    /** 플레이어가 이 아레나로 입장 가능한가? + 거절 메시지(있다면) */
    public EntryCheck canEnter(String arenaKey) {
        BossArenaInstance inst = arenas.get(arenaKey);
        if (inst == null) return EntryCheck.allow();
        long now = System.currentTimeMillis();
        switch (inst.state()) {
            case COMBAT -> {
                return EntryCheck.deny("전투 중인 보스 월드에는 입장할 수 없습니다.");
            }
            case LOCKOUT -> {
                long sec = Math.max(0, (inst.lockoutUntilMs() - now) / 1000);
                return EntryCheck.deny("보스 월드 잠금 중입니다. " + (sec + 1) + "초 후 다시 시도하세요.");
            }
            default -> { return EntryCheck.allow(); }
        }
    }

    /**
     * 플레이어가 보스 월드로 텔레포트된 직후 호출.
     * 첫 입장이면 카운트다운 / 보스 소환 트리거.
     */
    public void onPlayerEntered(Player player, BossArenaInstance inst) {
        if (inst.state() != BossArenaInstance.State.IDLE) return;
        long now = System.currentTimeMillis();
        // cooldown 이 안 끝났으면 아무것도 안 함 (플레이어는 빈 월드 자유 탐색)
        if (!inst.canSpawnBoss(now)) {
            long rem = inst.secondsUntilSpawnReady(now);
            player.sendMessage(Component.text("보스 재소환까지 " + rem + "초 대기.")
                    .color(NamedTextColor.GRAY));
            return;
        }
        // 첫 사람 → N초 카운트다운 후 spawn
        if (countPlayersInWorld(inst.config().worldName()) == 1) {
            startCountdown(inst, inst.config().countdownSec());
        }
    }

    /**
     * N초 카운트다운 후 보스 spawn. T-5..T-1 메시지, T=0 spawn → COMBAT.
     */
    private void startCountdown(BossArenaInstance inst, int seconds) {
        inst.setState(BossArenaInstance.State.COUNTDOWN);
        broadcastWorld(inst.config().worldName(),
                "&b" + seconds + "초 후 보스가 등장합니다.");

        var task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = seconds;
            @Override
            public void run() {
                if (inst.state() != BossArenaInstance.State.COUNTDOWN) {
                    inst.cancelCountdown();
                    return;
                }
                remaining--;
                if (remaining > 0 && remaining <= 5) {
                    broadcastWorld(inst.config().worldName(),
                            "&c" + remaining + "초 후 보스 등장!");
                } else if (remaining <= 0) {
                    inst.cancelCountdown();
                    Entity spawned = spawnBoss(inst);
                    if (spawned == null) {
                        // spawn 실패 → 상태 IDLE 로 복귀
                        inst.setState(BossArenaInstance.State.IDLE);
                        broadcastWorld(inst.config().worldName(), "&c보스 소환 실패. 관리자에게 문의.");
                        return;
                    }
                    inst.setState(BossArenaInstance.State.COMBAT);
                    broadcastWorld(inst.config().worldName(), "&4보스가 등장했다!");
                }
            }
        }, 20L, 20L);
        inst.setCountdownTask(task);
    }

    /**
     * 실제 spawn 호출. spawn 위치 + 플레이어 spawn 방향 정조준 + bossUuid 등록.
     * 성공 시 Entity 반환, 실패 시 null.
     */
    private Entity spawnBoss(BossArenaInstance inst) {
        World world = Bukkit.getWorld(inst.config().worldName());
        if (world == null) {
            plugin.getLogger().warning("[BossArena] " + inst.config().arenaKey()
                    + ": 월드 미로드, spawn 취소");
            return null;
        }
        // 보스 위치 + 플레이어 spawn 정조준 yaw/pitch 계산
        Location loc = new Location(world,
                inst.config().bossX(), inst.config().bossY(), inst.config().bossZ());
        WorldConfig wc = worldManager.getWorldConfig(world);
        if (wc != null) {
            Vector face = new Vector(wc.getSpawnX() - loc.getX(),
                                     wc.getSpawnY() - loc.getY(),
                                     wc.getSpawnZ() - loc.getZ());
            if (face.lengthSquared() > 0.0001) {
                loc.setDirection(face);
            }
        }

        MythicBukkit mm = mythic();
        if (mm == null) {
            plugin.getLogger().warning("[BossArena] MythicMobs 미설치 — 보스 소환 불가");
            return null;
        }
        Optional<MythicMob> mobType = mm.getMobManager().getMythicMob(inst.config().bossMobName());
        if (mobType.isEmpty()) {
            plugin.getLogger().warning("[BossArena] MythicMob '" + inst.config().bossMobName()
                    + "' 미정의 — spawn 실패");
            return null;
        }
        ActiveMob active = mobType.get().spawn(io.lumine.mythic.bukkit.BukkitAdapter.adapt(loc), 1.0);
        if (active == null) {
            plugin.getLogger().warning("[BossArena] 보스 spawn 실패");
            return null;
        }
        Entity entity = active.getEntity().getBukkitEntity();
        inst.setBossUuid(entity.getUniqueId());
        // yaw 강제 적용은 5틱 뒤 (ModelEngine model{} ~onSpawn 1 이 먼저 attach 되도록).
        // 즉시 teleport 하면 model 적용 전에 entity 가 클라에 재전송되어 스킨 깨짐.
        final UUID yawFixUuid = entity.getUniqueId();
        final Location finalLoc = loc.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity e = Bukkit.getEntity(yawFixUuid);
            if (e != null && !e.isDead()) {
                e.setRotation(finalLoc.getYaw(), finalLoc.getPitch());
            }
        }, 5L);
        plugin.getLogger().info("[BossArena] " + inst.config().arenaKey()
                + " 보스 spawn @ " + loc.getX() + "," + loc.getY() + "," + loc.getZ()
                + " yaw=" + loc.getYaw() + " uuid=" + entity.getUniqueId());

        // 1초 후 헬스체크 — spawn 됐는데 즉시 사라지는 케이스 진단용
        final UUID expectedUuid = entity.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity check = Bukkit.getEntity(expectedUuid);
            if (check == null || check.isDead()) {
                plugin.getLogger().warning("[BossArena] " + inst.config().arenaKey()
                        + " 보스가 1초 내에 사라짐 (좌표 안에 블록? 보호월드 정책?). "
                        + "현재 위치: " + (check == null ? "사라짐" : check.getLocation()));
                // 상태를 IDLE 로 되돌려 재시도 가능하게
                inst.setBossUuid(null);
                inst.setState(BossArenaInstance.State.IDLE);
            } else {
                plugin.getLogger().info("[BossArena] " + inst.config().arenaKey()
                        + " 보스 생존 확인 @ " + check.getLocation()
                        + " HP=" + (check instanceof org.bukkit.entity.LivingEntity le
                                ? le.getHealth() + "/" + le.getMaxHealth() : "n/a"));
            }
        }, 20L);
        return entity;
    }

    /**
     * MythicMobDeathEvent 에서 호출. 이 아레나의 보스가 죽었는지 확인 후 처리.
     */
    public void onMythicMobDeath(Entity bossEntity, Player killer) {
        if (bossEntity == null) return;
        BossArenaInstance inst = null;
        for (BossArenaInstance candidate : arenas.values()) {
            if (bossEntity.getUniqueId().equals(candidate.bossUuid())) {
                inst = candidate;
                break;
            }
        }
        if (inst == null) return; // 우리가 추적 중인 보스 아님
        final BossArenaInstance arena = inst;

        long now = System.currentTimeMillis();
        arena.markKill(now);
        arena.setBossUuid(null);
        arena.setState(BossArenaInstance.State.LOCKOUT);
        arena.setLockoutUntil(now + arena.config().graceSec() * 1000L);

        distributeReward(arena, bossEntity.getWorld());

        // graceSec 후 LOCKOUT → IDLE (입장 다시 허용)
        var release = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arena.state() == BossArenaInstance.State.LOCKOUT) {
                arena.setState(BossArenaInstance.State.IDLE);
                broadcastWorld(arena.config().worldName(),
                        "&7월드 입장이 다시 활성화되었습니다.");
            }
        }, arena.config().graceSec() * 20L);
        arena.setLockoutReleaseTask(release);

        broadcastWorld(arena.config().worldName(),
                "&6보스 처치 완료! 30초 후 마을로 자동 귀환 / 다음 보스 등장까지 "
                + arena.config().cooldownSec() + "초.");

        // 보스 사망 30초 후 월드 내 모든 플레이어 강제 마을 TP
        // (추가 인원 난입 차단을 위해 lockout 60초와 함께 동작 — 잔류 플레이어 청소)
        final String worldName = arena.config().worldName();
        final int autoTpSec = 30;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return;
            for (Player p : w.getPlayers()) {
                p.sendMessage(Component.text("⏱ 보스 처치 30초 경과 — 마을로 이동합니다.")
                        .color(NamedTextColor.YELLOW));
                worldManager.teleportPlayer(p, "village");
            }
        }, autoTpSec * 20L);
    }

    /**
     * 5초 polling — IDLE 상태에서 cooldown 이 막 끝났고 월드에 플레이어가 남아 있으면
     * 자동으로 카운트다운 시작 → 보스 재소환. 플레이어 입장 이벤트 없이도 동작.
     *
     * <p>(처음 입장 케이스는 onPlayerEntered 가 담당. 여기는 보스 사망 후 cooldown 대기 케이스만.)
     */
    private void tickCooldownChecks() {
        long now = System.currentTimeMillis();
        for (BossArenaInstance inst : arenas.values()) {
            if (inst.state() != BossArenaInstance.State.IDLE) continue;
            if (inst.lastKillTimeMs() < 0) continue;  // 한 번도 안 죽음 — 처음 입장 흐름에 맡김
            if (!inst.canSpawnBoss(now)) continue;     // cooldown 미만료
            if (countPlayersInWorld(inst.config().worldName()) <= 0) continue;
            broadcastWorld(inst.config().worldName(), "&b보스 재소환 카운트다운 시작.");
            startCountdown(inst, inst.config().countdownSec());
        }
    }

    private void distributeReward(BossArenaInstance inst, World deathWorld) {
        int min = inst.config().killRewardMin();
        int max = inst.config().killRewardMax();
        if (max <= 0) return;
        int amount = (min >= max) ? min : min + RNG.nextInt(max - min + 1);

        List<Player> players = deathWorld.getPlayers();
        if (players.isEmpty()) return;

        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            plugin.getLogger().warning("[BossArena] EconomyProvider 미등록 — 보상 분배 스킵");
            return;
        }

        // 균등 분배, 소수점 반올림
        long share = Math.round((double) amount / players.size());
        for (Player p : players) {
            eco.addBalance(p.getUniqueId(), share);
            p.sendMessage(Component.text("[+" + String.format("%,d", share) + "w] 보스 처치 보상")
                    .color(NamedTextColor.GOLD));
        }
        plugin.getLogger().info("[BossArena] " + inst.config().arenaKey()
                + " 보상 분배: " + share + "w × " + players.size() + "명 (총 " + amount + "w)");
    }

    /**
     * 플레이어가 월드를 떠났을 때 호출. 빈 월드면 + COMBAT 중이면 보스 despawn + LOCKOUT.
     */
    public void onPlayerLeft(String worldName) {
        BossArenaInstance inst = arenas.get(worldToArena.get(worldName));
        if (inst == null) return;
        long now = System.currentTimeMillis();

        int remaining = countPlayersInWorld(worldName);
        if (remaining > 0) return;

        // 월드 비었음
        if (inst.state() == BossArenaInstance.State.COMBAT) {
            // 보스 despawn
            if (inst.bossUuid() != null) {
                Entity e = Bukkit.getEntity(inst.bossUuid());
                if (e != null) e.remove();
                inst.setBossUuid(null);
            }
            inst.setState(BossArenaInstance.State.LOCKOUT);
            inst.setLockoutUntil(now + inst.config().graceSec() * 1000L);
            plugin.getLogger().info("[BossArena] " + inst.config().arenaKey()
                    + " 와이프 (월드 비움) → 10초 잠금");

            var release = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (inst.state() == BossArenaInstance.State.LOCKOUT) {
                    inst.setState(BossArenaInstance.State.IDLE);
                }
            }, inst.config().graceSec() * 20L);
            inst.setLockoutReleaseTask(release);
        } else if (inst.state() == BossArenaInstance.State.COUNTDOWN) {
            // 카운트다운 중 전원 퇴장 → 취소 후 IDLE
            inst.cancelCountdown();
            inst.setState(BossArenaInstance.State.IDLE);
            plugin.getLogger().info("[BossArena] " + inst.config().arenaKey()
                    + " 카운트다운 도중 전원 퇴장 → 취소");
        }
    }

    /**
     * 관리자: cooldown 즉시 해제 + 10초 후 보스 강제 소환 (월드에 플레이어 있어야 함).
     * 플레이어 없으면 cooldown 만 해제하고 첫 입장 시 정규 30초 카운트다운으로 진입.
     */
    public boolean adminResetCooldown(String arenaKey) {
        BossArenaInstance inst = arenas.get(arenaKey);
        if (inst == null) return false;
        inst.resetKill();
        // LOCKOUT 중이면 즉시 풀어주고 IDLE 로
        if (inst.state() == BossArenaInstance.State.LOCKOUT) {
            if (inst.bossUuid() != null) {
                Entity e = Bukkit.getEntity(inst.bossUuid());
                if (e != null && !e.isDead()) e.remove();
                inst.setBossUuid(null);
            }
            inst.setState(BossArenaInstance.State.IDLE);
        }
        // COMBAT 중이면 기존 보스 제거 후 재진입
        if (inst.state() == BossArenaInstance.State.COMBAT) {
            if (inst.bossUuid() != null) {
                Entity e = Bukkit.getEntity(inst.bossUuid());
                if (e != null && !e.isDead()) e.remove();
                inst.setBossUuid(null);
            }
            inst.setState(BossArenaInstance.State.IDLE);
        }
        // 빈 월드면 끝. 플레이어 있으면 10초 카운트다운 → spawn
        if (inst.state() == BossArenaInstance.State.IDLE
                && countPlayersInWorld(inst.config().worldName()) > 0) {
            broadcastWorld(inst.config().worldName(), "&b[관리자] 10초 후 보스 강제 소환");
            startCountdown(inst, 10);
        }
        return true;
    }

    // ── 유틸 ────────────────────────────────────────────────

    private int countPlayersInWorld(String worldName) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return 0;
        return w.getPlayers().size();
    }

    private void broadcastWorld(String worldName, String legacyText) {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        Component msg = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(legacyText);
        for (Player p : w.getPlayers()) p.sendMessage(msg);
    }

    private MythicBukkit mythic() {
        try {
            return MythicBukkit.inst();
        } catch (Throwable t) {
            return null;
        }
    }

    public record EntryCheck(boolean allowed, String denyReason) {
        public static EntryCheck allow() { return new EntryCheck(true, null); }
        public static EntryCheck deny(String reason) { return new EntryCheck(false, reason); }
    }
}
