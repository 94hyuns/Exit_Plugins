package com.exit.customitems.lamp.enchant;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 인챈트 효과 리스너에서 공통으로 쓰는 조회 헬퍼.
 *
 * <p>"어느 슬롯을 조회할지"를 Trigger 기준으로 캡슐화하고,
 * 각 리스너는 "주손/방어구 판정"을 신경 쓸 필요 없이
 * "이 이벤트에서 이 트리거의 인챈트 있는가?"만 물으면 됨.
 */
public class EnchantDispatcher {

    private final EnchantStorage storage;

    public EnchantDispatcher(EnchantStorage storage) {
        this.storage = storage;
    }

    /**
     * 아이템에 특정 키의 인챈트가 걸려있는지 조회.
     * 리스너가 "이 도구의 이 인챈트만 보고 싶다"고 할 때 사용.
     * non-stackable 인챈트 조회에 적합 (하나만 있다는 보장).
     */
    public Optional<RolledEnchant> findInItem(ItemStack item, NamespacedKey key) {
        if (item == null) return Optional.empty();
        for (RolledEnchant r : storage.load(item)) {
            if (r.enchant().getKey().equals(key)) return Optional.of(r);
        }
        return Optional.empty();
    }

    /**
     * 특정 키의 인챈트를 아이템에서 <b>모두</b> 찾는다.
     * stackable 인챈트가 여러 줄 붙어있을 수 있으므로 전리품/뼛가루/작물·씨앗 추가/경험치 등은
     * 이 메서드로 조회해서 각각 독립적으로 발동시켜야 한다.
     */
    public List<RolledEnchant> findAllInItem(ItemStack item, NamespacedKey key) {
        if (item == null) return List.of();
        List<RolledEnchant> out = new ArrayList<>();
        for (RolledEnchant r : storage.load(item)) {
            if (r.enchant().getKey().equals(key)) out.add(r);
        }
        return out;
    }

    /** 아이템의 모든 램프 인챈트 조회. */
    public List<RolledEnchant> loadAll(ItemStack item) {
        return storage.load(item);
    }

    /**
     * 플레이어가 지금 어떤 슬롯으로 Trigger 종류의 인챈트를 발동시킬 수 있는지.
     * (여러 슬롯에서 인챈트가 걸렸을 수도 있으므로 List 반환)
     */
    public List<RolledEnchant> collectActive(Player player, EnchantTrigger trigger) {
        List<RolledEnchant> out = new ArrayList<>();
        switch (trigger) {
            case ATTACK, LIFE_ACTION -> addFrom(out, player.getInventory().getItemInMainHand(), trigger);
            case DAMAGED, PASSIVE -> {
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    addFrom(out, armor, trigger);
                }
            }
        }
        return out;
    }

    private void addFrom(List<RolledEnchant> out, ItemStack item, EnchantTrigger trigger) {
        if (item == null) return;
        for (RolledEnchant r : storage.load(item)) {
            if (r.enchant().getTrigger() == trigger) out.add(r);
        }
    }
}
