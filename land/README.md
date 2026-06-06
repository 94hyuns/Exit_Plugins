# Land 플러그인

청크 단위 땅 구매/관리 시스템. 클레임된 청크는 보호 (다른 플레이어 건축/상호작용 차단).

## 동작

1. 플레이어가 `/땅 구매` 시 현재 위치 청크 구매
2. Core `EconomyProvider` 로 잔액 차감
3. 청크가 클레임되어 owner 만 build / break / interact 가능
4. World 플러그인의 보호 정책에 따라 land-override 동작 (마을 월드 = 클레임 보호 우선)

## 의존성

- **Paper API** 1.21.1
- **Core** 1.8.0 (EconomyProvider)
- **Economy** (depend — 잔액 차감)
- **softdepend**: World (월드별 보호 정책 연동)

## 빌드

```bash
cd land
mvn package
```

## 주요 명령

| 명령 | 설명 |
|---|---|
| `/땅 구매` | 현재 청크 구매 |
| `/땅 정보` | 현재 청크 owner / 가격 |
| `/땅 양도 <player>` | 청크 소유권 이전 |
| `/땅 admin claim/release` | 관리자 직접 조작 |

## 데이터

- `plugins/Land/claims.yml` — 클레임된 청크 owner 목록
- `plugins/Land/admin_chunks.yml` — 관리자 지정 보호 청크 (마을 등)
