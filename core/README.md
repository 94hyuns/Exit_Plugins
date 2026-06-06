# Core 플러그인 (v1.0.0 → 1.1.0)

서버 공통 Core 플러그인. 모든 커스텀 플러그인의 의존성 루트.

## 주요 변경 (1.1.0)

새 Provider 인터페이스 4개 추가:

### 1. `FishShopProvider` (api/FishShopProvider.java)
낚시 상점 GUI 진입점. Fishing 플러그인이 구현, Shop의 FISHING NPC가 호출.

```java
void openShop(Player player);
```

### 2. `CropItemProvider` + `CropInfo` (api/CropItemProvider.java, CropInfo.java)
작물 씨앗/열매 발급 및 PDC 기반 식별. Farming 구현, Shop 사용.

```java
List<CropInfo> listCrops();
ItemStack createSeed(String cropId, int amount);
String identifySeed(ItemStack item);
ItemStack createFruit(String cropId, int amount, boolean premium);
String identifyFruit(ItemStack item);
boolean isPremium(ItemStack item);
```

### 3. `FarmlandTicketProvider` (api/FarmlandTicketProvider.java)
경작지 티켓 아이템 발급 및 식별. Farming 구현, Shop이 구매 시 호출.

```java
ItemStack createTicket(int amount);
boolean isTicket(ItemStack item);
```

## 모든 플러그인 업그레이드 필요

Core 1.0.0 → 1.1.0은 **인터페이스 추가**만 있어 기존 구현 플러그인 호환되지만, 모든 플러그인의 pom.xml에서 Core 의존성 버전을 **반드시** 업데이트해야 함:

```xml
<dependency>
  <groupId>com.exit</groupId>
  <artifactId>core</artifactId>
  <version>1.1.0</version>
  <scope>provided</scope>
</dependency>
```

## 빌드

Core 는 `net.minecraft.*` 직접 import (FakePlayer NPC 등) 때문에 `paper-nms-maven-plugin` 의존. **최초 빌드 전 1회 init 필수**:

```bash
cd core
mvn ca.bkaw:paper-nms-maven-plugin:1.4.11-SNAPSHOT:init   # 1회만 (NMS 매핑 초기화)
mvn install                                                # 로컬 ~/.m2에 등록 (다른 플러그인이 참조)
```

init 안 하면 `net.minecraft.*` 심볼 resolve 실패로 컴파일 에러.

## 잔재 파일

`com/server/core/*` 은 레거시 중복 패키지로, `plugin.yml`은 `com.exit.core.CorePlugin`만 참조.
추후 정리 가능하지만 현재 영향도 없어서 유지.
