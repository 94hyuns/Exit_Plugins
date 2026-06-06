package com.exit.cosmetics.mount;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어별 활성 탈것 관리.
 *
 * <ul>
 *   <li>1인 1탈 — 새 소환 시 기존 탈것 자동 despawn</li>
 *   <li>FLYING 탈것은 매 틱 시점 기반 velocity 적용 (W 키 입력 시 전진, 미입력 시 hover)</li>
 *   <li>despawn 트리거는 MountListener에서 호출 (사망/로그아웃/월드이동 등)</li>
 * </ul>
 */
public class MountManager {

    private final JavaPlugin plugin;
    private final Map<UUID, ActiveMount> active = new HashMap<>();

    public MountManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 소환. 기존 탈것이 있으면 먼저 despawn. */
    public boolean summon(Player player, MountDefinition def) {
        if (def == null) return false;
        UUID uuid = player.getUniqueId();

        despawn(uuid);

        Location spawnLoc = player.getLocation().clone();
        Entity raw;
        try {
            raw = player.getWorld().spawnEntity(spawnLoc, def.getEntityType());
        } catch (Exception e) {
            player.sendMessage(Component.text("탈것 소환에 실패했습니다: " + def.getDisplayName())
                    .color(NamedTextColor.RED));
            return false;
        }

        if (!(raw instanceof LivingEntity entity)) {
            raw.remove();
            player.sendMessage(Component.text("탈것 엔티티 타입이 올바르지 않습니다.")
                    .color(NamedTextColor.RED));
            return false;
        }

        configureEntity(entity, player, def);

        // ModelEngine 모델 attach (선택). 실패해도 vanilla 엔티티로 폴백.
        // ME mount 본(seat)에 플레이어 앉히기 시도 → 실패 시 vanilla addPassenger.
        boolean meSeated = false;
        if (def.hasModelEngine()) {
            meSeated = tryAttachModelEngine(entity, def.getModelEngineId(), player);
        }
        if (!meSeated) {
            entity.addPassenger(player);
        }

        BukkitTask flyTask = null;
        if (def.getMountType() == MountType.FLYING) {
            flyTask = startFlightTask(player, entity, def);
        }

        active.put(uuid, new ActiveMount(entity.getUniqueId(), def, flyTask));
        player.sendMessage(Component.text("[탈것] ", NamedTextColor.AQUA)
                .append(Component.text(def.getDisplayName() + " §a소환!")));
        return true;
    }

    /** 해제. 활성 탈것이 없으면 false. */
    public boolean despawn(UUID uuid) {
        ActiveMount am = active.remove(uuid);
        if (am == null) return false;
        if (am.flightTask != null) am.flightTask.cancel();
        Entity e = Bukkit.getEntity(am.entityId);
        if (e != null && !e.isDead()) {
            e.remove();
        }
        return true;
    }

    /** 플레이어가 현재 어떤 탈것을 활성화 중인지. */
    public MountDefinition getActiveDefinition(UUID uuid) {
        ActiveMount am = active.get(uuid);
        return am == null ? null : am.definition;
    }

    /** 주어진 엔티티가 활성 탈것인지. */
    public boolean isActiveMountEntity(Entity entity) {
        if (entity == null) return false;
        UUID eid = entity.getUniqueId();
        for (ActiveMount am : active.values()) {
            if (am.entityId.equals(eid)) return true;
        }
        return false;
    }

    /** 주어진 엔티티 ID로 활성 탈것을 소유한 플레이어 UUID 찾기. */
    public UUID findOwnerOfEntity(UUID entityId) {
        for (Map.Entry<UUID, ActiveMount> e : active.entrySet()) {
            if (e.getValue().entityId.equals(entityId)) return e.getKey();
        }
        return null;
    }

    /** 플러그인 종료 시 모든 활성 탈것 정리. */
    public void shutdownAll() {
        for (UUID uuid : new HashMap<>(active).keySet()) {
            despawn(uuid);
        }
    }

    // ─── 내부 ───

    private void configureEntity(LivingEntity entity, Player owner, MountDefinition def) {
        // 공통 — 체력 / 이동속도 적용
        AttributeInstance maxHp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(def.getMaxHealth());
            entity.setHealth(def.getMaxHealth());
        }
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(def.getMovementSpeed());

        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        if (entity instanceof AbstractHorse horse) {
            horse.setTamed(true);
            horse.setOwner(owner);
            horse.setAdult();
            horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

            if (horse instanceof Horse h) {
                applyHorseAppearance(h, def);
            }
        }

        if (entity instanceof Phantom phantom) {
            // setAI(false) 는 Mob의 noai 플래그를 설정해 entity를 완전히 freeze 시킴
            // → setVelocity 가 무시됨. setAware(false) 로 의사결정만 끄고 물리는 유지.
            phantom.setAware(false);
            phantom.setSize(def.getPhantomSize());
            phantom.setInvulnerable(true);
            phantom.setSilent(true);
            phantom.setGravity(false);
            phantom.setVisualFire(false);
            phantom.setFireTicks(0);
        }
    }

    private void applyHorseAppearance(Horse horse, MountDefinition def) {
        if (def.getHorseColor() != null) {
            try {
                horse.setColor(Horse.Color.valueOf(def.getHorseColor().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
        if (def.getHorseStyle() != null) {
            try {
                horse.setStyle(Horse.Style.valueOf(def.getHorseStyle().toUpperCase()));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private BukkitTask startFlightTask(Player player, LivingEntity entity, MountDefinition def) {
        final double speed = Math.max(0.1, def.getMovementSpeed());
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (entity.isDead() || !entity.isValid()) {
                despawn(player.getUniqueId());
                return;
            }
            // 팬텀 햇빛 화상 방지
            if (entity.getFireTicks() > 0) entity.setFireTicks(0);
            if (!player.isOnline()) {
                // 로그아웃은 MountListener.onQuit 에서 별도 처리
                return;
            }

            // 탑승 중이 아니면 velocity 미적용 (제자리 hover) — 자동 despawn 안함
            if (!entity.getPassengers().contains(player)) {
                entity.setVelocity(new Vector(0, 0, 0));
                return;
            }

            // 시야 방향으로 yaw/pitch 동기화 (teleport 대신 setRotation 사용 — velocity 리셋 방지)
            float yaw = player.getLocation().getYaw();
            float pitch = player.getLocation().getPitch();
            entity.setRotation(yaw, pitch);

            // 6방향 무빙 — W/S/A/D + Space/Shift
            Input input = readInput(player);
            Vector move = computeMovement(player, input);
            if (move.lengthSquared() > 0) {
                if (move.lengthSquared() > 1) move.normalize();
                entity.setVelocity(move.multiply(speed));
            } else {
                entity.setVelocity(new Vector(0, 0, 0));
            }
        }, 1L, 1L);
    }

    /**
     * 플레이어 시야 + 입력으로 비행 방향 벡터 계산.
     * <ul>
     *   <li>W (forward): 시선 벡터(피치 포함) 방향 — 내려다보면 다이브, 올려다보면 상승</li>
     *   <li>S (backward): -시선 벡터</li>
     *   <li>A (left): 시선 yaw 의 좌측 수평 벡터</li>
     *   <li>D (right): 시선 yaw 의 우측 수평 벡터</li>
     *   <li>Space (jump): +Y</li>
     *   <li>Shift (sneak): -Y</li>
     * </ul>
     * 동시 입력은 합벡터. 결과 length 가 1 초과면 normalize.
     */
    private Vector computeMovement(Player player, Input input) {
        Vector move = new Vector(0, 0, 0);
        Vector look = player.getLocation().getDirection();
        if (input.forward)  move.add(look);
        if (input.backward) move.subtract(look);
        if (input.left || input.right) {
            double yawRad = Math.toRadians(player.getLocation().getYaw());
            // MC 좌표계: yaw=0 → 시야 +Z(남쪽). 플레이어의 right = forward × up = (-cos, 0, -sin).
            // (yaw=0 일 때 right = (-1,0,0) = 서쪽 = 남쪽 보고 있을 때 오른손 방향 ✓)
            Vector right = new Vector(-Math.cos(yawRad), 0, -Math.sin(yawRad));
            if (input.right) move.add(right);
            if (input.left)  move.subtract(right);
        }
        if (input.jump)  move.add(new Vector(0, 1, 0));
        if (input.sneak) move.add(new Vector(0, -1, 0));
        return move;
    }

    /** Paper PlayerInput 리플렉션 결과 컨테이너. */
    private record Input(boolean forward, boolean backward, boolean left, boolean right,
                         boolean jump, boolean sneak) {}

    private static final Input NO_INPUT = new Input(false, false, false, false, false, false);

    /**
     * Paper 의 Player.getCurrentInput() → Input 객체에서 6방향 입력 추출.
     * Sneak 은 isShift() / isSneak() 둘 중 존재하는 메서드 사용. 미지원 환경에서는 모두 false.
     */
    private Input readInput(Player player) {
        try {
            Object inputObj = Player.class.getMethod("getCurrentInput").invoke(player);
            if (inputObj == null) return NO_INPUT;
            boolean fwd  = invokeBool(inputObj, "isForward");
            boolean back = invokeBool(inputObj, "isBackward");
            boolean left = invokeBool(inputObj, "isLeft");
            boolean right= invokeBool(inputObj, "isRight");
            boolean jump = invokeBool(inputObj, "isJump");
            boolean sneak= invokeBool(inputObj, "isSneak", "isShift");
            return new Input(fwd, back, left, right, jump, sneak);
        } catch (Throwable t) {
            return NO_INPUT;
        }
    }

    private boolean invokeBool(Object target, String... methodNames) {
        for (String name : methodNames) {
            try {
                Object r = target.getClass().getMethod(name).invoke(target);
                if (r instanceof Boolean b) return b;
            } catch (NoSuchMethodException ignored) {
                // try next
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * 내려서 옆으로 떨어진 후 우클릭으로 다시 탑승. PlayerInteractEntityEvent 에서 호출.
     * ME 모델 가진 탈것은 이미 ActiveModel 이 entity 에 attached 되어 있으므로 단순 addPassenger
     * 만 호출 (ME 가 mount 본 위치로 자동 재seat 시도 — 안 되면 vanilla 안장).
     */
    public boolean reMount(Player player) {
        UUID uuid = player.getUniqueId();
        ActiveMount am = active.get(uuid);
        if (am == null) return false;
        Entity entity = Bukkit.getEntity(am.entityId);
        if (entity == null || entity.isDead() || !entity.isValid()) return false;
        if (entity.getPassengers().contains(player)) return true;
        entity.addPassenger(player);
        return true;
    }

    private record ActiveMount(UUID entityId, MountDefinition definition, BukkitTask flightTask) {}

    // ─── ModelEngine 통합 (soft dependency) ───

    private boolean modelEngineMissingLogged = false;
    private boolean modelEngineMethodsLogged = false;

    /**
     * ModelEngine 플러그인에 reflection 으로 모델 attach + 플레이어를 ME 의 mount 본 위치에 앉힘.
     *
     * <p>핵심: ME 의 ActiveModel.getMountManager().tryToRide(player) 를 호출해야
     * bbmodel 의 seat 본(보통 "mount" / "seat" / "p:mount") 위치에 플레이어가 앉음.
     * 그냥 vanilla addPassenger 를 쓰면 baseEntity(말) 의 안장 위치에 앉아 모델과 어긋남.
     *
     * @return ME 모델 attach + mount 본에 앉히기까지 성공하면 true. false 면 호출자가 vanilla addPassenger 로 폴백.
     */
    private boolean tryAttachModelEngine(LivingEntity entity, String modelId, Player rider) {
        if (Bukkit.getPluginManager().getPlugin("ModelEngine") == null) {
            if (!modelEngineMissingLogged) {
                plugin.getLogger().info("[Mounts] ModelEngine 플러그인 없음 — model_engine_id 필드 무시.");
                modelEngineMissingLogged = true;
            }
            return false;
        }
        try {
            Class<?> apiClass = Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");

            Object modeledEntity = apiClass.getMethod("createModeledEntity",
                    org.bukkit.entity.Entity.class).invoke(null, entity);

            Object activeModel = apiClass.getMethod("createActiveModel", String.class)
                    .invoke(null, modelId);

            // addModel(ActiveModel, boolean) — 두 번째 인자는 default behavior(visual swap 포함) 적용 여부
            boolean modelAdded = false;
            for (var m : modeledEntity.getClass().getMethods()) {
                if (m.getName().equals("addModel") && m.getParameterCount() == 2) {
                    m.invoke(modeledEntity, activeModel, true);
                    modelAdded = true;
                    break;
                }
            }
            if (!modelAdded) {
                plugin.getLogger().warning("[Mounts] ModelEngine addModel 시그니처를 찾지 못함 — API 변경 가능성.");
                return false;
            }

            // ME mount 본에 플레이어 앉히기.
            // ActiveModel.getMountManager() 또는 .getMountController() 시그니처 모두 대응.
            Object mountMgr = null;
            for (var m : activeModel.getClass().getMethods()) {
                if (m.getParameterCount() == 0
                        && (m.getName().equals("getMountManager") || m.getName().equals("getMountController"))) {
                    mountMgr = m.invoke(activeModel);
                    if (mountMgr != null) break;
                }
            }
            // ME R4: getMountController() 가 Optional<IMountController> 를 반환 → unwrap.
            if (mountMgr instanceof java.util.Optional<?> opt) {
                mountMgr = opt.orElse(null);
            }
            if (mountMgr == null) {
                plugin.getLogger().info("[Mounts] ActiveModel 의 mount 매니저를 찾지 못함 — 모델에 mount/seat 본이 없을 수 있음. vanilla 안장으로 폴백.");
                return false;
            }

            // ride 메서드 후보 — 다양한 ME 버전 호환:
            //   tryToRide / mount / ride / addPassenger / forceMount
            // 파라미터 타입은 Player/LivingEntity/Entity 어느 것이든 허용 (Player 가 다 호환)
            java.util.Set<String> rideNames = java.util.Set.of(
                    "tryToRide", "mount", "ride", "addPassenger", "forceMount", "forceRide");
            java.lang.reflect.Method rideMethod = null;
            for (var m : mountMgr.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                if (!rideNames.contains(m.getName())) continue;
                Class<?> paramType = m.getParameterTypes()[0];
                if (paramType.isAssignableFrom(Player.class)) {
                    rideMethod = m;
                    break;
                }
            }
            if (rideMethod != null) {
                Object result = rideMethod.invoke(mountMgr, rider);
                if (result instanceof Boolean b && !b) {
                    plugin.getLogger().info("[Mounts] ME " + rideMethod.getName()
                            + " 가 false 반환 — mount 본 없거나 점유. vanilla 폴백.");
                    return false;
                }
                return true;
            }

            // 못 찾았으면 사용 가능한 메서드 목록을 한 번 로그로 출력 (디버그)
            if (!modelEngineMethodsLogged) {
                modelEngineMethodsLogged = true;
                StringBuilder sb = new StringBuilder();
                sb.append("[Mounts] ME mount manager (").append(mountMgr.getClass().getName()).append(") 의 사용 가능한 메서드:");
                for (var m : mountMgr.getClass().getMethods()) {
                    if (m.getDeclaringClass() == Object.class) continue;
                    if (m.getParameterCount() > 2) continue;
                    sb.append("\n  ").append(m.getName()).append("(");
                    var pts = m.getParameterTypes();
                    for (int i = 0; i < pts.length; i++) {
                        if (i > 0) sb.append(",");
                        sb.append(pts[i].getSimpleName());
                    }
                    sb.append(") -> ").append(m.getReturnType().getSimpleName());
                }
                plugin.getLogger().warning(sb.toString());
            } else {
                plugin.getLogger().warning("[Mounts] ME mount manager 의 ride 메서드를 찾지 못함 — vanilla 폴백.");
            }
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("[Mounts] ModelEngine API 클래스 로드 실패: " + e.getMessage());
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Mounts] ModelEngine 모델 attach 실패 (id=" + modelId + "): " + t.getMessage());
            return false;
        }
    }
}
