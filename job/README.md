# Job 플러그인

3직업 (광부 / 어부 / 농부) × Lv1-10 × 5종 perk 시스템.

## 직업

| 직업 | 핵심 perk | 보관함 |
|---|---|---|
| **광부** | 광물 채굴 시 경험치 / Lv6 max_health, Lv6=+3p 보관함 페이지 | 광물 (오버월드+네더+엔드 돌 화이트리스트, 다중 페이지 43슬롯/단위) |
| **어부** | 낚시 시 경험치, Lv4=+3p / Lv6=+3p 보관함 페이지 | 물고기 (총 7p / 301칸) |
| **농부** | 작물 수확 시 경험치, Lv6 광역 수집, Lv10 auto_replant | 작물 |

## API (cross-plugin)

- `MiningExpHook` — CustomItems 등이 광물 채굴 시 경험치 추가
- `FishingExpHook` — Fishing 플러그인이 낚시 성공 시 경험치 추가
- `JobLevelUpEvent` — Farming 등이 직업 levelup 캐치해서 perk 발동

## 의존성

- **Paper API** 1.21.4
- **Core** 1.8.0

## 빌드

```bash
cd job
mvn install   # 다른 플러그인 (Custom Items / Farming / Fishing / Shop) 이 의존하므로 install 필수
```

## 주요 명령

| 명령 | 설명 |
|---|---|
| `/job` | 직업 GUI |
| `/job select <mining\|fishing\|farming>` | 직업 선택 |
| `/job stats [player]` | 레벨/경험치 조회 |
| `/보관함` | 직업별 보관함 페이지 GUI |

## 데이터

- `plugins/Job/players/<uuid>.yml` — 플레이어별 직업/레벨
- `plugins/Job/mineral_storage/<uuid>.yml` — 광물 보관함
- `plugins/Job/crop_storage/<uuid>.yml` — 작물 보관함
- `plugins/Job/fish_storage/<uuid>.yml` — 물고기 보관함
