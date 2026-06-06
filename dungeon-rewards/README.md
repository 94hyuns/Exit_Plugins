# Dungeon Rewards 플러그인

MythicMobs 보스 처치 시 자동 보상 지급. 보스별로 드롭 풀 + 확률 + 경제 보상 정의.

## 동작

1. MythicMobs 의 `MythicMobDeathEvent` 캐치
2. `config.yml` 에서 죽은 mob 의 internal name 조회
3. 매칭되는 보상 풀 → 확률 roll → 플레이어 인벤 지급
4. 울캐쉬 등 경제 보상 같이 지급 (Core EconomyProvider 경유)

## 설정 예시

```yaml
rewards:
  Anubis:           # MythicMobs internal mob name
    money: 5000     # 처치 보상 (모든 참여자에게)
    drops:
      - { item: DIAMOND, amount: [2, 5], chance: 100 }
      - { item: NETHERITE_INGOT, amount: 1, chance: 25 }
      - { custom: anubis_helm, chance: 5 }    # CustomItems 의 커스텀 아이템 ID
```

## 의존성

- **Paper API** 1.21.1
- **Core** 1.8.0
- **MythicMobs** (hard depend — 5.11.2)
- **Economy** (depend — 화폐 지급용)

## 빌드

```bash
cd dungeon-rewards
mvn package
```
