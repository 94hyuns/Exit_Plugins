# Economy 플러그인 (v1.0.0)

Paper 1.21.x 기반 자체 화폐 시스템. **울캐쉬 (단위: `w`)** 를 Core 의 `PlayerDataManager` (SQLite) 에 영속 저장하고, `EconomyProvider` 인터페이스로 다른 플러그인(Shop, Job, Cosmetics, exit-gamble 등)에 잔액 조작 API 를 노출합니다.

## 주요 기능

- **잔액 CRUD** — `EconomyProviderImpl` 이 `Core.PlayerDataManager` 위임 (`getBalance`, `add/subtract/setBalance`)
- **울캐쉬 종이 (`w` 지폐)** — 종이 아이템 + PDC 로 금액 각인. 우클릭 시 자동 입금
- **잔액 스코어보드** — 로그인 시 사이드바에 실시간 잔액 표시 (BalanceScoreboard)
- **한글 명령어** — `/출금`, `/입금`, `/송금` (Core 1.3.0+ 자동 두벌식 alias 대상)

## 의존성

- **Paper API** 1.21.1-R0.1-SNAPSHOT (`provided`)
- **Core** 1.8.0 (`provided`) — `EconomyProvider` 인터페이스 + `PlayerDataManager`
- **ServiceRegistry** — onEnable 시 `EconomyProvider` 를 등록, onDisable 시 unregister

## 빌드

```bash
cd economy
mvn clean package
# 결과: target/economy-1.0.0.jar
```

**주의**: Core 를 먼저 로컬 install 해야 함 (`cd core && mvn install`).

## 명령어

| 명령 | 설명 | 권한 |
|---|---|---|
| `/출금 <금액>` (`/withdraw`) | 잔액에서 지정 금액만큼 울캐쉬 종이(PAPER) 발행 | - |
| `/입금` (`/deposit`) | 인벤 안의 모든 울캐쉬 종이를 잔액으로 회수 | - |
| `/송금 <플레이어> <금액>` (`/transfer`) | 관리자용 임의 지급 | `economy.admin` (default: op) |

플레이어 간 자율 거래는 **울캐쉬 종이** 를 아이템으로 주고받는 방식으로 처리 — 자체 계좌 이체가 아니라 실물 지폐 유통 모델입니다.

## 울캐쉬 종이 (Cash Note)

- `Material.PAPER` + PDC (`economy:cash_note` = true, `economy:cash_amount` = long)
- 이름: `<금액>w` (금색), lore: `울캐쉬 <금액>w` + `우클릭으로 자동 입금됩니다`
- `CashNoteFactory.createNote(plugin, amount)` 로 생성
- `CashNoteFactory.isCashNote(item)` / `getAmount(item)` 로 식별

`CashNoteListener` 가 `PlayerInteractEvent` 를 훅해 우클릭 시 즉시 잔액에 합산 + 아이템 소모.

## 스코어보드

`BalanceScoreboard` 가 각 플레이어에게 개별 사이드바를 부여해 잔액을 표시합니다.
잔액 변경 시 `plugin.getBalanceScoreboard().update(player)` 호출로 갱신.
Core 의 `BalanceChangeEvent` 를 구독해 자동 갱신할 수도 있음 (구현 확장 여지).

## 다른 플러그인에서 사용 (외부 연동)

Economy 는 Core 의 `ServiceRegistry` 에 `EconomyProvider` 로 등록됩니다. Vault 대체 경로.

```java
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;

EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
if (eco != null) {
    long balance = eco.getBalance(player.getUniqueId());
    if (eco.subtractBalance(player.getUniqueId(), 1000L)) {
        // 결제 성공
    }
}
```

**주의**: `EconomyProvider` 는 Core 가 제공하는 인터페이스입니다. 다른 플러그인은 Economy 에 직접 의존할 필요 없이 Core 만 의존하면 됨. `plugin.yml` 에 `depend: [Core]` 또는 `softdepend: [Economy]` (호출 안전용) 정도.

## 파일 구조

```
economy/
├── src/main/java/com/exit/economy/
│   ├── EconomyPlugin.java             onEnable/Disable + ServiceRegistry
│   ├── EconomyProviderImpl.java       PlayerDataManager 위임 구현
│   ├── commands/
│   │   ├── WithdrawCommand.java       /출금
│   │   ├── DepositCommand.java        /입금
│   │   └── TransferCommand.java       /송금 (admin)
│   ├── display/
│   │   └── BalanceScoreboard.java     사이드바
│   └── items/
│       ├── CashNoteFactory.java       PDC 기반 지폐 생성/식별
│       └── CashNoteListener.java      우클릭 자동 입금
└── src/main/resources/plugin.yml
```

## 라이선스

MIT (top-level `LICENSE` 참고).
