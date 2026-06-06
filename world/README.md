# World 플러그인

다중 월드 관리 + 보스 아레나 + 던전 + 보호 정책. 운영 중 가장 중심적인 플러그인.

## 주요 기능

### 1. 다중 월드 정의 (`worlds.yml`)

월드별 속성 한 곳 관리:
- spawn-location, border-size, biome, monster-spawn, pvp, difficulty
- protection (block-break/place/interact, land-override)
- VoidWorldGenerator 적용 여부 (마을/던전/보스 월드)
- time-lock (밤 고정 등)
- blocked-commands

### 2. 보스 아레나 시스템 (`dungeons.yml`)

보스 인스턴스 (Lich King, Anubis 등) 의 진입/처치/리스폰 + 카운트다운/취소 처리.
- 보스 spawn 시 damage-multiplier 자동 적용 (mob 체력 1024 캡 우회용)
- 전원 퇴장 시 자동 취소
- 보스 처치 시 인스턴스 종료

### 3. 보호 정책

- 마을 월드: hostile 베이스 MythicMobs 즉시 제거 (SILVERFISH/ZOMBIE 등)
- 던전 월드: VoidWorldGenerator + 자동 정리 (빈 던전 10초 후 MM mob 일괄 제거)
- /포탈 명령 — 정의된 월드 간 텔레포트

### 4. 침대 우선 리스폰

`onPlayerRespawn` — 침대/마을월드 조건부 spawn point 결정. `onSpawnChange` — 다른월드 spawn point 설정 차단.

## 의존성

- **Paper API** 1.21.1
- **Core** 1.8.0
- **softdepend**: MythicMobs (보스 spawn/death 이벤트)

## 빌드

```bash
cd world
mvn package
```

## 주요 명령

| 명령 | 설명 |
|---|---|
| `/포탈` | 월드 텔레포트 GUI |
| `/던전마스터 reload` | dungeons.yml 핫리로드 |
| `/admin chunk ...` | 청크 보호 admin |

## 데이터

- `plugins/World/worlds.yml` — 월드 정의 (재시작 필수)
- `plugins/World/dungeons.yml` — 보스/던전 설정 (`/던전마스터 reload` 가능)
