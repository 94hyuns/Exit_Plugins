package com.exit.customitems.lamp;

/**
 * 램프 한 번 적용 결과. {@link LampHandler#applyLamp} 와 BulkLampGUI 둘 다 같은 흐름을 거치며
 * 호출자가 메시지를 어떻게 보낼지(채팅 vs 액션바) 결정할 수 있도록 결과만 반환한다.
 *
 * @param success     적용 성공 여부.
 * @param errorMessage 실패 사유 (success=false 일 때). 성공 시 null.
 * @param rolledLines 부여된 인챈트 줄 수 (성공 시).
 * @param wasReroll   대상에 이미 인챈트가 있었는지 (= 리롤이었는지).
 */
public record LampApplyResult(
        boolean success,
        String errorMessage,
        int rolledLines,
        boolean wasReroll
) {
    public static LampApplyResult ok(int lines, boolean reroll) {
        return new LampApplyResult(true, null, lines, reroll);
    }

    public static LampApplyResult fail(String msg) {
        return new LampApplyResult(false, msg, 0, false);
    }
}
