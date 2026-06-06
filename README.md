# Exit MC Plugins

Paper 1.21.x 기반 자체 제작 마인크래프트 플러그인 monorepo. 4~8인 소규모 사설 서버 운영에서 검증된 11종 플러그인 모음.

## 플러그인 목록

| 플러그인 | 주요 기능 | 외부 의존 |
|---|---|---|
| **core** | PlayerDataManager(SQLite), ServiceRegistry, Provider 인터페이스(Economy/Lamp/Cosmetic/FishShop/Crop/FarmlandTicket), BalanceChangeEvent/ShardChangeEvent, 한글 명령어 두벌식 alias | paper-nms |
| **economy** *(미공개 자리)* | 울캐쉬 화폐 — 이번 monorepo 에 미포함 | - |
| **land** | 청크 단위 땅 구매/관리, Core EconomyProvider 연동 | - |
| **shop** | NPC 기반 4-카테고리 상점 (광물/작물/램프/낚시), provider 라우팅, 시세 변동 | paper-nms, MythicMobs (soft) |
| **custom-items** | 커스텀 인챈트 ("램프") + 동시 도구 시스템 | MythicMobs (soft), Job/Fishing/Farming (soft) |
| **cosmetics** | 7종 치장 슬롯(모자/상의/하의/신발/무기/날개/트레일), 뽑기/가루 경제 | ModelEngine (soft), MythicMobs (soft) |
| **job** | 광부/어부/농부 3직업 × Lv1-10 × 5perk, 보관함 시스템, JobLevelUpEvent | - |
| **farming** † | 13작물 커스텀 농사, 경작지 클레임, 물뿌리개/스프링쿨러, 작물 보관함 | Job (soft), MythicMobs (cooking 통합, soft) |
| **fishing** † | 계절별 낚시 시스템, 동적 가격, FishingExpHook | Job (soft) |
| **dungeon-rewards** | 보스 처치 보상 시스템 | MythicMobs |
| **exit-gamble** | 슬롯/로또 미니게임, NPC 베팅 시스템 | Economy (soft) |
| **world** | 다중 월드 관리, 보스 아레나, 보호월드 정책, 던전 시스템 | MythicMobs (soft) |

> † 표시된 플러그인은 **외부 원본을 기반으로 수정/확장한 파생물** 입니다. 자세한 출처/라이선스는 아래 "원본 출처" 섹션 참고.

## 원본 출처 (파생 플러그인 †)

다음 두 플러그인은 KoreaMinecraft 의 원본 플러그인을 기반으로 본 프로젝트에서 수정/확장한 파생물입니다. **원본 라이선스/사용 조건은 각 원본 페이지를 따르며**, 본 repo 의 추가/수정분만 MIT 적용입니다.

| 플러그인 | 원본 | 본 repo 에서 수정/추가된 부분 |
|---|---|---|
| **fishing** | <https://www.koreaminecraft.net/plugins/3963170> | 24종 월별 제철 어종, 4계절 순환, `FishShopProvider` 연동, 낚시 상점 GUI |
| **farming** | <https://www.koreaminecraft.net/plugins/3963017> | 13작물 + 월드별 정책(FREE/MANAGED/FORBIDDEN), 경작지 클레임 시스템, `CropItemProvider`/`FarmlandTicketProvider`, cooking 팩 통합 |

각 플러그인 폴더의 [`fishing/README.md`](fishing/README.md) / [`farming/README.md`](farming/README.md) 에도 동일한 출처가 명시되어 있습니다.

## 커스텀 인챈트 목록 (CustomItems · 38종)

바닐라 인챈트와 완전 분리된 자체 인챈트 시스템. **램프** 라는 소비 아이템으로 주손에 들고 보조손 도구에 우클릭 → 1~2줄 랜덤 롤. 자세한 시스템 설명은 [`custom-items/README.md`](custom-items/README.md).

브라우저로 보고 싶으면 [`enchantslist.html`](enchantslist.html) (자동 생성, dark theme) 을 열어보세요.

<details>
<summary><b>생활 인챈트 (13종)</b> — 곡괭이 / 괭이 / 삽 / 낚싯대 / 도끼</summary>

| 이름 | 레벨 | 도구 | 효과 |
|---|---|---|---|
| 농작물 뼛가루 | - | 공통 | 수확/채광 시 (10~50)% 확률로 뼛가루 (1~2)개 드랍 |
| 경험치 획득량 | - | 공통 | 생활 활동 시 (20~50)% 확률로 경험치 구슬 (2~3)개 |
| 사냥꾼 | - | 공통 | 동물 처치 시 (20~50)% 확률로 부산물 (1~2)배 추가 |
| 급속제련 | Lv 1~2 | 곡괭이 | 광물 채굴 시 제련된 주괴 드랍 (섬세한 손길과 동시 적용 X) |
| 채굴의 달인 | Lv 1~2 | 곡괭이 | 인접 블록 동시 채굴 (Lv1 상하 / Lv2 십자) · 야생 한정 |
| 인간 굴착기 | Lv 1~3 | 곡괭이 | (50/60/75)% 확률로 3×3×3 폭발 채굴 · 야생 한정 |
| 행운의 작물 | Lv 1~5 | 괭이 | 농작물 수확 시 (30~50)% 확률로 작물 +2 |
| 괭이의 달인 | Lv 1~2 | 괭이 | 시선 앞쪽 (1/2)칸 자란 작물 동시 수확 |
| 모래 유리화 | - | 삽 | 모래 채굴 시 유리블록 드랍 |
| 자동 회수 | Lv 1~3 | 낚싯대 | 입질 후 (5/3/1)초 뒤 자동 회수 · 물고기 티어 +Lv |
| 자동 투척 | Lv 1~3 | 낚싯대 | 잡은 후 (3/2/1)초 뒤 자동 투척 · 물고기 티어 +Lv |
| 숙련된 벌목 | Lv 1~3 | 도끼 | 나무 벌목 시 (20/35/50)% 확률로 +1 |
| 벌목의 왕 | Lv 1~2 | 도끼 | 연결된 나무 (5/10)개 이하면 한번에 전체 벌목 |

</details>

<details>
<summary><b>전투 인챈트 — 무기 기본 (4종)</b></summary>

| 이름 | 도구 | 효과 |
|---|---|---|
| 공격력 | 검·창·삼지창·철퇴 | 공격력 +3 / +4 / +5 |
| 흡혈 | 검·창·삼지창·철퇴 | 공격 시 체력 +(0.5~1.0)칸 · 쿨 (2~3)초 |
| 활 공격력 | 활 | 화살에 +(1.5~3.0) 데미지 |
| 활 치명타 | 활 | 화살 (30~50)% 확률로 ×2 데미지 |

</details>

<details>
<summary><b>전투 인챈트 — 무기 유니크 (12종)</b></summary>

| 이름 | 레벨 | 도구 | 효과 |
|---|---|---|---|
| 거인의 힘 | Lv 1~2 | 검·창·삼지창·철퇴 | 최대체력 비례 +(300~420)% 데미지 (28칸 기준) |
| 충만한 활력 | Lv 1~2 | 검·창·삼지창·철퇴 | 허기 7칸+ 부분 — 만복 시 +300~420% |
| 끝없는 허기 | Lv 1~2 | 검·창·삼지창·철퇴 | 허기 5칸- 부분 — 1칸 시 +390~560% |
| 약자멸시 | Lv 1~2 | 검·창·삼지창·철퇴 | 적 체력 50% 미만 시 +(300~420)% |
| 묵직한 일격 | Lv 1~2 | 검·창·삼지창·철퇴·활 | 공격력 +(10/15) flat |
| 정면승부 | Lv 1~2 | 검·창·삼지창·철퇴·활 | 적 체력 50% 이상 시 +(300~420)% |
| 치명타 | Lv 1~2 | 검·창·삼지창·철퇴·활 | (25/40)% 확률로 데미지 (5/6)배 (합연산) |
| 질주의 일격 | Lv 1~2 | 창 | 스프린트 시 이속 비례 (풀스프린트 +300~420%) |
| 낙뢰 | Lv 1~2 | 철퇴 | 근접 시 (40~50)% 확률로 850% 번개 데미지 |
| 이연사 | Lv 1~2 | 활 | (50/75)% 확률로 화살 1발 추가 |
| 삼연사 | Lv 1~2 | 활 | (35/55)% 확률로 화살 2발 추가 (이연사와 중복) |
| 신속 사격 | Lv 1~2 | 활 | (80/90)% 차지 시간 감소 |

</details>

<details>
<summary><b>전투 인챈트 — 방어구 기본 (4종)</b></summary>

| 이름 | 효과 |
|---|---|
| 견고함 | 방어도 +(2~4) |
| 활력 | 최대체력 +(1.0~2.0)칸 |
| 황금 심장 | Lv 1~3 · (20/10/5)초마다 황금심장 +1 (최대 = 부위수+1) |
| 신속 | 이동속도 +(5~10)% |

</details>

<details>
<summary><b>전투 인챈트 — 방어구 세트 (5종)</b> · 부위마다 Lv 1씩 합산, 최대 4부위</summary>

| 이름 | 효과 |
|---|---|
| 공복의 저주 | 허기 (4/3/2/1)칸+로 회복 X · 3부위+부터 이속 +30% 보상 |
| 체력은 국력 | 최대체력 +(4/6/8/10)칸 |
| 아그니 | 적 불 데미지 +(50/100/150/200)% (화염·발화 인챈트) |
| 도깨비불 | 방어구 주위 영혼불 공전 + 공격 시 부위수×1.5 깡뎀 추가 |
| 행운 | 부위 기본 인챈트 효과 2배 + 다른 세트 인챈트 부위 카운트 자동 포함 |

</details>

## 커스텀 아이템 목록 (CustomItems · 7종)

램프 인챈트 시스템 외에 자체 제작한 무기 / 방어구 / 소비 아이템 / 훈련 도구.

| 분류 | 아이템 | 베이스 | 설명 |
|---|---|---|---|
| 무기 | **프로스트모운 (Frostmourne)** | NETHERITE_SWORD + CMD 11001 | 보스 드랍 무기. 능동 스킬 3종 (우클릭=빙판 돌진 / Shift+우클릭=빙결 슬램 / Shift+좌클릭=빙결 숨결 궁극기) |
| 무기 | **그레이트 소드 (GreatSword)** | NETHERITE_SWORD + CMD 11002 | 중간단계 검. 우클릭 스킬 없는 순수 스탯 강화형 |
| 방어구 | **아누비스 세트 (Anubis Armor)** | NETHERITE (3부위) | 보스2 (Anubis) 처치 드롭. 투구/하의/신발 (흉갑 X) + Equippable asset 외형 |
| 방어구 | **용의 날개 (Winged Armor)** | ELYTRA + CMD 11003 | 활공 가능 커스텀 갑옷. 방어 10 / 강도 4 / 넉백저항 0.1 |
| 소비 | **빅맥 (BigMac)** | COOKED_BEEF + CMD 10002 | 커스텀 음식. ExitItemPack 의 `bigmac_burger` 모델 적용. 식료 8 회복 |
| 소비 | **인벤세이브권 (InvSave)** | PAPER + CMD 10001 | 사망 시 인벤토리 보호 아이템 |
| 훈련 | **허수아비 (Dummy v2)** | COW 기반 entity | HP 500, NoAI, 데미지 받지만 안 죽음, 머리 위 HP 표시, 2초마다 풀피 회복 |

지급 명령 (OP):
```
/frostmourne <player>     # 프로스트모운 1개
/greatsword <player>      # 그레이트 소드 1개
/anubisarmor <player>     # 아누비스 세트 3부위
/wingedarmor <player>     # 용의 날개
/dummy spawn              # 허수아비 소환 (현 위치)
```

빅맥/인벤세이브권은 ShopProvider 경로 (잡화상인) 으로 판매. 자세한 구현은 [`custom-items/`](custom-items/) 의 `armor/` / `weapon/` / `consumable/` / `dummy/` 패키지 참고.

## 빠른 시작 (빌드 없이)

[`plugins/`](plugins/) 폴더에 사전 빌드된 최신 jar 11개가 있습니다. 마크 서버의 `plugins/` 폴더에 그대로 복사 후 재시작하면 끝.

```bash
# 예: 우분투 서버
cp plugins/*.jar /path/to/your-paper-server/plugins/
# 그 후 서버 재시작
```

필요시 [MythicMobs](https://www.mythicmobs.net/) / [ModelEngine](https://github.com/Ticxo/Model-Engine) 도 같이 install (일부 플러그인의 soft depend).

## 소스 빌드 (커스터마이징 시)

### 사전 준비

- **JDK 21** (core/cosmetics/exit-gamble/job/farming/fishing/shop/custom-items)
- **JDK 17** 도 OK (land/world/dungeon-rewards)
- **Maven 3.9+**
- **paper-nms 초기화** (Core, Shop 만 필요 — NMS 직접 접근):
  ```bash
  cd core && mvn ca.bkaw:paper-nms-maven-plugin:1.4.11-SNAPSHOT:init
  cd ../shop && mvn ca.bkaw:paper-nms-maven-plugin:1.4.11-SNAPSHOT:init
  ```
  이 init 은 1회만. 매핑 캐시가 만들어지면 이후 빌드에서 자동 활용.

### 빌드 순서

플러그인 간 컴파일 의존이 있어서 다음 순서 권장:

```bash
# 1. Core (모든 플러그인의 root)
cd core && mvn install

# 2. Core 만 의존하는 leaf 들
cd ../job && mvn install
cd ../fishing && mvn install
cd ../farming && mvn install

# 3. 위 3종 + Core 의존
cd ../custom-items && mvn package
cd ../cosmetics && mvn package
cd ../shop && mvn package

# 4. 독립적
cd ../land && mvn package
cd ../world && mvn package
cd ../dungeon-rewards && mvn package
cd ../exit-gamble && mvn package
```

빌드 산출물은 각 플러그인의 `target/<artifactId>-<version>.jar` 에 생성됨. 그걸 Paper 서버의 `plugins/` 에 복사.

## 외부 플러그인 (선택 / 별도 install)

다음은 코드 import 또는 plugin.yml softdepend 로 참조되지만 이 repo 에는 포함 안 됨:

- **[MythicMobs](https://www.mythicmobs.net/)** (Lumine) — 보스/커스텀 mob 시스템. dungeon-rewards 는 hard depend, 나머지는 soft
- **[ModelEngine](https://github.com/Ticxo/Model-Engine)** (Ticxo) — 3D 모델링. cosmetics 의 mount 시스템 등에서 soft depend

이 두 플러그인은 별도 라이선스라 사용자가 직접 download / install 필요. 없어도 다른 기능은 정상 동작 (런타임 가드).

## 라이선스

[MIT](LICENSE)

## 기여

이 repo 는 운영 종료된 시즌의 정리본. 외부 contribution 은 받지 않지만 fork / 학습 / 자체 서버 활용 모두 환영.

---

*이번 시즌 4~8인 사설 서버에서 1개월 가량 운영하면서 검증된 코드입니다. 외부 자산 (BlockBench 모델, 외부 작가 텍스처 등) 은 별도 사용권 문제로 본 repo 에서 제외됩니다.*
