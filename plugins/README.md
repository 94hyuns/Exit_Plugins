# Pre-built plugin jars

이 폴더는 Paper 서버에 바로 넣을 수 있는 **사전 빌드된 jar 11개** 모음입니다.
운영 검증된 최신 버전이므로 별도 빌드 없이 사용 가능.

## 사용법

```bash
cp *.jar /path/to/your-paper-server/plugins/
```

이후 Paper 서버 재시작. 첫 부팅 시 각 플러그인이 `plugins/<PluginName>/` 폴더 + 기본 `config.yml` 자동 생성.

## 포함된 jar

| 파일 | 버전 | 설명 | 소스 |
|---|---|---|---|
| `core-1.8.0.jar` | 1.8.0 | PlayerDataManager, ServiceRegistry, Provider 인터페이스 | [../core/](../core/) |
| `economy-?` | — | 본 repo 미포함 (외부 economy 플러그인 별도 설치 필요) | — |
| `land-1.1.2.jar` | 1.1.2 | 청크 단위 땅 구매/관리 | [../land/](../land/) |
| `shop-1.8.0.jar` | 1.8.0 | NPC 4-카테고리 상점 + 시세 변동 | [../shop/](../shop/) |
| `CustomItems-1.9.0.jar` | 1.9.0 | 커스텀 인챈트 ("램프") | [../custom-items/](../custom-items/) |
| `cosmetics-1.3.1.jar` | 1.3.1 | 7종 치장 + 뽑기/가루 | [../cosmetics/](../cosmetics/) |
| `job-2.5.1.jar` | 2.5.1 | 광부/어부/농부 직업 + 보관함 | [../job/](../job/) |
| `Farming-1.11.3.jar` | 1.11.3 | 13작물 커스텀 농사 | [../farming/](../farming/) |
| `Fishing-1.5.1.jar` | 1.5.1 | 계절별 낚시 시스템 | [../fishing/](../fishing/) |
| `rewards-1.2.0.jar` | 1.2.0 | 보스 처치 보상 (dungeon-rewards) | [../dungeon-rewards/](../dungeon-rewards/) |
| `exit-gamble-1.5.1.jar` | 1.5.1 | 슬롯/로또 미니게임 | [../exit-gamble/](../exit-gamble/) |
| `world-1.4.0.jar` | 1.4.0 | 다중 월드 + 보스 아레나 + 보호 정책 | [../world/](../world/) |

## 외부 의존 (별도 설치 필요)

다음 두 플러그인은 일부 자체 플러그인이 hook/softdepend 하지만 **본 repo 에는 미포함** (별도 라이선스):

- **MythicMobs** — [공식 사이트](https://www.mythicmobs.net/) (`dungeon-rewards` 는 hard depend)
- **ModelEngine** — [Ticxo 공식](https://github.com/Ticxo/Model-Engine) (`cosmetics` 의 모델 시스템 soft depend)
- **Economy 플러그인** (예: Vault + Essentials 등) — Land/Shop 의 잔액 차감에 필요

없어도 다른 플러그인 기능은 정상 동작 (런타임 가드).

## 첫 부팅 시 필수 설정

서버 첫 실행 후 종료한 다음 다음 파일들을 운영 환경에 맞게 손볼 것:

- `plugins/Shop/config.yml` — 상품/가격 카탈로그
- `plugins/Farming/config.yml` — 작물 가격/허용 월드
- `plugins/Job/config.yml` — 직업별 exp multiplier
- `plugins/World/worlds.yml` — 월드 정의 (서버 환경에 맞는 좌표)
- `plugins/World/dungeons.yml` — 던전/보스 정의

각 항목 상세는 해당 플러그인의 [`../<plugin>/README.md`](..) 참고.
