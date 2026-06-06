package com.exit.customitems.lamp;

import com.exit.customitems.lamp.enchant.EnchantCategory;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.inventory.ItemStack;

/**
 * 램프 타입 정의. 1단계에서는 책(BOOK) + CustomModelData로 구분하고,
 * 리소스팩 적용 시 CustomModelData 값을 그대로 사용한다.
 */
public enum LampType {
    LIFE(
        "생활 램프",
        NamedTextColor.GREEN,
        1001,
        EnchantCategory.LIFE,
        "곡괭이·괭이·삽·낚싯대에 사용 가능"
    ),
    COMBAT(
        "전투 램프",
        NamedTextColor.RED,
        1002,
        EnchantCategory.COMBAT,
        "무기·방어구에 사용 가능"
    ),
    MUTATION(
        "변성 램프",
        NamedTextColor.GOLD,
        1003,
        EnchantCategory.COMBAT,
        "기존 람프 인챈트가 있는 무기에 사용. 70% 성공/20% 실패/10% 파괴"
    );

    private final String displayName;
    private final TextColor color;
    private final int customModelData;
    private final EnchantCategory category;
    private final String description;

    LampType(String displayName, TextColor color, int customModelData,
             EnchantCategory category, String description) {
        this.displayName = displayName;
        this.color = color;
        this.customModelData = customModelData;
        this.category = category;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public TextColor getColor()    { return color; }
    public int getCustomModelData(){ return customModelData; }
    public EnchantCategory getCategory() { return category; }
    public String getDescription() { return description; }

    /** 이 램프 타입이 해당 아이템에 사용 가능한지. */
    public boolean isApplicableTo(ItemStack target) {
        return switch (this) {
            case LIFE     -> ToolCategory.isLifeApplicable(target);
            case COMBAT   -> ToolCategory.isCombatApplicable(target);
            // 변성램프: 무기/방어구. MutationApplier 가 세부 조건(기존 람프 인챈트 보유, 락 여부) 추가 검증.
            case MUTATION -> ToolCategory.isCombatApplicable(target);
        };
    }

    /** 문자열 파싱 (명령어용). */
    public static LampType fromString(String s) {
        if (s == null) return null;
        try {
            return LampType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
