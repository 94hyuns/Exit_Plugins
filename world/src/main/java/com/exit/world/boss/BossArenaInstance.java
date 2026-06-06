package com.exit.world.boss;

import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * 보스 아레나 1개의 런타임 상태.
 *
 * 상태 머신:
 * - IDLE     : 보스 없음, 입장 자유 (단 cooldown 안 끝났으면 보스 안 뜸)
 * - COUNTDOWN: 첫 입장 후 30초 카운트다운 중. 보스 곧 등장. 입장 자유 (파티 합류 가능)
 * - COMBAT   : 보스 생존. 신규 입장 차단
 * - LOCKOUT  : 보스 사망 직후 또는 월드 와이프 직후 10초 잠금. 입장 차단
 *
 * 별도 추적:
 * - lastKillTimeMs: 마지막 보스 사망 시각 (없으면 -1). cooldownSec 와 합쳐 다음 spawn 가능 시점 계산
 * - lockoutUntilMs: 입장 잠금 만료 시각 (LOCKOUT 상태에서만 유효)
 */
public class BossArenaInstance {

    public enum State { IDLE, COUNTDOWN, COMBAT, LOCKOUT }

    private final BossArenaConfig config;

    private State state = State.IDLE;
    private long lastKillTimeMs = -1;
    private long lockoutUntilMs = -1;
    private UUID bossUuid = null;
    private BukkitTask countdownTask = null;
    private BukkitTask lockoutReleaseTask = null;

    public BossArenaInstance(BossArenaConfig config) {
        this.config = config;
    }

    public BossArenaConfig config() { return config; }
    public State state() { return state; }
    public UUID bossUuid() { return bossUuid; }

    public void setState(State s) { this.state = s; }
    public void setBossUuid(UUID u) { this.bossUuid = u; }
    public void setCountdownTask(BukkitTask t) {
        if (this.countdownTask != null) this.countdownTask.cancel();
        this.countdownTask = t;
    }
    public void cancelCountdown() {
        if (this.countdownTask != null) { this.countdownTask.cancel(); this.countdownTask = null; }
    }
    public void setLockoutReleaseTask(BukkitTask t) {
        if (this.lockoutReleaseTask != null) this.lockoutReleaseTask.cancel();
        this.lockoutReleaseTask = t;
    }

    public void markKill(long nowMs) {
        this.lastKillTimeMs = nowMs;
    }
    public void resetKill() {
        this.lastKillTimeMs = -1;
    }
    public long lastKillTimeMs() { return lastKillTimeMs; }

    public void setLockoutUntil(long ms) { this.lockoutUntilMs = ms; }
    public long lockoutUntilMs() { return lockoutUntilMs; }

    /** 신규 플레이어가 이 아레나(보스 월드) 로 들어올 수 있는가? */
    public boolean canEnter() {
        return state != State.COMBAT && state != State.LOCKOUT;
    }

    /** 지금 보스를 소환해도 되는가? (cooldown 체크) */
    public boolean canSpawnBoss(long nowMs) {
        if (lastKillTimeMs < 0) return true;  // 한 번도 죽인 적 없음
        return nowMs >= lastKillTimeMs + config.cooldownSec() * 1000L;
    }

    /** 다음 보스 소환 가능 시점까지 남은 초 (cooldown 활성일 때만 의미 있음) */
    public long secondsUntilSpawnReady(long nowMs) {
        if (lastKillTimeMs < 0) return 0;
        long ms = (lastKillTimeMs + config.cooldownSec() * 1000L) - nowMs;
        return Math.max(0, ms / 1000);
    }
}
