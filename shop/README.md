# Shop 플러그인 (v1.0.1 → 1.1.0)

NPC 기반 상점 플러그인. 광물 / 작물 / 램프 / 낚시 4가지 카테고리 NPC를 FakePlayer로 배치.

## 주요 변경 (1.1.0)

- **FISHING 카테고리 추가** — 낚시 상인 NPC
  - 플레이어 우클릭 시 Fishing 플러그인의 `FishShopProvider.openShop` 으로 라우팅
  - Shop 자체 아이템 카탈로그엔 물고기 등록 안 함 (동적 가격이라 분리)
- **config.yml 100% 구동 전환** — `registerDefaults()` 제거. 모든 아이템을 config로 관리
- **Farming provider 라우팅** — 13작물 열매 + 경작지 티켓을 Shop에서 판매
  - `provider: Farming`, `type: wheat/tomato/...` 로 작물 지정
  - `type: FARMLAND_TICKET` 으로 경작지 티켓 지정
- **PDC 기반 인벤토리 매칭** (`InventoryMatcher`) — 작물 판매 시 바닐라 아이템과 분리
- `/shop reload` 명령 추가

## 의존성

- **Paper API** 1.21.11 (paper-nms 기반)
- **Core** 1.8.0
- **softdepend**: CustomItems (램프), Farming (작물), Fishing (낚시 상인)

## 빌드

Shop 도 NMS 직접 접근(스킨/FakePlayer 호환) 때문에 `paper-nms-maven-plugin` 의존. **최초 빌드 전 1회 init 필수**:

```bash
cd shop
mvn ca.bkaw:paper-nms-maven-plugin:1.4.11-SNAPSHOT:init   # 1회만
mvn package
```

## 설정 구조 (config.yml)

```yaml
items:
  - id: coal
    category: MINERAL
    material: COAL
    buy: 10
    sell: 5
    name: "석탄"

  - id: wheat
    category: CROP
    provider: Farming
    type: wheat
    buy: 20
    sell: 10
    name: "밀"
    icon: BEETROOT

  - id: farmland_ticket
    category: CROP
    provider: Farming
    type: FARMLAND_TICKET
    buy: 500
    name: "경작지 티켓"
    icon: PAPER

  - id: life_lamp
    category: LAMP
    provider: CustomItems
    type: LIFE
    buy: 1000
    name: "생활 램프"
    icon: REDSTONE_LAMP
```

**중요 규칙**:
- `sell` 필드가 있으면 판매 가능(시세 변동 적용), 없으면 구매 전용
- `provider` 없으면 바닐라 아이템 (material 필수)
- FISHING 카테고리에는 아이템 등록 불가 (경고 후 skip)

## 명령어

| 명령 | 설명 |
|---|---|
| `/shop spawn <mineral\|crop\|lamp\|fishing> [스킨]` | NPC 생성 |
| `/shop remove <카테고리>` | NPC 제거 |
| `/shop setskin <카테고리> <닉네임>` | 스킨 변경 |
| `/shop prices [카테고리]` | 시세 조회 (fishing 제외) |
| `/shop reload` | config.yml 재로드 |

## 낚시 상점 워크플로우

1. `/shop spawn fishing` 으로 낚시 상인 NPC 배치
2. 플레이어가 NPC 우클릭
3. Shop이 `FishShopProvider` (Core 경유) 로 `openShop(player)` 호출
4. Fishing 플러그인의 계절별 GUI 오픈

## 4-8인 서버 기준 데이터 구조

- `plugins/Shop/config.yml`: 아이템 카탈로그
- `plugins/Shop/npcs.yml`: NPC 위치/스킨
- `plugins/Shop/prices.dat`: 시세 변동 영속 데이터
