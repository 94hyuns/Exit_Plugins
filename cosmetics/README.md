# Cosmetics 플러그인

7종 치장 슬롯 시스템 + 뽑기/가루 경제. Villager NPC 상인 + 옷장 GUI.

## 슬롯 타입

| 슬롯 | 구현 방식 |
|---|---|
| 모자 / 상의 / 하의 / 신발 | 패킷 기반 방어구 스왑 (방어구 슬롯 점유 X) |
| 무기 | 실제 ItemStack CMD 수정 (원복 안전장치 섬세) |
| 날개 | ItemDisplay passenger |
| 트레일 | BukkitRunnable 파티클 |

## 경제 시스템

- **뽑기권**: 상인에서 구매 → 옷장 GUI 에서 뽑기 → 중복 시 가루 환불
- **가루**: 중복 환불 + 가루 거래소에서 원하는 치장 교환 가능
- `ShardChangeEvent` (Core) — 가루 잔액 변경 발행

## 의존성

- **Paper API** 1.21.11
- **Core** 1.8.0 (`CosmeticProvider` 구현 + `BalanceChangeEvent`/`ShardChangeEvent` 사용)
- **softdepend**: ModelEngine (날개/모델 적용), MythicMobs

## 빌드

```bash
cd cosmetics
mvn package
```

## 주요 명령

| 명령 | 설명 |
|---|---|
| `/치장` | 옷장 GUI 열기 |
| `/치장 상인 spawn <닉네임>` | 치장 상인 NPC 배치 |
| `/치장 가루 <amount>` | 가루 잔액 확인/조작 (admin) |

## 외부 자산

bbmodel (날개/모자/무기 모델) 은 외부 작가 자산이라 본 repo 에 미포함. 클라이언트 측 리소스팩에 자체 머지 필요.
