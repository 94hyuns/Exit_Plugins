# Exit Gamble 플러그인

NPC 기반 미니게임/베팅 시스템. 슬롯머신 + 로또.

## 기능

- **슬롯머신** — NPC 우클릭 시 GUI 오픈 → 일정 코스트 차감 → 결과 roll → 당첨 시 배율 보상
- **로또** — 매일 자정 한 번 자동 추첨, 플레이어가 미리 베팅한 번호와 비교

## 설정 (`machines.yml`, `lottery.yml`)

각 슬롯/로또 인스턴스는 yml 로 정의. 베팅 단위, 배율, 잭팟 누적 등 튜닝 가능.

## 의존성

- **Paper API** 1.21.11
- **Core** 1.8.0
- **softdepend**: Economy (베팅 잔액 차감/지급)

## 빌드

```bash
cd exit-gamble
mvn package
```

## 주요 명령

| 명령 | 설명 |
|---|---|
| `/gamble spawn <slot\|lottery> <id>` | 슬롯/로또 NPC 배치 |
| `/gamble stats [player]` | 통계 조회 |

## 데이터

- `plugins/ExitGamble/machines.yml` — 슬롯 NPC 정의
- `plugins/ExitGamble/lottery.yml` — 로또 NPC + 베팅 풀
- `plugins/ExitGamble/gamble-stats.yml` — 누적 베팅/당첨 통계
