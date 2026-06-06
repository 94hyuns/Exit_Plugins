package com.exit.world.boss;

/**
 * 보스 아레나 1개의 설정 (dungeons.yml 의 boss-arena 섹션에서 로드).
 *
 * @param arenaKey      DungeonEntry.key (예: "boss1")
 * @param worldName     보스가 등장하는 월드 이름
 * @param bossMobName   MythicMobs internal name
 * @param bossX/Y/Z     보스가 소환되는 좌표
 * @param countdownSec  플레이어 첫 입장 후 보스 소환까지 카운트다운 (기본 30)
 * @param cooldownSec   보스 사망 후 다음 소환까지 (기본 600 = 10분)
 * @param graceSec      사망/와이프 후 재입장 잠금 (기본 10)
 * @param killRewardMin 보스 처치 보상 최소 (월드 내 플레이어 균등 분배)
 * @param killRewardMax 보스 처치 보상 최대
 */
public record BossArenaConfig(
        String arenaKey,
        String worldName,
        String bossMobName,
        double bossX,
        double bossY,
        double bossZ,
        int countdownSec,
        int cooldownSec,
        int graceSec,
        int killRewardMin,
        int killRewardMax,
        double damageMultiplier   // 보스가 받는 데미지 배율 (1024 어트리뷰트 캡 우회용). 기본 1.0
) {}
