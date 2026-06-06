package com.exit.gamble.slot.world;

import com.exit.core.api.NpcService;
import com.exit.core.api.NpcSpawnSpec;
import com.exit.core.registry.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

public class SlotMachineManager {

    private static final String MACHINES_FILE = "machines.yml";
    private static final String TAG_PREFIX = "gamble_slot_";

    private final Plugin plugin;
    private final Map<String, SlotMachine> machines = new HashMap<>();
    private final File file;

    public SlotMachineManager(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), MACHINES_FILE);
    }

    public Optional<SlotMachine> get(String id) {
        return Optional.ofNullable(machines.get(id));
    }

    public java.util.Collection<SlotMachine> all() {
        return machines.values();
    }

    public Optional<SlotMachine> findByNpcId(String npcId) {
        for (SlotMachine m : machines.values()) {
            if (npcId.equals(m.npcId())) return Optional.of(m);
        }
        return Optional.empty();
    }

    public Optional<SlotMachine> findNearest(Location loc, double maxDistance) {
        SlotMachine best = null;
        double bestSq = maxDistance * maxDistance;
        for (SlotMachine m : machines.values()) {
            if (!m.anchor().getWorld().equals(loc.getWorld())) continue;
            double dsq = m.anchor().distanceSquared(loc);
            if (dsq <= bestSq) { bestSq = dsq; best = m; }
        }
        return Optional.ofNullable(best);
    }

    public synchronized boolean tryAcquire(SlotMachine machine, Player player) {
        if (machine.isOccupied() && !player.getUniqueId().equals(machine.currentUser())) return false;
        machine.setCurrentUser(player.getUniqueId());
        return true;
    }

    public synchronized void release(SlotMachine machine) {
        machine.setCurrentUser(null);
        machine.setCurrentBet(0);
        machine.setSpinning(false);
    }

    public SlotMachine spawn(String id, Player admin) {
        // 시선이 가리키는 블록을 기준으로 정렬. 5블록 이내, 비어있지 않은 블록 필요.
        org.bukkit.block.Block target = admin.getTargetBlockExact(5);
        if (target == null || target.isEmpty()) {
            admin.sendMessage(net.kyori.adventure.text.Component.text(
                    "5블록 이내에서 바라보는 흰색 콘크리트(또는 어떤 블록)가 필요합니다",
                    net.kyori.adventure.text.format.NamedTextColor.RED));
            return null;
        }

        Vector forward = admin.getEyeLocation().getDirection().clone().setY(0).normalize();
        float displayYaw = (admin.getLocation().getYaw() + 180f) % 360f;

        // 블록 중심
        Location centerBlock = target.getLocation().add(0.5, 0.5, 0.5);
        // 플레이어 쪽 면 (블록 앞면 0.6만큼 튀어나오게 — 0.5 면 50/50 묻힘)
        Vector towardPlayer = forward.clone().multiply(-0.6);
        Location anchor = centerBlock.clone().add(towardPlayer);

        // 1블록 간격으로 좌우 배치 (블록 grid 에 자동 정렬)
        Vector right = new Vector(-forward.getZ(), 0, forward.getX()).normalize();
        Location[] reelLocs = new Location[3];
        for (int i = 0; i < 3; i++) {
            double offset = (i - 1) * 1.0;
            Location l = anchor.clone().add(right.clone().multiply(offset));
            l.setYaw(displayYaw);
            l.setPitch(0f);
            reelLocs[i] = l;
        }
        // 상태 텍스트는 중앙 릴 위 1블록 (red 콘크리트 부분 또는 그 위)
        Location statusLoc = anchor.clone().add(0, 1.2, 0);
        statusLoc.setYaw(displayYaw);
        statusLoc.setPitch(0f);

        World world = anchor.getWorld();
        UUID[] reelIds = new UUID[3];
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            ItemDisplay disp = world.spawn(reelLocs[i], ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.BARRIER));
                d.setBillboard(Display.Billboard.FIXED);
                d.setTransformation(reelTransformation());
                d.setPersistent(true);
                d.addScoreboardTag(TAG_PREFIX + id + "_reel" + idx);
            });
            reelIds[i] = disp.getUniqueId();
        }

        TextDisplay status = world.spawn(statusLoc, TextDisplay.class, d -> {
            d.text(Component.text("비어있음", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(true);
            d.setShadowed(true);
            d.addScoreboardTag(TAG_PREFIX + id + "_status");
        });

        SlotMachine machine = new SlotMachine(id, anchor.clone(),
                reelIds[0], reelIds[1], reelIds[2], status.getUniqueId());
        machines.put(id, machine);
        save();
        return machine;
    }

    public boolean remove(String id) {
        SlotMachine m = machines.remove(id);
        if (m == null) return false;
        for (UUID uid : m.displayIds()) {
            Entity e = Bukkit.getEntity(uid);
            if (e != null) e.remove();
        }
        if (m.hasNpc()) {
            NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
            if (npc != null) npc.remove(NPC_OWNER, m.npcId());
        }
        save();
        return true;
    }

    public static final String NPC_OWNER = "gamble";
    public static final String SLOT_NPC_PREFIX = "slot_";
    public static final String LOTTERY_NPC_PREFIX = "lottery_";

    /** owner=gamble 의 npcId 에서 사용자가 입력한 raw id 부분을 추출 (prefix 제거). */
    public static String stripNpcPrefix(String npcId) {
        if (npcId == null) return null;
        if (npcId.startsWith(SLOT_NPC_PREFIX)) return npcId.substring(SLOT_NPC_PREFIX.length());
        if (npcId.startsWith(LOTTERY_NPC_PREFIX)) return npcId.substring(LOTTERY_NPC_PREFIX.length());
        return null;
    }

    /** 이미 등록된 머신에 NPC 를 붙인다. NPC 는 플레이어 standing 위치에 스폰. */
    public boolean attachNpc(SlotMachine machine, String npcId, String skinOwner, Location npcLoc) {
        NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
        if (npc == null) {
            plugin.getLogger().warning("[ExitGamble] NpcService 미등록 (Core 1.6.1+ 필요)");
            return false;
        }
        if (npc.get(NPC_OWNER, npcId).isPresent()) return false;
        String displayName = stripNpcPrefix(npcId);
        if (displayName == null) displayName = npcId;
        boolean ok = npc.spawn(new NpcSpawnSpec(NPC_OWNER, npcId, npcLoc, displayName, skinOwner, true));
        if (!ok) return false;
        machine.setNpc(npcId, skinOwner);
        save();
        return true;
    }

    public boolean detachNpc(SlotMachine machine) {
        if (!machine.hasNpc()) return false;
        NpcService npc = ServiceRegistry.get(NpcService.class).orElse(null);
        if (npc != null) npc.remove(NPC_OWNER, machine.npcId());
        machine.clearNpc();
        save();
        return true;
    }

    private Transformation reelTransformation() {
        // 흰색 콘크리트 블록 1개 면(1×1)에 거의 꽉 차도록 스케일.
        // ⚠ Transformation translation 은 entity LOCAL space (yaw 회전 적용됨).
        //   Y 축은 항상 월드 up 이지만 X/Z 는 entity 가 회전한 만큼 회전됨 →
        //   잘못 주면 벽 안으로 들어가거나 옆으로 튐. ItemDisplay 의 기본 렌더링은
        //   entity 위치를 중심으로 거의 맞춰주므로 translation 0 으로 두는 게 안전.
        float s = 0.85f;
        return new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(),
                new Vector3f(s, s, s),
                new Quaternionf()
        );
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("machines");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            String worldName = s.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("머신 " + id + ": world '" + worldName + "' 없음, 스킵");
                continue;
            }
            Location anchor = new Location(world,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw", 0), 0f);
            UUID r1 = UUID.fromString(s.getString("reel1"));
            UUID r2 = UUID.fromString(s.getString("reel2"));
            UUID r3 = UUID.fromString(s.getString("reel3"));
            UUID st = UUID.fromString(s.getString("status"));
            SlotMachine machine = new SlotMachine(id, anchor, r1, r2, r3, st);
            String npcId = s.getString("npc.id");
            String npcSkin = s.getString("npc.skin");
            if (npcId != null) machine.setNpc(npcId, npcSkin);
            machines.put(id, machine);
        }
        plugin.getLogger().info("슬롯머신 " + machines.size() + "대 로드");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (SlotMachine m : machines.values()) {
            String base = "machines." + m.id();
            Location a = m.anchor();
            cfg.set(base + ".world", a.getWorld().getName());
            cfg.set(base + ".x", a.getX());
            cfg.set(base + ".y", a.getY());
            cfg.set(base + ".z", a.getZ());
            cfg.set(base + ".yaw", a.getYaw());
            cfg.set(base + ".reel1", m.reelId(0).toString());
            cfg.set(base + ".reel2", m.reelId(1).toString());
            cfg.set(base + ".reel3", m.reelId(2).toString());
            cfg.set(base + ".status", m.statusId().toString());
            if (m.hasNpc()) {
                cfg.set(base + ".npc.id", m.npcId());
                cfg.set(base + ".npc.skin", m.npcSkin());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "machines.yml 저장 실패", e);
        }
    }
}
