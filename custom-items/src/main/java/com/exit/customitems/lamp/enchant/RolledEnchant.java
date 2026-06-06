package com.exit.customitems.lamp.enchant;

/**
 * "어떤 인챈트가 어떤 값들로 롤되었는가" 한 건.
 *
 * @param enchant 롤된 인챈트 정의
 * @param values  각 ValueSpec 순서대로 결정된 정수(×100) 값들
 */
public record RolledEnchant(CustomEnchant enchant, int[] values) {
    public RolledEnchant {
        if (enchant == null) throw new IllegalArgumentException("enchant is null");
        if (values == null) throw new IllegalArgumentException("values is null");
        if (values.length != enchant.getValueSpecs().size()) {
            throw new IllegalArgumentException(
                "values.length(" + values.length + ") != specs.size("
                    + enchant.getValueSpecs().size() + ") for " + enchant.getKey());
        }
    }
}
