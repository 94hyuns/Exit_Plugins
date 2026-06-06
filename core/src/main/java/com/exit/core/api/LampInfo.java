package com.exit.core.api;

/**
 * 램프 타입 메타데이터. 상점 등 외부 플러그인이 카탈로그를 구성할 때 사용.
 *
 * @param typeId      램프 타입 식별자 ("LIFE" / "COMBAT" 등). 대소문자 구분 없이 취급 권장.
 * @param displayName 사용자에게 보여줄 이름 (예: "생활 램프")
 * @param description 간단한 설명 (예: "곡괭이·괭이·삽·낚싯대에 사용 가능")
 */
public record LampInfo(String typeId, String displayName, String description) {}
