package com.exit.customitems.lamp;

import com.exit.customitems.lamp.enchant.EnchantRoller;
import com.exit.customitems.lamp.enchant.EnchantStorage;
import com.exit.customitems.lamp.enchant.LoreRenderer;
import com.exit.customitems.lamp.enchant.RolledEnchant;
import com.exit.customitems.lamp.mutation.MutationApplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 램프 사용 트리거: <b>주손에 램프 + 보조손에 대상 도구 + 우클릭</b>.
 *
 * <p>일반 람프(LIFE/COMBAT): {@link #applyLamp(Player, ItemStack, ItemStack, boolean)} 호출.
 * <p>변성램프(MUTATION): {@link MutationApplier#apply(Player, ItemStack)} 호출.
 */
public class LampHandler implements Listener {

    private final LampItem lampItem;
    private final EnchantRoller roller;
    private final EnchantStorage storage;
    private final LoreRenderer loreRenderer;
    private final LampConfig config;
    private final MutationApplier mutationApplier;

    public LampHandler(LampItem lampItem, EnchantRoller roller,
                       EnchantStorage storage, LoreRenderer loreRenderer,
                       LampConfig config, MutationApplier mutationApplier) {
        this.lampItem = lampItem;
        this.roller = roller;
        this.storage = storage;
        this.loreRenderer = loreRenderer;
        this.config = config;
        this.mutationApplier = mutationApplier;
    }

    /**
     * 일반 람프(LIFE/COMBAT) 1개를 대상 아이템에 적용.
     *
     * <p>변성램프는 이 메서드를 거치지 않고 {@link MutationApplier} 가 처리.
     */
    public LampApplyResult applyLamp(Player player, ItemStack lamp, ItemStack target, boolean playSound) {
        if (lamp == null || lamp.getType().isAir()) {
            return LampApplyResult.fail("램프가 없습니다.");
        }
        LampType type = lampItem.getTypeOf(lamp);
        if (type == null) {
            return LampApplyResult.fail("이 아이템은 램프가 아닙니다.");
        }
        if (type == LampType.MUTATION) {
            return LampApplyResult.fail("변성램프는 일반 적용 경로를 사용할 수 없습니다.");
        }
        if (target == null || target.getType().isAir()) {
            return LampApplyResult.fail("적용할 도구가 없습니다.");
        }
        if (lampItem.isLamp(target)) {
            return LampApplyResult.fail("램프에는 램프를 사용할 수 없습니다.");
        }
        if (!type.isApplicableTo(target)) {
            return LampApplyResult.fail(type.getDisplayName() + "은(는) 이 아이템에 사용할 수 없습니다.");
        }
        if (mutationApplier.isMutated(target)) {
            return LampApplyResult.fail("변성된 아이템에는 일반 람프를 사용할 수 없습니다.");
        }

        List<RolledEnchant> rolled = roller.roll(target, type.getCategory());
        if (rolled.isEmpty()) {
            return LampApplyResult.fail("적용 가능한 인챈트 풀이 비어있습니다. (관리자에게 문의)");
        }

        boolean isReroll = storage.has(target);
        storage.save(target, rolled);
        loreRenderer.render(target, rolled);

        lamp.setAmount(lamp.getAmount() - 1);

        if (playSound && player != null) {
            player.playSound(player.getLocation(),
                    config.getRollSound(), config.getRollVolume(), config.getRollPitch());
        }

        return LampApplyResult.ok(rolled.size(), isReroll);
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!lampItem.isLamp(mainHand)) return;

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack target = player.getInventory().getItemInOffHand();
        LampType type = lampItem.getTypeOf(mainHand);

        if (type == LampType.MUTATION) {
            handleMutation(player, mainHand, target);
            return;
        }

        LampApplyResult result = applyLamp(player, mainHand, target, true);
        if (!result.success()) {
            player.sendMessage(Component.text(result.errorMessage()).color(NamedTextColor.RED));
            return;
        }
        Component prefix = Component.text(result.wasReroll() ? "[리롤] " : "[인챈트] ")
                .color(NamedTextColor.LIGHT_PURPLE);
        player.sendMessage(prefix.append(
                Component.text("람프 인챈트 " + result.rolledLines() + "줄이 부여되었습니다.")
                        .color(NamedTextColor.GREEN)));
    }

    private void handleMutation(Player player, ItemStack lamp, ItemStack target) {
        if (target == null || target.getType().isAir()) {
            player.sendMessage(Component.text("적용할 무기를 보조손에 들어주세요.").color(NamedTextColor.RED));
            return;
        }
        if (lampItem.isLamp(target)) {
            player.sendMessage(Component.text("람프에는 변성램프를 사용할 수 없습니다.").color(NamedTextColor.RED));
            return;
        }
        if (!LampType.MUTATION.isApplicableTo(target)) {
            player.sendMessage(Component.text("변성램프는 무기·방어구에만 사용할 수 있습니다.").color(NamedTextColor.RED));
            return;
        }
        if (mutationApplier.isMutated(target)) {
            player.sendMessage(Component.text("이미 변성된 무기입니다. 다시 적용할 수 없습니다.").color(NamedTextColor.RED));
            return;
        }
        if (!storage.has(target)) {
            player.sendMessage(Component.text("변성램프는 기존 람프 인챈트가 있는 무기에만 사용할 수 있습니다.").color(NamedTextColor.RED));
            return;
        }

        // 람프 소모는 outcome 과 무관하게 진행 (실패/파괴여도 소진)
        lamp.setAmount(lamp.getAmount() - 1);
        player.playSound(player.getLocation(),
                config.getRollSound(), config.getRollVolume(), config.getRollPitch());

        MutationApplier.Outcome outcome = mutationApplier.apply(player, target);
        switch (outcome) {
            case SUCCESS -> player.sendMessage(Component.text("[변성 성공] 보너스 인챈트가 추가되었습니다!")
                    .color(NamedTextColor.GOLD));
            case MEGA_SUCCESS -> player.sendMessage(Component.text("[변성 초대박!!] UNIQUE 2개가 한꺼번에 부여되었습니다!")
                    .color(NamedTextColor.GOLD));
            case FAIL -> player.sendMessage(Component.text("[변성 실패] 보너스 없이 변성-락만 걸렸습니다.")
                    .color(NamedTextColor.YELLOW));
            case DESTROYED -> {
                target.setAmount(0);
                player.sendMessage(Component.text("[변성 파괴] 아이템이 파괴되었습니다...")
                        .color(NamedTextColor.DARK_RED));
            }
        }
    }
}
