# CustomItems - 램프 인챈트 시스템

Paper 1.21.1 기반. 바닐라 인챈트와 완전히 분리된 커스텀 인챈트를 "램프"라는 소비 아이템으로 부여하는 시스템.

## 빌드

```bash
cd custom-items
mvn clean package
# 결과: target/CustomItems-1.0.0.jar
```

JAR을 서버의 `plugins/` 폴더에 넣고 재시작.

## 사용법

1. 관리자: `/lamp give <player> <life|combat> [amount]` 로 램프 지급
2. 플레이어: **주손에 램프 + 보조손에 대상 도구** 들고 **우클릭**
3. 모루 소리와 함께 도구 로어에 램프 인챈트 1-2줄 추가
4. 같은 도구에 램프를 한 번 더 쓰면 **리롤**

### 도구 카테고리
- **생활 램프**: 곡괭이, 괭이, 삽, 낚싯대
- **전투 램프**: 검, 활, 쇠뇌, 도끼, 삼지창, 철퇴, 방어구 4부위
- **제외**: 방패

## 구현된 인챈트 (생활 16종 + 전투 1종 테스트)

### 공통 (생활도구 전체)
| 인챈트 | 효과 |
|---|---|
| 닭/토끼/돼지/양/소 처치 전리품 | 각 동물 처치 시 [10-50]% 확률로 바닐라 드롭 +[1-2]세트 |
| 몬스터 처치 전리품 | Monster 인터페이스 구현 몹 처치 시 [10-50]% 확률로 드롭 +[1-2]세트 |
| 농작물 뼛가루 | 완전히 자란 농작물 수확 시 [10-50]% 확률로 뼛가루 +[1-2] |
| 경험치 획득량 | 경험치 획득 시 [20-50]% 확률로 획득량 +[3-5] |

### 곡괭이 전용
| 인챈트 | 효과 |
|---|---|
| 주괴 드랍 | 철/금/구리/고대잔해 채굴 시 주괴로 드랍. 섬세한 손길과 동시 적용 시 효과 발동 안 함 |
| 주변 채굴 | Lv.1 상·하 / Lv.2 상·하 + 동서남북 4방향 동시 채굴. **야생 한정** |
| 폭발 채굴 | Lv.1/2/3 = 50/60/75% 확률로 시선 기준 3x3 평면 동시 채굴. **야생 한정** |

### 괭이 전용
| 인챈트 | 효과 |
|---|---|
| 농작물 추가 작물 | 농작물 수확 시 [20-50]% 확률로 해당 작물 +[1-3] |
| 농작물 추가 씨앗 | 농작물 수확 시 [20-50]% 확률로 해당 씨앗 +[1-3] (밀/비트/네더사마귀만) |

### 삽 전용
| 인챈트 | 효과 |
|---|---|
| 모래 유리화 | 모래/붉은모래 채굴 시 유리 블록으로 드랍 |

### 낚싯대 전용
| 인챈트 | 효과 |
|---|---|
| 오토릴 Lv.1-3 | 물림 시 자동 리트리브. Lv.1 = 5초 / Lv.2 = 3초 / Lv.3 = 1초 |

## 설정 (`plugins/CustomItems/config.yml`)

```yaml
lamp:
  line-count-weights: [7, 3]   # 1줄 70%, 2줄 30%
  level-bias: 2.0              # 낮은 값 쏠림 강도
  roll-sound:
    name: BLOCK_ANVIL_USE
    volume: 1.0
    pitch: 1.0

# "야생 한정" 인챈트가 발동 가능한 월드 이름
wilderness-worlds:
  - "world_wild"

# 인챈트 롤 가중치 (공통 vs 특정도구)
# common: 카테고리 전체에 적용 가능한 인챈트 (예: 처치 전리품, 뼛가루, 경험치)
# tool-specific: 특정 도구 전용 인챈트 (예: 곡괭이 전용 폭발 채굴)
# 값이 클수록 해당 범위 인챈트가 자주 롤됨.
enchant-weights:
  common: 6
  tool-specific: 4
```

## 아키텍처

```
CustomItemsPlugin
├── LampConfig          config.yml 로드
├── WildernessChecker   야생 월드 판정 (추후 land 플러그인으로 교체 가능)
├── EnchantRegistry     인챈트 등록/조회
├── LampItem            램프 아이템 생성/식별
├── EnchantStorage      PDC 직렬화/역직렬화
├── EnchantRoller       1/2줄 결정 + 값 롤
├── LoreRenderer        로어 갱신 (기존 사용자 로어 보존)
├── EnchantDispatcher   트리거별 슬롯 조회 헬퍼
├── 리스너 5종
│   ├── MobKillListener      동물/몬스터 처치 전리품
│   ├── HarvestListener      농작물 수확 (뼛가루/작물/씨앗)
│   ├── MiningListener       곡괭이/삽 인챈트 통합
│   ├── FishingListener      오토릴
│   └── ExpListener          경험치 증가
├── LampHandler         PlayerInteractEvent 처리
└── LampCommand         /lamp give
```

## 확장 포인트

**인챈트 하나 추가하는 방법:**

1. `enchant/impl/` 아래에 `CustomEnchant` 구현 클래스 작성
2. 효과가 필요하면 해당 카테고리 리스너에 로직 추가 (없으면 새 Listener 작성)
3. `CustomItemsPlugin#registerEnchants()` 에 `registry.register(new MyEnchant(this));` 한 줄 추가

**수치 포맷:** 모든 값은 정수(×100) 스케일로 저장/계산. `NumUtil.toStored/fromStored`.
**확률 체크:** `RollUtil.percentRoll(storedValue)`.
**야생 판정:** `WildernessChecker.isWilderness(location)`.

## 외부 플러그인 연동 (Shop 등)

CustomItems는 Core의 `ServiceRegistry` 에 `LampProvider` 인터페이스로 노출됩니다.
다른 플러그인에서 램프 아이템을 생성하거나 식별할 때 이 경로를 사용하세요.

```java
import com.exit.core.api.LampProvider;
import com.exit.core.api.LampInfo;
import com.exit.core.registry.ServiceRegistry;

// 상점 GUI 구성 시 카탈로그 조회
LampProvider lamp = ServiceRegistry.get(LampProvider.class).orElse(null);
if (lamp != null) {
    for (LampInfo info : lamp.listTypes()) {
        // info.typeId()  = "LIFE" / "COMBAT"
        // info.displayName() / info.description()
    }
}

// 구매 시 아이템 지급
ItemStack item = lamp.createLamp("LIFE", 1);
player.getInventory().addItem(item);

// 환불 등 역방향: 이 아이템이 램프인지 확인
String typeId = lamp.identify(someItem);  // 램프 아니면 null
```

**shop/config.yml 예시:**
```yaml
items:
  - id: life_lamp
    provider: CustomItems
    type: LIFE
    price: 1000

  - id: combat_lamp
    provider: CustomItems
    type: COMBAT
    price: 1500
```

## 구현 시 가정한 부분 (실서버 테스트 후 조정 필요)

1. **"전리품"** = 해당 몹의 바닐라 드롭 세트를 세트 수만큼 복제 추가
2. **주변 채굴 Lv.2** = 상·하 + 동서남북 4방향 = 총 6개 인접 블록
3. **제련 가능 대상** = 철/금/구리 광석(+심층암) + 고대잔해
4. **수확 대상 작물** = 밀/당근/감자/비트/네더사마귀/코코아
5. **씨앗 대상 작물** = 밀/비트/네더사마귀만
6. **breakNaturally()** 는 재귀 이벤트 미발동. 주변/폭발 채굴로 부서진 블록엔 다른 램프 인챈트 미적용
7. **폭발 채굴 발동 시 주변 채굴은 스킵** (중복 방지)

## 인챈트 가중치 시스템

각 인챈트는 `EnchantScope` 분류를 가짐:
- **COMMON**: 카테고리의 모든 도구에 적용 가능 (공통). 기본 가중치 6
- **TOOL_SPECIFIC**: 특정 도구 전용. 기본 가중치 4

롤 시 각 인챈트의 뽑힐 확률은 해당 가중치에 비례. 공통 인챈트가 특정 인챈트보다 1.5배 자주 롤됨.
값은 config.yml의 `enchant-weights` 에서 조정 가능.

## 테스트 시나리오

```
/lamp give <본인> life 50

# 각 도구에 램프 적용 후
# - 동물 잡아보기 (전리품 증가 확인)
# - 밀 수확 (뼛가루/작물/씨앗 추가 확인)
# - 철 광석 채굴 (주괴 드랍/등급 드랍 확인)
# - 모래 캐기 (유리 드랍 확인)
# - 낚시 (BITE 후 자동 리트리브 확인)

# 야생 한정 인챈트 테스트는
# config.yml 의 wilderness-worlds 에 월드 이름 추가 후 서버 재시작
```
