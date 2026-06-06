package com.exit.customitems.lamp;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * 플러그인이 사용하는 모든 NamespacedKey 를 중앙에서 관리.
 * 오타 방지 및 키 변경 시 한 곳만 수정하면 됨.
 */
public final class LampKeys {

    /** 이 아이템이 램프인지 + 어떤 타입인지. 값: "LIFE" / "COMBAT" */
    public final NamespacedKey lampType;

    /**
     * 도구에 저장되는 램프 인챈트 데이터.
     * 포맷: "{enchantKey}|{v1},{v2},...;{enchantKey2}|{v1};..."
     * 예: "customitems:lifesteal|50;customitems:critrate|300"
     */
    public final NamespacedKey enchants;

    /**
     * 램프 인챈트로 인해 현재 아이템 로어에 추가된 라인 수.
     * 리롤/제거 시 기존 램프 로어 라인만 정확히 걷어내기 위해 사용.
     */
    public final NamespacedKey loreLineCount;

    /**
     * 변성램프가 적용된 아이템 표시. BYTE 1 = 변성됨.
     * 변성-락이 걸리면 일반 람프 / 2차 변성램프 적용 불가.
     */
    public final NamespacedKey lampMutated;

    public LampKeys(Plugin plugin) {
        this.lampType      = new NamespacedKey(plugin, "lamp_type");
        this.enchants      = new NamespacedKey(plugin, "lamp_enchants");
        this.loreLineCount = new NamespacedKey(plugin, "lamp_lore_lines");
        this.lampMutated   = new NamespacedKey(plugin, "lamp_mutated");
    }
}
