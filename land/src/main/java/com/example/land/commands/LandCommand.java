package com.example.land.commands;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
import com.example.land.data.ClaimedChunk;
import com.example.land.managers.LandManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LandCommand implements CommandExecutor, TabCompleter {

    /** 범위 지정 명령어로 한 번에 처리할 수 있는 최대 청크 수 (실수 방지) */
    private static final int MAX_AREA_CHUNKS = 200;

    private final LandPlugin plugin;
    private final LandManager manager;

    public LandCommand(LandPlugin plugin) {
        this.plugin = plugin;
        this.manager = plugin.getLandManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용 가능합니다.");
            return true;
        }

        // 모든 명령어 월드 체크
        if (!plugin.isAllowedWorld(player.getWorld())) {
            player.sendMessage(Component.text("마을 서버에서만 사용 가능한 명령어입니다.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        // 한글 명령어 → 영문 매핑
        sub = switch (sub) {
            case "구매" -> "claim";
            case "반환" -> "unclaim";
            case "목록" -> "list";
            case "정보" -> "info";
            case "관리" -> "admin";
            default -> sub;
        };

        switch (sub) {
            case "claim" -> handleClaim(player);
            case "unclaim" -> handleUnclaim(player);
            case "list" -> handleList(player);
            case "info" -> handleInfo(player);
            case "admin" -> handleAdmin(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleClaim(Player player) {
        if (!player.hasPermission("land.claim")) {
            player.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return;
        }
        int max = plugin.getMaxChunksPerPlayer();
        int countBefore = manager.getClaimsOf(player.getUniqueId()).size();
        long pricePlanned = countBefore < max ? plugin.getPriceForSlot(countBefore + 1) : -1;

        LandManager.ClaimResult result = manager.claim(player, player.getLocation().getChunk());

        switch (result) {
            case SUCCESS -> {
                int countAfter = manager.getClaimsOf(player.getUniqueId()).size();
                player.sendMessage(Component.text(
                        "청크를 구매했습니다! (" + fmt(pricePlanned) + " 울캐쉬 소비, 보유 "
                                + countAfter + " / " + max + ")").color(NamedTextColor.GREEN));
                if (countAfter < max) {
                    long nextPrice = plugin.getPriceForSlot(countAfter + 1);
                    player.sendMessage(Component.text(
                            "§7다음 청크 구매 가격: §f" + fmt(nextPrice) + " 울캐쉬"));
                } else {
                    player.sendMessage(Component.text(
                            "§e최대 보유량(" + max + "청크)에 도달했습니다."));
                }
            }
            case ALREADY_CLAIMED -> {
                ClaimedChunk chunk = manager.getChunk(player.getLocation().getChunk());
                if (chunk != null && chunk.isAdmin()) {
                    player.sendMessage(Component.text("이 청크는 관리구역입니다. 구매할 수 없습니다.").color(NamedTextColor.RED));
                } else {
                    String ownerName = chunk != null
                            ? Bukkit.getOfflinePlayer(chunk.getOwner()).getName() : "알 수 없음";
                    player.sendMessage(Component.text("이 청크는 이미 " + ownerName + " 님의 땅입니다.").color(NamedTextColor.RED));
                }
            }
            case MAX_REACHED -> player.sendMessage(Component.text(
                    "최대 보유 청크 수(" + max + ")에 도달했습니다.").color(NamedTextColor.RED));
            case NO_MONEY -> player.sendMessage(Component.text(
                    "잔액이 부족합니다. (필요: " + fmt(pricePlanned) + " 울캐쉬)").color(NamedTextColor.RED));
            case ECO_UNAVAILABLE -> player.sendMessage(Component.text(
                    "경제 시스템을 사용할 수 없습니다. 관리자에게 문의하세요.").color(NamedTextColor.RED));
            case WRONG_WORLD -> player.sendMessage(Component.text(
                    "이 월드에서는 청크를 구매할 수 없습니다.").color(NamedTextColor.RED));
        }
    }

    private void handleUnclaim(Player player) {
        if (!player.hasPermission("land.unclaim")) {
            player.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return;
        }
        // 환불 = 현재 보유 N개 중 마지막 슬롯 가격 × ratio (unclaim 전 시점 기준)
        int countBefore = manager.getClaimsOf(player.getUniqueId()).size();
        double ratio = plugin.getConfig().getDouble("land.refund-ratio", 0.75);
        long slotPrice = countBefore > 0 ? plugin.getPriceForSlot(countBefore) : 0L;
        long refund = Math.round(slotPrice * ratio);

        LandManager.UnclaimResult result = manager.unclaim(player, player.getLocation().getChunk());

        switch (result) {
            case SUCCESS -> player.sendMessage(Component.text(
                    "청크를 반환했습니다. (" + fmt(refund) + " 울캐쉬 환불)").color(NamedTextColor.GREEN));
            case NOT_CLAIMED -> player.sendMessage(Component.text(
                    "이 청크는 클레임되지 않은 땅입니다.").color(NamedTextColor.RED));
            case NOT_OWNER -> {
                ClaimedChunk chunk = manager.getChunk(player.getLocation().getChunk());
                if (chunk != null && chunk.isAdmin()) {
                    player.sendMessage(Component.text("관리구역은 /땅 관리 반환 명령어로 해제하세요.").color(NamedTextColor.RED));
                } else {
                    player.sendMessage(Component.text("본인 소유의 청크가 아닙니다.").color(NamedTextColor.RED));
                }
            }
            case ECO_UNAVAILABLE -> player.sendMessage(Component.text(
                    "경제 시스템을 사용할 수 없습니다. 관리자에게 문의하세요.").color(NamedTextColor.RED));
            case WRONG_WORLD -> player.sendMessage(Component.text(
                    "이 월드에서는 청크를 반환할 수 없습니다.").color(NamedTextColor.RED));
        }
    }

    private void handleList(Player player) {
        if (!player.hasPermission("land.list")) {
            player.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return;
        }
        int max = plugin.getMaxChunksPerPlayer();
        List<ClaimedChunk> claims = manager.getClaimsOf(player.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(Component.text("§7보유한 청크가 없습니다. (0 / " + max + ")"));
            return;
        }
        player.sendMessage(Component.text("§6=== 내 청크 목록 (" + claims.size() + " / " + max + ") ==="));
        for (ClaimedChunk c : claims) {
            ChunkPos pos = c.getPos();
            int members = c.getMembers().size();
            player.sendMessage(Component.text(
                    "§7  " + pos.toString() + " §8[멤버: " + members + "명]"));
        }
    }

    private void handleInfo(Player player) {
        if (!player.hasPermission("land.info")) {
            player.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return;
        }
        // ── 내 땅 정보 출력 ──
        int max = plugin.getMaxChunksPerPlayer();
        int count = manager.getClaimsOf(player.getUniqueId()).size();
        double ratio = plugin.getConfig().getDouble("land.refund-ratio", 0.75);

        player.sendMessage(Component.text("§6=== 내 땅 정보 ==="));
        player.sendMessage(Component.text("§7보유 청크: §f" + count + " / " + max));
        if (count < max) {
            long nextPrice = plugin.getPriceForSlot(count + 1);
            player.sendMessage(Component.text(
                    "§7다음 청크 구매 가격: §f" + fmt(nextPrice) + " 울캐쉬"));
        } else {
            player.sendMessage(Component.text("§e최대 보유량 도달 (더 구매 불가)"));
        }
        if (count > 0) {
            long slotPrice = plugin.getPriceForSlot(count);
            long refund = Math.round(slotPrice * ratio);
            player.sendMessage(Component.text(
                    "§7현재 슬롯 반환 시 환불: §f" + fmt(refund) + " 울캐쉬 §8(" + fmt(slotPrice)
                            + " × " + (int) Math.round(ratio * 100) + "%)"));
        }

        // ── 토글: 청크 정보 모드 (ActionBar) ──
        boolean nowOn = plugin.getInfoModeManager().toggle(player.getUniqueId());
        if (nowOn) {
            player.sendMessage(Component.text("§a청크 정보 모드 활성화. 이동하면 소유자가 표시됩니다."));
        } else {
            player.sendMessage(Component.text("§7청크 정보 모드 비활성화."));
        }
    }

    /** 천단위 콤마 포맷팅. */
    private static String fmt(long value) {
        return String.format("%,d", value);
    }

    // ─────────────────────────────────────────────
    // 관리구역 명령어
    // ─────────────────────────────────────────────

    /**
     * /land admin <서브명령> ...
     *   claim                               — 현재 청크를 관리구역으로 지정
     *   unclaim                             — 현재 청크 관리구역 해제
     *   claim-area <x1> <z1> <x2> <z2>      — 블록 좌표로 범위 지정
     *   unclaim-area <x1> <z1> <x2> <z2>    — 범위 해제 (범위 내 관리구역만, 플레이어 청크는 건드리지 않음)
     */
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("land.admin")) {
            player.sendMessage(Component.text("권한이 없습니다.").color(NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sendAdminHelp(player);
            return;
        }

        String sub = args[1].toLowerCase();
        sub = switch (sub) {
            case "지정" -> "claim";
            case "해제", "반환" -> "unclaim";
            case "범위지정" -> "claim-area";
            case "범위해제" -> "unclaim-area";
            case "초기화" -> "reset-players";
            case "리로드", "재로드" -> "reload";
            default -> sub;
        };

        switch (sub) {
            case "claim" -> handleAdminClaim(player);
            case "unclaim" -> handleAdminUnclaim(player);
            case "claim-area" -> handleAdminArea(player, args, true);
            case "unclaim-area" -> handleAdminArea(player, args, false);
            case "reset-players" -> handleResetPlayers(player, args);
            case "reload" -> handleReload(player);
            default -> sendAdminHelp(player);
        }
    }

    /**
     * 모든 PLAYER 청크 초기화. 관리구역(ADMIN)은 보존.
     * 안전: 30초 내 두 번 입력해야 실제 실행 (사용자별 confirm 토큰).
     * 사용법:
     *   /땅 관리 초기화           — 1차: 경고 + confirm 대기
     *   /땅 관리 초기화 확인       — 2차: 실제 실행 (30초 내)
     */
    private final java.util.Map<java.util.UUID, Long> resetConfirm = new java.util.HashMap<>();
    private static final long RESET_CONFIRM_WINDOW_MS = 30_000L;

    private void handleResetPlayers(Player player, String[] args) {
        boolean isConfirm = args.length >= 3
                && (args[2].equalsIgnoreCase("confirm") || args[2].equals("확인"));

        if (!isConfirm) {
            // 영향 범위 미리 계산해서 보여주기
            int playerChunks = 0;
            java.util.Set<java.util.UUID> owners = new java.util.HashSet<>();
            for (ClaimedChunk c : manager.getAllChunks()) {
                if (!c.isAdmin()) {
                    playerChunks++;
                    owners.add(c.getOwner());
                }
            }
            resetConfirm.put(player.getUniqueId(), System.currentTimeMillis());
            player.sendMessage(Component.text(
                    "⚠ 경고: 모든 플레이어 소유 청크를 초기화합니다.").color(NamedTextColor.RED));
            player.sendMessage(Component.text(
                    "  영향: " + playerChunks + "청크, " + owners.size() + "명").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                    "  관리구역(ADMIN)은 그대로 유지됩니다.").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                    "  취소하려면 30초 동안 아무것도 입력하지 마세요.").color(NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                    "  실행: /땅 관리 초기화 확인").color(NamedTextColor.YELLOW));
            return;
        }

        Long ts = resetConfirm.remove(player.getUniqueId());
        if (ts == null || System.currentTimeMillis() - ts > RESET_CONFIRM_WINDOW_MS) {
            player.sendMessage(Component.text(
                    "확인 시간이 만료되었거나 1차 명령이 없습니다. /땅 관리 초기화 부터 다시 실행해주세요.")
                    .color(NamedTextColor.RED));
            return;
        }

        LandManager.ResetSummary summary = manager.resetPlayerClaims();
        player.sendMessage(Component.text(
                "플레이어 청크 초기화 완료. 삭제: " + summary.chunksRemoved() + "청크, 영향: "
                        + summary.playersAffected() + "명. 관리구역은 보존됨.")
                .color(NamedTextColor.GREEN));
        plugin.getLogger().info("[LandReset] " + player.getName() + " executed reset-players: "
                + summary.chunksRemoved() + " chunks, " + summary.playersAffected() + " players affected.");
    }

    /**
     * /땅 관리 리로드 — admin_chunks.yml 을 다시 읽어 ADMIN 캐시 갱신.
     * PLAYER 청크 및 멤버는 손대지 않음.
     */
    private void handleReload(Player player) {
        int[] r = manager.reloadAdminChunks();
        int added = r[0], removed = r[1], kept = r[2];
        player.sendMessage(Component.text(
                "admin_chunks.yml 리로드 완료. 추가: " + added + ", 제거: " + removed + ", 유지: " + kept)
                .color(NamedTextColor.GREEN));
        plugin.getLogger().info("[LandReload] " + player.getName()
                + " reloaded admin_chunks.yml (+" + added + " -" + removed + " =" + kept + ")");
    }

    private void handleAdminClaim(Player player) {
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        LandManager.AdminClaimResult result = manager.claimAdmin(player.getWorld(), ChunkPos.of(chunk));
        switch (result) {
            case SUCCESS -> player.sendMessage(Component.text(
                    "관리구역으로 지정했습니다: (" + chunk.getX() + ", " + chunk.getZ() + ")").color(NamedTextColor.GREEN));
            case ALREADY_ADMIN -> player.sendMessage(Component.text(
                    "이미 관리구역입니다.").color(NamedTextColor.YELLOW));
            case ALREADY_PLAYER_CLAIMED -> {
                ClaimedChunk existing = manager.getChunk(chunk);
                String ownerName = existing != null
                        ? Bukkit.getOfflinePlayer(existing.getOwner()).getName() : "알 수 없음";
                player.sendMessage(Component.text(
                        "이 청크는 " + ownerName + " 님이 소유 중입니다. 먼저 회수해야 합니다.")
                        .color(NamedTextColor.RED));
            }
            case WRONG_WORLD -> player.sendMessage(Component.text(
                    "이 월드에서는 관리구역을 지정할 수 없습니다.").color(NamedTextColor.RED));
        }
    }

    private void handleAdminUnclaim(Player player) {
        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        LandManager.AdminUnclaimResult result = manager.unclaimAdmin(player.getWorld(), ChunkPos.of(chunk));
        switch (result) {
            case SUCCESS -> player.sendMessage(Component.text(
                    "관리구역을 해제했습니다: (" + chunk.getX() + ", " + chunk.getZ() + ")").color(NamedTextColor.GREEN));
            case NOT_CLAIMED -> player.sendMessage(Component.text(
                    "이 청크는 클레임되지 않았습니다.").color(NamedTextColor.YELLOW));
            case NOT_ADMIN -> player.sendMessage(Component.text(
                    "이 청크는 관리구역이 아닙니다 (플레이어 청크).").color(NamedTextColor.RED));
            case WRONG_WORLD -> player.sendMessage(Component.text(
                    "이 월드에서는 관리구역을 해제할 수 없습니다.").color(NamedTextColor.RED));
        }
    }

    /**
     * /land admin claim-area / unclaim-area 공통 처리.
     * isClaim=true → 관리구역 지정, false → 해제
     */
    private void handleAdminArea(Player player, String[] args, boolean isClaim) {
        if (args.length < 6) {
            player.sendMessage(Component.text(
                    "사용법: /land admin " + (isClaim ? "claim-area" : "unclaim-area")
                    + " <x1> <z1> <x2> <z2>  (블록 좌표)").color(NamedTextColor.RED));
            return;
        }

        int bx1, bz1, bx2, bz2;
        try {
            bx1 = Integer.parseInt(args[2]);
            bz1 = Integer.parseInt(args[3]);
            bx2 = Integer.parseInt(args[4]);
            bz2 = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("좌표는 정수로 입력해주세요.").color(NamedTextColor.RED));
            return;
        }

        // 블록 좌표 → 청크 좌표 (바닐라 공식: chunkCoord = blockCoord >> 4)
        int cx1 = Math.min(bx1, bx2) >> 4;
        int cx2 = Math.max(bx1, bx2) >> 4;
        int cz1 = Math.min(bz1, bz2) >> 4;
        int cz2 = Math.max(bz1, bz2) >> 4;

        int count = (cx2 - cx1 + 1) * (cz2 - cz1 + 1);
        if (count > MAX_AREA_CHUNKS) {
            player.sendMessage(Component.text(
                    "범위가 너무 넓습니다. 최대 " + MAX_AREA_CHUNKS + "청크까지 가능합니다. (요청: " + count + "청크)")
                    .color(NamedTextColor.RED));
            return;
        }

        World world = player.getWorld();

        if (isClaim) {
            // 사전 검증: 범위 내에 플레이어 청크가 섞여있으면 전체 중단
            List<ChunkPos> playerClaimed = new ArrayList<>();
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    ChunkPos pos = new ChunkPos(world.getName(), cx, cz);
                    ClaimedChunk existing = manager.getChunk(pos);
                    if (existing != null && !existing.isAdmin()) {
                        playerClaimed.add(pos);
                    }
                }
            }
            if (!playerClaimed.isEmpty()) {
                player.sendMessage(Component.text(
                        "범위 내에 플레이어 소유 청크가 " + playerClaimed.size() + "개 있습니다. 먼저 회수해주세요:")
                        .color(NamedTextColor.RED));
                int shown = 0;
                for (ChunkPos p : playerClaimed) {
                    if (shown++ >= 5) {
                        player.sendMessage(Component.text("  ... 외 " + (playerClaimed.size() - 5) + "개")
                                .color(NamedTextColor.GRAY));
                        break;
                    }
                    ClaimedChunk c = manager.getChunk(p);
                    String name = c != null ? Bukkit.getOfflinePlayer(c.getOwner()).getName() : "?";
                    player.sendMessage(Component.text(
                            "  (" + p.getX() + ", " + p.getZ() + ") — " + name).color(NamedTextColor.GRAY));
                }
                return;
            }

            int added = 0, skipped = 0;
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    ChunkPos pos = new ChunkPos(world.getName(), cx, cz);
                    LandManager.AdminClaimResult r = manager.claimAdmin(world, pos);
                    if (r == LandManager.AdminClaimResult.SUCCESS) added++;
                    else if (r == LandManager.AdminClaimResult.ALREADY_ADMIN) skipped++;
                }
            }
            player.sendMessage(Component.text(
                    "관리구역 지정 완료. 신규: " + added + "청크, 기존: " + skipped + "청크")
                    .color(NamedTextColor.GREEN));
        } else {
            int removed = 0, skippedPlayer = 0, empty = 0;
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    ChunkPos pos = new ChunkPos(world.getName(), cx, cz);
                    LandManager.AdminUnclaimResult r = manager.unclaimAdmin(world, pos);
                    switch (r) {
                        case SUCCESS -> removed++;
                        case NOT_ADMIN -> skippedPlayer++;
                        case NOT_CLAIMED -> empty++;
                        default -> { /* WRONG_WORLD는 위에서 걸러짐 */ }
                    }
                }
            }
            player.sendMessage(Component.text(
                    "관리구역 해제 완료. 해제: " + removed + "청크, 스킵(플레이어): " + skippedPlayer
                    + "청크, 스킵(비어있음): " + empty + "청크")
                    .color(NamedTextColor.GREEN));
        }
    }

    // ─────────────────────────────────────────────
    // 도움말
    // ─────────────────────────────────────────────

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("§6=== Land 플러그인 명령어 ==="));
        player.sendMessage(Component.text("§e/land claim §7(또는 /땅 구매) §f- 현재 청크 구매"));
        player.sendMessage(Component.text("§e/land unclaim §7(또는 /땅 반환) §f- 현재 청크 반환 (75% 환불)"));
        player.sendMessage(Component.text("§e/land list §7(또는 /땅 목록) §f- 내 청크 목록"));
        player.sendMessage(Component.text("§e/land info §7(또는 /땅 정보) §f- 청크 정보 모드 토글"));
        if (player.hasPermission("land.admin")) {
            player.sendMessage(Component.text("§c/land admin §7(또는 /땅 관리) §f- 관리자 명령어"));
        }
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(Component.text("§6=== Land 관리자 명령어 ==="));
        player.sendMessage(Component.text("§e/land admin claim §f- 현재 청크를 관리구역으로 지정"));
        player.sendMessage(Component.text("§e/land admin unclaim §f- 현재 청크 관리구역 해제"));
        player.sendMessage(Component.text("§e/land admin claim-area <x1> <z1> <x2> <z2>"));
        player.sendMessage(Component.text("  §7→ 두 블록 좌표 사이의 모든 청크를 관리구역으로"));
        player.sendMessage(Component.text("§e/land admin unclaim-area <x1> <z1> <x2> <z2>"));
        player.sendMessage(Component.text("  §7→ 범위 내 관리구역만 해제 (플레이어 청크는 유지)"));
        player.sendMessage(Component.text("§c/land admin reset-players §7(또는 초기화)"));
        player.sendMessage(Component.text("  §7→ 모든 플레이어 청크 일괄 초기화 (관리구역은 보존)"));
        player.sendMessage(Component.text("§e/land admin reload §7(또는 리로드)"));
        player.sendMessage(Component.text("  §7→ admin_chunks.yml 다시 읽어 관리구역 갱신 (HTML 도구 사용 후)"));
        player.sendMessage(Component.text("§8범위 명령은 최대 " + MAX_AREA_CHUNKS + "청크까지 가능합니다."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> all = new ArrayList<>(List.of(
                    "claim", "unclaim", "list", "info",
                    "구매", "반환", "목록", "정보"));
            if (sender.hasPermission("land.admin")) {
                all.add("admin");
                all.add("관리");
            }
            return all;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("admin") || args[0].equals("관리"))
                && sender.hasPermission("land.admin")) {
            return List.of("claim", "unclaim", "claim-area", "unclaim-area",
                    "reset-players", "reload",
                    "지정", "해제", "범위지정", "범위해제", "초기화", "리로드");
        }
        if (args.length == 3
                && (args[0].equalsIgnoreCase("admin") || args[0].equals("관리"))
                && (args[1].equalsIgnoreCase("reset-players") || args[1].equals("초기화"))
                && sender.hasPermission("land.admin")) {
            return List.of("confirm", "확인");
        }
        return List.of();
    }
}
