package com.exit.customitems.dummy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * 깔끔한 허수아비 v2 — vanilla COW 기반.
 *
 * 사양:
 * - 최대 HP 500, 머리 위에 "허수아비 §a500/500" 형식으로 표시 (AlwaysVisible)
 * - NoAI, Invincible 아님 (데미지는 받지만 죽지 않음)
 * - PDC 마커: training_dummy_v2 + max_hp
 * - 2초마다 heartbeat 로 풀피 회복
 * - 데미지 받으면 attacker 에게 채팅 메시지 + name 갱신 + HP 0 이하 시 즉시 풀피
 *
 * MM 의존 X. 순수 Bukkit API.
 */
public final class NewDummyManager {

    public static final int MAX_HP = 500;
    private static final long HEARTBEAT_TICKS = 40L; // 2초
    private static final String ME_MODEL_ID = "dummy_normal";

    private final JavaPlugin plugin;
    private final DummyKeys keys;

    public NewDummyManager(JavaPlugin plugin, DummyKeys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    public void start() {
        // 2초마다 모든 더미 풀피 회복 + 표시 갱신
        Bukkit.getScheduler().runTaskTimer(plugin, this::healAllDummies, HEARTBEAT_TICKS, HEARTBEAT_TICKS);
    }

    public Cow spawn(Location loc) {
        Cow cow = loc.getWorld().spawn(loc, Cow.class, c -> {
            c.setAI(false);
            c.setSilent(true);
            c.setPersistent(true);
            c.setRemoveWhenFarAway(false);
            // GENERIC_MAX_HEALTH 최대 1024 안전 마진
            var attr = c.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(MAX_HP);
            c.setHealth(MAX_HP);
            c.setCustomNameVisible(true);
            c.setInvulnerable(false);
            // PDC 마커
            var pdc = c.getPersistentDataContainer();
            pdc.set(keys.markerV2, PersistentDataType.BYTE, (byte) 1);
        });
        updateName(cow, MAX_HP);
        // ME 모델 attach (bv_dummy 스킨 재활용) — 1 tick 뒤 (entity 등록 후)
        Bukkit.getScheduler().runTaskLater(plugin, () -> attachModelEngineModel(cow), 1L);
        return cow;
    }

    /**
     * ModelEngine API 리플렉션 호출로 vanilla COW 에 dummy_normal 모델 attach.
     * Cosmetics MountManager 와 동일 패턴.
     */
    private void attachModelEngineModel(Entity entity) {
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
            Object modeledEntity = apiClass.getMethod("createModeledEntity",
                    org.bukkit.entity.Entity.class).invoke(null, entity);
            Object activeModel = apiClass.getMethod("createActiveModel", String.class)
                    .invoke(null, ME_MODEL_ID);
            for (var m : modeledEntity.getClass().getMethods()) {
                if (m.getName().equals("addModel") && m.getParameterCount() == 2) {
                    m.invoke(modeledEntity, activeModel, true);  // visual swap = true → vanilla cow 외형 가림
                    return;
                }
            }
            plugin.getLogger().warning("[Dummy] ModelEngine addModel 시그니처 미발견 — ME 버전 확인 필요");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Dummy] ModelEngine 미설치 — vanilla COW 외형으로 표시");
        } catch (Throwable t) {
            plugin.getLogger().warning("[Dummy] ME 모델 attach 실패: " + t.getMessage());
        }
    }

    /** 데미지 받았을 때 호출. attacker 에게 메시지 + 표시 갱신 + HP 0 이하면 풀피. */
    public void onDamaged(LivingEntity dummy, double damage, org.bukkit.entity.Player attacker) {
        // EntityDamageEvent 의 setDamage 가 이미 적용 후라 dummy.getHealth() 는 다음 tick 에 갱신됨.
        // 직접 계산: 현재 HP - damage
        double newHp = dummy.getHealth() - damage;
        if (newHp <= 0) newHp = MAX_HP;  // 죽지 않고 풀피

        // 다음 tick 에 setHealth — damage event 처리 후 적용되도록
        final double finalHp = newHp;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!dummy.isValid() || dummy.isDead()) return;
            dummy.setHealth(Math.min(finalHp, MAX_HP));
            updateName(dummy, (int) Math.round(finalHp));
        });

        // attacker 메시지
        if (attacker != null) {
            attacker.sendMessage(Component.text("[허수아비] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("-" + String.format("%.1f", damage), NamedTextColor.RED))
                    .append(Component.text(" 데미지", NamedTextColor.GRAY)));
        }
    }

    private void updateName(Entity e, int currentHp) {
        if (!(e instanceof LivingEntity le)) return;
        int hp = Math.max(0, Math.min(currentHp, MAX_HP));
        NamedTextColor color;
        double pct = (double) hp / MAX_HP;
        if (pct > 0.6) color = NamedTextColor.GREEN;
        else if (pct > 0.3) color = NamedTextColor.YELLOW;
        else color = NamedTextColor.RED;
        Component name = Component.text("허수아비 ", NamedTextColor.GRAY)
                .append(Component.text(hp + "/" + MAX_HP, color))
                .append(Component.text(" ❤", NamedTextColor.RED));
        le.customName(name);
        le.setCustomNameVisible(true);
    }

    private void healAllDummies() {
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof Cow cow)) continue;
                if (!cow.getPersistentDataContainer().has(keys.markerV2, PersistentDataType.BYTE)) continue;
                if (cow.getHealth() < MAX_HP) {
                    cow.setHealth(MAX_HP);
                }
                updateName(cow, MAX_HP);
            }
        }
    }

    public static boolean isNewDummy(Entity e, DummyKeys keys) {
        return e instanceof Cow && e.getPersistentDataContainer().has(keys.markerV2, PersistentDataType.BYTE);
    }
}
