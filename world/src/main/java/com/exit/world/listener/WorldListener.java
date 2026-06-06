package com.exit.world.listener;

import com.exit.world.boss.BossArenaManager;
import com.exit.world.gui.DungeonConfirmGUI;
import com.exit.world.gui.DungeonTabGUI;
import com.exit.world.gui.PortalGUI;
import com.exit.world.manager.DungeonEntry;
import com.exit.world.manager.DungeonRegistry;
import com.exit.world.manager.WorldConfig;
import com.exit.world.manager.WorldManager;
import com.exit.core.api.EconomyProvider;
import com.exit.core.registry.ServiceRegistry;
import org.bukkit.event.player.PlayerSpawnChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * 처리하는 이벤트:
 * 0. 사망 후 리스폰 — 마을월드 침대면 그대로, 아니면 마을 spawn 강제
 * 1. spawn point 변경 — 마을월드 침대만 허용
 * 2. GUI 클릭 → 포탈 / 던전 마스터 / 입장 확인
 * 3. 몬스터 스폰 차단 (worlds.yml 규칙 적용)
 * 4. 던전/보스 월드에서 logout 후 재접속 → 마을로 강제 텔포
 */
public class WorldListener implements Listener {

    private static final String VILLAGE_WORLD = "world_village";

    private final WorldManager worldManager;
    private final DungeonRegistry dungeonRegistry;
    private final DungeonTabGUI dungeonTabGUI;
    private final DungeonConfirmGUI dungeonConfirmGUI;
    private final BossArenaManager bossArenaManager;
    private final JavaPlugin plugin;

    public WorldListener(WorldManager worldManager,
                         PortalGUI portalGUI,
                         DungeonRegistry dungeonRegistry,
                         DungeonTabGUI dungeonTabGUI,
                         DungeonConfirmGUI dungeonConfirmGUI,
                         BossArenaManager bossArenaManager,
                         JavaPlugin plugin) {
        this.worldManager = worldManager;
        this.dungeonRegistry = dungeonRegistry;
        this.dungeonTabGUI = dungeonTabGUI;
        this.dungeonConfirmGUI = dungeonConfirmGUI;
        this.bossArenaManager = bossArenaManager;
        this.plugin = plugin;
    }

    // ── 0. 사망 후 리스폰 — 마을월드 침대 우선, 그 외 마을 spawn ──

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location respawn = event.getRespawnLocation();
        if (respawn != null && respawn.getWorld() != null
                && VILLAGE_WORLD.equals(respawn.getWorld().getName())
                && (event.isBedSpawn() || event.isAnchorSpawn())) {
            return;
        }
        Location loc = getSafeVillageSpawn();
        if (loc != null) event.setRespawnLocation(loc);
    }

    // ── 1. spawn point 변경 차단 — 다른월드 침대/앵커 무시 ───────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnChange(PlayerSpawnChangeEvent event) {
        Location newSpawn = event.getNewSpawn();
        if (newSpawn == null || newSpawn.getWorld() == null) return;
        if (!VILLAGE_WORLD.equals(newSpawn.getWorld().getName())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "다른 월드에서는 스폰 지점을 설정할 수 없습니다.")
                    .color(NamedTextColor.YELLOW));
        }
    }

    // ── 2. 마을 침대 우클릭 — 낮에도 spawn point 강제 설정 ────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || !Tag.BEDS.isTagged(block.getType())) return;
        if (!VILLAGE_WORLD.equals(block.getWorld().getName())) return;

        Player player = event.getPlayer();
        player.setRespawnLocation(block.getLocation().add(0.5, 0.5, 0.5), true);
        player.sendMessage(Component.text("이 침대를 스폰 지점으로 설정했습니다.")
                .color(NamedTextColor.GREEN));
    }

    // ── 3. 첫 접속 / 던전·보스월드 재접속 — 마을로 강제 텔포 ──────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstTime = !player.hasPlayedBefore();
        boolean inDungeon = isDungeonWorld(player.getWorld().getName());
        if (!firstTime && !inDungeon) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = getSafeVillageSpawn();
            if (loc != null) player.teleport(loc);
        });
    }

    private boolean isDungeonWorld(String worldName) {
        for (DungeonEntry e : dungeonRegistry.getByTab(DungeonEntry.Tab.DUNGEON)) {
            if (e.worldName().equals(worldName)) return true;
        }
        for (DungeonEntry e : dungeonRegistry.getByTab(DungeonEntry.Tab.BOSS)) {
            if (e.worldName().equals(worldName)) return true;
        }
        return false;
    }

    // ── 4. GUI 클릭 → 포탈 / 던전 탭 / 입장 확인 ────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText()
            .serialize(event.getView().title());

        // 포탈 GUI
        if (title.equals(PortalGUI.GUI_TITLE)) {
            event.setCancelled(true);
            String worldKey = PortalGUI.getWorldKeyBySlot(event.getRawSlot());
            if (worldKey == null) return;
            player.closeInventory();
            boolean success = worldManager.teleportPlayer(player, worldKey);
            if (!success) {
                player.sendMessage(Component.text("월드를 불러올 수 없습니다. 관리자에게 문의하세요.")
                        .color(NamedTextColor.RED));
            }
            return;
        }

        // 던전 탭 GUI
        DungeonEntry.Tab currentTab = DungeonTabGUI.parseTabFromTitle(title);
        if (currentTab != null) {
            event.setCancelled(true);
            // 자기 인벤토리 클릭 무시 (raw slot >= 54 면 아래 인벤)
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            if (DungeonTabGUI.isCloseSlot(slot)) {
                player.closeInventory();
                return;
            }
            DungeonEntry.Tab[] target = new DungeonEntry.Tab[1];
            if (DungeonTabGUI.isTabSlot(slot, target)) {
                if (target[0] != currentTab) {
                    dungeonTabGUI.open(player, target[0]);
                }
                return;
            }
            List<DungeonEntry> list = dungeonRegistry.getByTab(currentTab);
            DungeonEntry clicked = DungeonTabGUI.entryAtSlot(slot, list);
            if (clicked == null) return;
            dungeonConfirmGUI.open(player, clicked);
            return;
        }

        // 던전 입장 확인 GUI
        String key = DungeonConfirmGUI.parseKey(title);
        if (key != null) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot == DungeonConfirmGUI.SLOT_NO) {
                player.closeInventory();
                return;
            }
            if (slot != DungeonConfirmGUI.SLOT_YES) return;

            DungeonEntry entry = dungeonRegistry.getByKey(key);
            if (entry == null) {
                player.closeInventory();
                player.sendMessage(Component.text("해당 던전이 존재하지 않습니다.")
                        .color(NamedTextColor.RED));
                return;
            }
            handleDungeonEntry(player, entry);
        }
    }

    /** NPC 클릭 → 던전 탭 GUI 오픈 (WorldPlugin 에서 NpcService 클릭 핸들러로 호출). */
    public void onDungeonMasterClick(Player player) {
        dungeonTabGUI.open(player, DungeonEntry.Tab.DUNGEON);
    }

    private void handleDungeonEntry(Player player, DungeonEntry entry) {
        player.closeInventory();

        // 보스 아레나라면 입장 가능 여부 먼저 체크 (잠금 중이면 비용 차감 전에 거절)
        if (entry.bossArena() != null) {
            BossArenaManager.EntryCheck check = bossArenaManager.canEnter(entry.key());
            if (!check.allowed()) {
                player.sendMessage(Component.text(check.denyReason())
                        .color(NamedTextColor.YELLOW));
                return;
            }
        }

        EconomyProvider eco = ServiceRegistry.get(EconomyProvider.class).orElse(null);
        if (eco == null) {
            player.sendMessage(Component.text("경제 시스템을 불러올 수 없습니다.")
                    .color(NamedTextColor.RED));
            return;
        }
        if (entry.cost() > 0 && !eco.subtractBalance(player.getUniqueId(), entry.cost())) {
            long balance = eco.getBalance(player.getUniqueId());
            player.sendMessage(
                    Component.text("잔액이 부족합니다. 현재 잔액: ").color(NamedTextColor.RED)
                            .append(Component.text(String.format("%,d", balance) + "w").color(NamedTextColor.GOLD))
            );
            return;
        }

        boolean ok = worldManager.teleportByWorldName(player, entry.worldName());
        if (!ok) {
            player.sendMessage(Component.text("월드를 불러올 수 없습니다. 관리자에게 문의하세요.")
                    .color(NamedTextColor.RED));
            // 비용 환불
            if (entry.cost() > 0) eco.addBalance(player.getUniqueId(), entry.cost());
            return;
        }

        // 보스 아레나 입장 시 — 시선을 보스 spawn 위치로 조준
        if (entry.bossArena() != null) {
            var ba = entry.bossArena();
            Location current = player.getLocation();
            org.bukkit.util.Vector dir = new org.bukkit.util.Vector(
                    ba.bossX() - current.getX(),
                    ba.bossY() - current.getY(),
                    ba.bossZ() - current.getZ());
            if (dir.lengthSquared() > 0.0001) {
                Location facing = current.clone();
                facing.setDirection(dir);
                player.teleport(facing);
            }
        }
    }

    // ── 5. 몬스터 스폰 제어 ──────────────────────────────────────

    // ── 명령어 차단 (worlds.yml 의 blocked-commands) ─────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        WorldConfig wc = worldManager.getWorldConfig(event.getPlayer().getWorld());
        if (wc == null || wc.getBlockedCommands().isEmpty()) return;
        // /명령어 [args] 에서 명령어 추출 (slash 제거 + 소문자)
        String msg = event.getMessage();
        if (!msg.startsWith("/")) return;
        String cmd = msg.substring(1).split("\\s+", 2)[0].toLowerCase();
        if (wc.getBlockedCommands().contains(cmd)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "이 월드에서는 /" + cmd + " 명령어를 사용할 수 없습니다.")
                    .color(NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;

        WorldConfig wc = worldManager.getWorldConfig(event.getEntity().getWorld());
        if (wc == null) return;

        if (wc.isMonsterSpawn()) return;

        // monster-spawn:false 라도 플러그인/명령어/MythicMobs 같은 의도된 spawn 은 허용
        var reason = event.getSpawnReason();
        switch (reason) {
            case CUSTOM, COMMAND, SPAWNER_EGG, BREEDING, DISPENSE_EGG, DEFAULT,
                 RAID, PATROL, MOUNT -> {
                // 통과
            }
            default -> event.setCancelled(true);  // NATURAL, SPAWNER, VILLAGE_INVASION 등
        }
    }

    // ── 내부 유틸 ────────────────────────────────────────────────

    private Location getSafeVillageSpawn() {
        WorldConfig wc = worldManager.getConfig("village");
        if (wc == null) return null;
        Location loc = wc.getSpawnLocation();
        if (loc == null) return null;
        int safeY = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()) + 1;
        loc.setY(safeY);
        return loc;
    }
}
