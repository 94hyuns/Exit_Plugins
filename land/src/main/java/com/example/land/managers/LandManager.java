package com.example.land.managers;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
import com.example.land.data.ClaimedChunk;
import com.exit.core.api.EconomyProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class LandManager {

    private final LandPlugin plugin;
    private final LandDatabase db;
    private final AdminChunkStore adminStore;

    /** 메모리 캐시 (PLAYER + ADMIN 합본) */
    private final Map<ChunkPos, ClaimedChunk> claimedChunks = new HashMap<>();

    /** 컨트롤러 소유자 → 설치 위치 */
    private final Map<UUID, org.bukkit.Location> controllerLocations = new HashMap<>();

    public LandManager(LandPlugin plugin, LandDatabase db) {
        this.plugin = plugin;
        this.db = db;
        this.adminStore = new AdminChunkStore(plugin);
    }

    public void load() {
        // PLAYER 청크 (SQLite)
        Map<ChunkPos, ClaimedChunk> players = db.loadAll();
        claimedChunks.putAll(players);

        // ADMIN 청크 (YAML) — 충돌 시 ADMIN 우선 + 경고
        Map<ChunkPos, ClaimedChunk> admins = adminStore.load();
        int conflicts = 0;
        for (Map.Entry<ChunkPos, ClaimedChunk> e : admins.entrySet()) {
            if (claimedChunks.containsKey(e.getKey())) {
                conflicts++;
                ClaimedChunk existing = claimedChunks.get(e.getKey());
                plugin.getLogger().warning("청크 충돌: " + e.getKey() + " 가 PLAYER("
                        + Bukkit.getOfflinePlayer(existing.getOwner()).getName()
                        + ") 와 ADMIN 양쪽에 있어 ADMIN 으로 덮어씀.");
            }
            claimedChunks.put(e.getKey(), e.getValue());
        }

        controllerLocations.putAll(db.loadControllerLocations());
        plugin.getLogger().info("청크 데이터 로드 완료: PLAYER " + players.size()
                + ", ADMIN " + admins.size() + (conflicts > 0 ? ", 충돌 " + conflicts : "") + ".");
    }

    /**
     * admin_chunks.yml 을 다시 읽어 메모리 캐시의 ADMIN 청크를 교체한다.
     * PLAYER 청크는 손대지 않는다.
     * @return [추가, 제거, 유지] 카운트
     */
    public int[] reloadAdminChunks() {
        // 새 ADMIN 청크 로드
        Map<ChunkPos, ClaimedChunk> newAdmins = adminStore.load();

        // 기존 ADMIN 청크 식별
        Set<ChunkPos> oldAdminKeys = new HashSet<>();
        for (Map.Entry<ChunkPos, ClaimedChunk> e : claimedChunks.entrySet()) {
            if (e.getValue().isAdmin()) oldAdminKeys.add(e.getKey());
        }

        int added = 0, kept = 0, removed = 0, conflict = 0;

        // 제거: 기존 ADMIN 중 새 셋에 없는 것
        for (ChunkPos pos : oldAdminKeys) {
            if (!newAdmins.containsKey(pos)) {
                claimedChunks.remove(pos);
                removed++;
            } else {
                kept++;
            }
        }

        // 추가: 새 ADMIN 중 기존에 없던 것 (충돌 시 PLAYER 보존 — 안전 정책)
        for (Map.Entry<ChunkPos, ClaimedChunk> e : newAdmins.entrySet()) {
            if (oldAdminKeys.contains(e.getKey())) continue;
            ClaimedChunk existing = claimedChunks.get(e.getKey());
            if (existing != null && !existing.isAdmin()) {
                conflict++;
                plugin.getLogger().warning("리로드: " + e.getKey() + " 는 PLAYER("
                        + Bukkit.getOfflinePlayer(existing.getOwner()).getName()
                        + ") 소유이므로 ADMIN 지정이 무시됨. 먼저 회수하세요.");
                continue;
            }
            claimedChunks.put(e.getKey(), e.getValue());
            added++;
        }

        if (conflict > 0) {
            plugin.getLogger().warning("admin_chunks.yml 리로드 중 PLAYER 청크와 충돌 " + conflict
                    + "건이 무시되었습니다.");
        }
        return new int[] { added, removed, kept };
    }

    // ─────────────────────────────────────────────
    // 클레임
    // ─────────────────────────────────────────────

    public enum ClaimResult { SUCCESS, ALREADY_CLAIMED, MAX_REACHED, NO_MONEY, ECO_UNAVAILABLE, WRONG_WORLD }

    public ClaimResult claim(Player player, Chunk chunk) {
        if (!plugin.isAllowedWorld(chunk.getWorld())) return ClaimResult.WRONG_WORLD;

        ChunkPos pos = ChunkPos.of(chunk);

        if (claimedChunks.containsKey(pos)) return ClaimResult.ALREADY_CLAIMED;

        int currentCount = getClaimsOf(player.getUniqueId()).size();
        int max = plugin.getMaxChunksPerPlayer();
        if (currentCount >= max) return ClaimResult.MAX_REACHED;

        EconomyProvider eco = plugin.getEconomy();
        if (eco == null) return ClaimResult.ECO_UNAVAILABLE;

        // 다음 구매는 (currentCount + 1)번째 슬롯
        long price = plugin.getPriceForSlot(currentCount + 1);
        if (eco.getBalance(player.getUniqueId()) < price) return ClaimResult.NO_MONEY;

        eco.subtractBalance(player.getUniqueId(), price);

        ClaimedChunk claimed = new ClaimedChunk(pos, player.getUniqueId());
        claimedChunks.put(pos, claimed);
        db.saveClaim(claimed);

        // 첫 클레임 시에만 컨트롤러 지급 (이미 청크가 있었으면 이미 가지고 있음)
        if (getClaimsOf(player.getUniqueId()).size() == 1) {
            giveControllerItem(player);
        }

        return ClaimResult.SUCCESS;
    }

    public enum UnclaimResult { SUCCESS, NOT_OWNER, NOT_CLAIMED, ECO_UNAVAILABLE, WRONG_WORLD }

    public UnclaimResult unclaim(Player player, Chunk chunk) {
        if (!plugin.isAllowedWorld(chunk.getWorld())) return UnclaimResult.WRONG_WORLD;

        ChunkPos pos = ChunkPos.of(chunk);
        ClaimedChunk claimed = claimedChunks.get(pos);

        if (claimed == null) return UnclaimResult.NOT_CLAIMED;
        if (!claimed.getOwner().equals(player.getUniqueId()) && !player.hasPermission("land.admin"))
            return UnclaimResult.NOT_OWNER;

        EconomyProvider eco = plugin.getEconomy();
        if (eco == null) return UnclaimResult.ECO_UNAVAILABLE;

        // 환불 = 가장 최근 슬롯 가격 × ratio (현재 N개 보유 → N번째 슬롯 환불)
        int currentCount = getClaimsOf(player.getUniqueId()).size();
        long price = plugin.getPriceForSlot(currentCount);
        double ratio = plugin.getConfig().getDouble("land.refund-ratio", 0.75);
        long refund = Math.round(price * ratio);
        eco.addBalance(player.getUniqueId(), refund);

        claimedChunks.remove(pos);
        db.deleteClaim(pos);

        // 모든 청크 반환 시 컨트롤러 회수
        if (getClaimsOf(player.getUniqueId()).isEmpty()) {
            takeControllerItem(player);
            unregisterController(player.getUniqueId());
        }

        return UnclaimResult.SUCCESS;
    }

    // ─────────────────────────────────────────────
    // 관리구역 (Admin Claim)
    // ─────────────────────────────────────────────

    public enum AdminClaimResult { SUCCESS, ALREADY_PLAYER_CLAIMED, ALREADY_ADMIN, WRONG_WORLD }
    public enum AdminUnclaimResult { SUCCESS, NOT_CLAIMED, NOT_ADMIN, WRONG_WORLD }

    /**
     * 지정한 청크를 관리구역으로 등록한다. 경제/제한 무시.
     * 이미 플레이어가 소유한 청크는 덮어쓰지 않고 에러 반환 (관리자가 먼저 unclaim 시켜야 함).
     */
    public AdminClaimResult claimAdmin(org.bukkit.World world, ChunkPos pos) {
        if (!plugin.isAllowedWorld(world)) return AdminClaimResult.WRONG_WORLD;

        ClaimedChunk existing = claimedChunks.get(pos);
        if (existing != null) {
            if (existing.isAdmin()) return AdminClaimResult.ALREADY_ADMIN;
            return AdminClaimResult.ALREADY_PLAYER_CLAIMED;
        }

        ClaimedChunk adminChunk = ClaimedChunk.admin(pos);
        claimedChunks.put(pos, adminChunk);
        persistAdminStore();
        return AdminClaimResult.SUCCESS;
    }

    /** 메모리에 있는 모든 ADMIN 청크를 admin_chunks.yml 에 저장 */
    private void persistAdminStore() {
        List<ClaimedChunk> admins = new ArrayList<>();
        for (ClaimedChunk c : claimedChunks.values()) if (c.isAdmin()) admins.add(c);
        adminStore.save(admins);
    }

    /**
     * 관리구역 해제. 플레이어 청크는 건드리지 않는다 (NOT_ADMIN 반환).
     */
    public AdminUnclaimResult unclaimAdmin(org.bukkit.World world, ChunkPos pos) {
        if (!plugin.isAllowedWorld(world)) return AdminUnclaimResult.WRONG_WORLD;

        ClaimedChunk existing = claimedChunks.get(pos);
        if (existing == null) return AdminUnclaimResult.NOT_CLAIMED;
        if (!existing.isAdmin()) return AdminUnclaimResult.NOT_ADMIN;

        claimedChunks.remove(pos);
        persistAdminStore();
        return AdminUnclaimResult.SUCCESS;
    }

    // ─────────────────────────────────────────────
    // 조회
    // ─────────────────────────────────────────────

    public ClaimedChunk getChunk(ChunkPos pos) {
        return claimedChunks.get(pos);
    }

    public ClaimedChunk getChunk(Chunk chunk) {
        return getChunk(ChunkPos.of(chunk));
    }

    /** 메모리 캐시의 모든 청크 스냅샷 (PLAYER + ADMIN). */
    public List<ClaimedChunk> getAllChunks() {
        return new ArrayList<>(claimedChunks.values());
    }

    public List<ClaimedChunk> getClaimsOf(UUID owner) {
        return claimedChunks.values().stream()
                .filter(c -> c.getOwner().equals(owner))
                .collect(Collectors.toList());
    }

    public boolean canInteract(Player player, Chunk chunk) {
        ClaimedChunk claimed = getChunk(chunk);
        if (claimed == null) return true;
        return claimed.canInteract(player.getUniqueId());
    }

    /**
     * 클레임된 청크의 소유자/멤버인지 확인한다.
     * canInteract()와의 차이: 클레임되지 않은 청크는 false를 반환한다.
     * → 월드 기본 금지 + land-override 월드(마을)에서 사용.
     */
    public boolean canInteractOwned(Player player, Chunk chunk) {
        ClaimedChunk claimed = getChunk(chunk);
        if (claimed == null) return false;  // 클레임 안 됨 → 불허
        return claimed.canInteract(player.getUniqueId());
    }

    // ─────────────────────────────────────────────
    // 멤버 관리
    // ─────────────────────────────────────────────

    public boolean addMember(UUID owner, ChunkPos pos, UUID member) {
        ClaimedChunk chunk = claimedChunks.get(pos);
        if (chunk == null || !chunk.getOwner().equals(owner)) return false;
        chunk.addMember(member);
        db.saveMember(pos, member);
        return true;
    }

    public boolean removeMember(UUID owner, ChunkPos pos, UUID member) {
        ClaimedChunk chunk = claimedChunks.get(pos);
        if (chunk == null || !chunk.getOwner().equals(owner)) return false;
        chunk.removeMember(member);
        db.deleteMember(pos, member);
        return true;
    }

    public void removeMemberFromAll(UUID owner, UUID member) {
        getClaimsOf(owner).forEach(chunk -> {
            chunk.removeMember(member);
            db.deleteMember(chunk.getPos(), member);
        });
    }

    // ─────────────────────────────────────────────
    // 청크 컨트롤러
    // ─────────────────────────────────────────────

    public boolean hasController(UUID owner) {
        return controllerLocations.containsKey(owner);
    }

    public org.bukkit.Location getControllerLocation(UUID owner) {
        return controllerLocations.get(owner);
    }

    public void registerController(UUID owner, org.bukkit.Location loc) {
        controllerLocations.put(owner, loc);
        db.saveControllerLocation(owner, loc);
    }

    public void unregisterController(UUID owner) {
        controllerLocations.remove(owner);
        db.deleteControllerLocation(owner);
    }

    public void giveControllerItem(Player player) {
        org.bukkit.Material mat = org.bukkit.Material.valueOf(
                plugin.getConfig().getString("land.controller-material", "LODESTONE"));
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[청크 컨트롤러]").color(NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("우클릭으로 권한 관리 GUI 열기").color(NamedTextColor.GRAY),
                Component.text("설치 후 우클릭으로 사용").color(NamedTextColor.GRAY)
        ));
        meta.getPersistentDataContainer().set(
                new org.bukkit.NamespacedKey(plugin, "chunk_controller"),
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1
        );
        item.setItemMeta(meta);

        for (org.bukkit.inventory.ItemStack inv : player.getInventory().getContents()) {
            if (isControllerItem(inv)) return;
        }
        player.getInventory().addItem(item);
        player.sendMessage(Component.text("청크 컨트롤러를 획득했습니다! 설치 후 우클릭으로 관리 창을 열 수 있습니다.")
                .color(NamedTextColor.GREEN));
    }

    public boolean isControllerItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
                new org.bukkit.NamespacedKey(plugin, "chunk_controller"),
                org.bukkit.persistence.PersistentDataType.BYTE
        );
    }

    // ─────────────────────────────────────────────
    // 일괄 리셋 (관리구역은 보존, 플레이어 청크만 초기화)
    // ─────────────────────────────────────────────

    public record ResetSummary(int chunksRemoved, int playersAffected) {}

    /**
     * PLAYER 타입 청크만 전부 삭제. ADMIN 청크는 그대로 유지.
     * <ul>
     *   <li>메모리 캐시에서 PLAYER 항목 제거</li>
     *   <li>DB 의 PLAYER 청크 + 그 멤버 데이터 + 모든 controller_locations 삭제</li>
     *   <li>온라인 플레이어 인벤토리의 컨트롤러 아이템 회수</li>
     *   <li>설치된 컨트롤러 블록은 다음 unregister/사용 시 자연스럽게 정리됨
     *       (위치 매핑이 사라지므로 ControllerListener 가 무시)</li>
     * </ul>
     */
    public ResetSummary resetPlayerClaims() {
        // 영향받는 owner 들 미리 수집 (온라인 플레이어 컨트롤러 회수용)
        Set<UUID> owners = new HashSet<>();
        for (ClaimedChunk c : claimedChunks.values()) {
            if (!c.isAdmin()) owners.add(c.getOwner());
        }

        // 메모리에서 PLAYER 청크 제거
        int removedFromMemory = 0;
        Iterator<Map.Entry<ChunkPos, ClaimedChunk>> it = claimedChunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ChunkPos, ClaimedChunk> e = it.next();
            if (!e.getValue().isAdmin()) {
                it.remove();
                removedFromMemory++;
            }
        }

        // 컨트롤러 위치 매핑 전부 클리어 (PLAYER 청크 종속)
        controllerLocations.clear();

        // DB 일괄 삭제
        db.resetPlayerClaims();

        // 온라인 플레이어 컨트롤러 아이템 회수
        for (UUID ownerId : owners) {
            Player online = Bukkit.getPlayer(ownerId);
            if (online != null && online.isOnline()) {
                takeControllerItem(online);
            }
        }

        return new ResetSummary(removedFromMemory, owners.size());
    }

    /** 플레이어 인벤토리에서 컨트롤러 아이템 회수 */
    public void takeControllerItem(Player player) {
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isControllerItem(contents[i])) {
                player.getInventory().setItem(i, null);
                player.sendMessage(Component.text("모든 청크를 반환하여 청크 컨트롤러가 회수되었습니다.")
                        .color(NamedTextColor.GRAY));
                return;
            }
        }
    }
}
