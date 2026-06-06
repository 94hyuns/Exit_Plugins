변성램프 리소스팩 추가 파일
================================

ExitItemPack.zip 의 다음 경로에 이 폴더의 파일들을 덮어쓰기 / 추가:

  assets/minecraft/items/book.json                 (CMD 1003 매핑 추가 — 덮어쓰기)
  assets/minecraft/models/item/lamp_mutation.json  (신규)
  assets/minecraft/textures/item/lamp_mutation.png (HighQuality_Lamp_ResourcePack 출처 정식본, 2026-05-12 교체)

주의:
  - zip 내부 경로는 반드시 forward-slash (/) 사용. PowerShell Compress-Archive 금지.
  - 7-Zip 또는 Python zipfile 모듈 권장.
  - lamp_mutation.png 는 HighQuality_Lamp_ResourcePack 의 lamp_battle.png 를
    그대로 채택 (2026-05-12 교체). 이전: lamp_battle.png 곱연산 임시본.

서버에 적용:
  - 리소스팩 재배포 → 클라이언트 재접속.
  - 책 아이템에 CustomModelData=1003 가 들어가면 금색 람프로 보임.
