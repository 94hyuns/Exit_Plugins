# Farming 플러그인 (v1.0.0 → 1.1.0)

Paper 1.21.11 용 농사 플러그인. 13작물 + 월드별 정책 + 경작지 클레임 시스템.

## 원본 출처

이 플러그인은 다음 원본을 기반으로 **수정 / 확장한 파생물** 입니다:

- **원본**: <https://www.koreaminecraft.net/plugins/3963017>

원본 라이선스 / 사용 조건은 위 페이지를 참고하세요. 수정/추가된 부분 (13작물, 월드별 정책, 경작지 클레임 시스템, CropItemProvider/FarmlandTicketProvider, cooking 팩 통합 등) 은 본 repo 의 [MIT LICENSE](LICENSE) 적용.

## 주요 변경 (1.1.0)

- **Core 1.1.0 인터페이스 구현**
  - `CropItemProvider`: 작물 씨앗/열매 발급 및 식별 (Shop 등 외부 사용)
  - `FarmlandTicketProvider`: 경작지 티켓 아이템 발급
- **월드별 농사 정책** (config.yml `worlds` 섹션)
  - `FREE`: 바닐라처럼 자유
  - `MANAGED`: 클레임된 경작지에서만 농사 + 괭이질 차단 + 티켓 필요
  - `FORBIDDEN`: 모든 농사 금지
- **경작지 클레임 시스템** (`plugins/Farming/claims.yml`)
  - 좌표별 소유자 UUID 저장
  - 클레임된 경작지는 파괴/트램플/페이드 모두 방지 (관리자 크리 예외)
- 새 명령어 `/경작지정보` — 바라보는 블록 클레임 조회

## 의존성

- **Paper API** 1.21.4+
- **Core** 1.1.0

## 월드 정책 (기본값)

```yaml
worlds:
  world_village:
    policy: MANAGED      # 마을: 티켓 구매 후 등록된 경작지에서만
  world_wild:
    policy: FORBIDDEN    # 야생: 농사 금지
  world_dungeon:
    policy: FORBIDDEN
  default-policy: FREE   # 그 외 월드 (nether, end 등)
```

## 경작지 티켓 흐름

1. 플레이어가 **작물 상인**에서 "경작지 티켓" 구매 (기본 500w)
2. 마을 서버(`world_village`)에서 일반 흙 블록에 **티켓 들고 우클릭**
3. 흙 → farmland 전환 + 소유권 등록 + 티켓 1개 소비
4. 해당 경작지 위에서만 씨앗 심기 가능, 트램플·파괴 방지

## 명령어

| 명령 | 설명 | 권한 |
|---|---|---|
| `/씨앗모음` | 13종 씨앗 각 2개 지급 | OP |
| `/과일모음` | 13종 열매 각 2개 지급 | OP |
| `/경작지정보` | 바라보는 경작지 클레임 조회 | 누구나 |
