package com.exit.farming.cooking;

import com.exit.core.api.CropItemProvider;
import com.exit.core.registry.ServiceRegistry;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Optional;

/**
 * Cooking Pack 의 cooking_pot mob 에 우리 커스텀 작물 (BEETROOT + PDC cropId) 을
 * 던지면 cooking pot 의 vanilla 재료 score 에 매핑되도록 우회 통합.
 *
 * <p>원리:
 * <ul>
 *   <li>cooking pack 의 detection (`cookingpotBREAD` 등) 은 {@code holding{m=<Material>}} 로
 *       vanilla Material 만 검사 → 우리 작물(전부 BEETROOT)은 매칭 X.</li>
 *   <li>우리는 PlayerInteractEntityEvent 를 가로채서 cropId 추출 →
 *       매핑 표 lookup → MythicMobs 의 wrapper skill (exitCookingScoreXxx) cast.</li>
 *   <li>wrapper skill (plugins/MythicMobs/Skills/exit_cooking_integration.yml) 이
 *       cooking pack 의 cookingpotPORK 와 동일 패턴으로 score / ingredients / splash 처리.</li>
 *   <li>ItemStack 1개 소비는 우리가 직접 처리 (consumeslot 우회).</li>
 * </ul>
 *
 * <p>매핑 정책 (B1+ 분류 그룹): 13작물 → 5 cooking pot score
 * (bread, potato, kelp, apple, sweet_berries). 1대다 허용.
 */
public final class CookingIntegrationListener implements Listener {

    private static final String COOKING_POT_TYPE = "cooking_pot";
    private static final String STANCE_ONFIRE = "onfire";

    /** cropId → cooking pot score 그룹 매핑. */
    private static final Map<String, String> CROP_TO_SCORE = Map.ofEntries(
            // 곡물 → bread
            Map.entry("wheat", "bread"),
            Map.entry("corn", "bread"),
            // 뿌리채소 → potato
            Map.entry("potato", "potato"),
            Map.entry("carrot", "potato"),
            Map.entry("beetroot", "potato"),
            Map.entry("garlic", "potato"),
            // 잎채소 → kelp
            Map.entry("cauliflower", "kelp"),
            Map.entry("leek", "kelp"),
            Map.entry("pea", "kelp"),
            // 열매 → apple
            Map.entry("tomato", "apple"),
            // 베리 → sweet_berries
            Map.entry("cranberry", "sweet_berries"),
            Map.entry("blueberry", "sweet_berries"),
            Map.entry("grape", "sweet_berries")
    );

    /** score → MythicMobs wrapper skill 이름. */
    private static final Map<String, String> SCORE_TO_SKILL = Map.of(
            "bread", "exitCookingScoreBread",
            "potato", "exitCookingScorePotato",
            "kelp", "exitCookingScoreKelp",
            "apple", "exitCookingScoreApple",
            "sweet_berries", "exitCookingScoreSweetBerries"
    );

    private final JavaPlugin plugin;

    public CookingIntegrationListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        // 메인핸드만 처리 (오프핸드 중복 방지)
        if (e.getHand() != EquipmentSlot.HAND) return;

        Player player = e.getPlayer();
        Entity ent = e.getRightClicked();

        // MM ActiveMob 인지 + cooking_pot type 인지 확인
        ActiveMob am = getActiveMob(ent);
        if (am == null) return;
        if (!COOKING_POT_TYPE.equals(am.getType().getInternalName())) return;

        // 손에 든 아이템이 우리 커스텀 작물 열매인지
        ItemStack hand = player.getInventory().getItemInMainHand();
        CropItemProvider crops = ServiceRegistry.get(CropItemProvider.class).orElse(null);
        if (crops == null) return;
        String cropId = crops.identifyFruit(hand);
        if (cropId == null) return;  // vanilla 재료 → cooking pack 본 트리거가 처리

        String score = CROP_TO_SCORE.get(cropId);
        if (score == null) return;  // 매핑 없는 작물 → 무반응
        String skillName = SCORE_TO_SKILL.get(score);
        if (skillName == null) return;

        // stance 검사는 wrapper skill 측에서 처리하지 않음 (race condition 회피).
        // 우리 listener 가 cooking_pot type 검증만으로 cast 진행.

        // wrapper skill cast (caster=cooking_pot mob, trigger=player)
        boolean ok = castWrapperSkill(am, skillName, player);
        if (!ok) {
            player.sendMessage(Component.text(
                    "[Cooking] 요리 시스템 오류 — 관리자 문의", NamedTextColor.RED));
            return;
        }

        // ItemStack 1개 소비 + interact 동작 차단
        hand.setAmount(hand.getAmount() - 1);
        e.setCancelled(true);
    }

    /** MM API 변경에 안전한 ActiveMob 조회. 실패 시 null. */
    private ActiveMob getActiveMob(Entity ent) {
        try {
            MythicBukkit mm = MythicBukkit.inst();
            if (mm == null) return null;
            Optional<ActiveMob> opt = mm.getMobManager().getActiveMob(ent.getUniqueId());
            return opt.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** stance 조회 — MM 버전별 메서드 차이 회피. 못 읽으면 빈 문자열. */
    private String safeStance(ActiveMob am) {
        try {
            // MM 5.x: ActiveMob.getStance() → Stance 객체. Stance.getName() 으로 이름
            Object stance = am.getClass().getMethod("getStance").invoke(am);
            if (stance == null) return "";
            // Stance.getName() 호출
            try {
                Object name = stance.getClass().getMethod("getName").invoke(stance);
                return name == null ? "" : name.toString().toLowerCase();
            } catch (Throwable t2) {
                return stance.toString().toLowerCase();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * MythicMobs API 로 wrapper skill cast.
     * caster = cooking_pot mob entity, trigger = player.
     * API 호출 실패 시 콘솔 명령어 fallback.
     */
    private boolean castWrapperSkill(ActiveMob am, String skillName, Player trigger) {
        try {
            MythicBukkit mm = MythicBukkit.inst();
            if (mm == null) return false;
            Entity caster = am.getEntity().getBukkitEntity();
            return mm.getAPIHelper().castSkill(
                    caster, skillName,
                    trigger,                              // trigger entity
                    caster.getLocation(),                 // origin
                    null, null,
                    1.0f
            );
        } catch (Throwable t) {
            plugin.getLogger().warning("[Farming/Cooking] castSkill 실패: " + t.getMessage());
            return false;
        }
    }
}
