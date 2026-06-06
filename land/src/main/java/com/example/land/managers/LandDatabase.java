package com.example.land.managers;

import com.example.land.LandPlugin;
import com.example.land.data.ChunkPos;
import com.example.land.data.ClaimedChunk;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * 플레이어 청크 전용 SQLite. 관리구역(ADMIN)은 {@link AdminChunkStore} 의 YAML 에 저장된다.
 * 파일: plugins/Land/player_chunks.db
 *
 * 월드 초기화: 서버 정지 후 player_chunks.db 파일만 삭제하면 끝 (관리구역은 그대로 보존).
 */
public class LandDatabase {

    private static final String DB_FILENAME = "player_chunks.db";
    private static final String LEGACY_DB_FILENAME = "land.db";

    private final LandPlugin plugin;
    private Connection connection;

    public LandDatabase(LandPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        File dataDir = plugin.getDataFolder();
        if (!dataDir.exists()) dataDir.mkdirs();

        File dbFile = new File(dataDir, DB_FILENAME);
        File legacyDb = new File(dataDir, LEGACY_DB_FILENAME);

        try {
            Class.forName("org.sqlite.JDBC");

            // 신규 파일이 없고 구버전 land.db 가 있으면 마이그레이션
            if (!dbFile.exists() && legacyDb.exists()) {
                migrateFromLegacy(legacyDb, dbFile);
            }

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "DB 초기화 실패", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS claimed_chunks (
                    chunk_key TEXT PRIMARY KEY,
                    owner_uuid TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS chunk_members (
                    chunk_key TEXT NOT NULL,
                    member_uuid TEXT NOT NULL,
                    PRIMARY KEY (chunk_key, member_uuid)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS controller_locations (
                    owner_uuid TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL
                )
            """);
        }
    }

    /**
     * 구버전 land.db (PLAYER + ADMIN 통합) 에서 PLAYER 행만 새 player_chunks.db 로 옮기고,
     * ADMIN 행은 admin_chunks.yml 로 export 한다. 작업 후 land.db 는 land.db.bak 으로 리네임.
     *
     * 이 메서드는 신규 파일이 없을 때만 호출되므로 멱등성이 보장된다.
     */
    private void migrateFromLegacy(File legacyDb, File newDb) {
        plugin.getLogger().info("구버전 land.db 발견 → player_chunks.db + admin_chunks.yml 로 마이그레이션 시작");

        Connection legacy = null;
        Connection target = null;
        int playerRows = 0, adminRows = 0;
        List<ChunkPos> adminPositions = new ArrayList<>();

        try {
            legacy = DriverManager.getConnection("jdbc:sqlite:" + legacyDb.getAbsolutePath());
            target = DriverManager.getConnection("jdbc:sqlite:" + newDb.getAbsolutePath());

            // 신규 DB 스키마 생성 (target 에서)
            try (Statement stmt = target.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS claimed_chunks (
                        chunk_key TEXT PRIMARY KEY,
                        owner_uuid TEXT NOT NULL
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS chunk_members (
                        chunk_key TEXT NOT NULL,
                        member_uuid TEXT NOT NULL,
                        PRIMARY KEY (chunk_key, member_uuid)
                    )
                """);
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS controller_locations (
                        owner_uuid TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL
                    )
                """);
            }

            // 구 DB 에 type 컬럼이 있는지 확인 (없으면 전부 PLAYER 로 간주)
            boolean hasType = false;
            try (PreparedStatement ps = legacy.prepareStatement("PRAGMA table_info(claimed_chunks)");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("type".equalsIgnoreCase(rs.getString("name"))) { hasType = true; break; }
                }
            }

            // PLAYER 청크 복사 + ADMIN 좌표 수집
            String selectSql = hasType
                ? "SELECT chunk_key, owner_uuid, type FROM claimed_chunks"
                : "SELECT chunk_key, owner_uuid FROM claimed_chunks";
            target.setAutoCommit(false);
            try (PreparedStatement read = legacy.prepareStatement(selectSql);
                 PreparedStatement write = target.prepareStatement(
                     "INSERT OR REPLACE INTO claimed_chunks (chunk_key, owner_uuid) VALUES (?, ?)");
                 ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    String type = hasType ? rs.getString("type") : "PLAYER";
                    String key = rs.getString("chunk_key");
                    String owner = rs.getString("owner_uuid");
                    if ("ADMIN".equalsIgnoreCase(type)) {
                        adminPositions.add(ChunkPos.fromKey(key));
                        adminRows++;
                    } else {
                        write.setString(1, key);
                        write.setString(2, owner);
                        write.executeUpdate();
                        playerRows++;
                    }
                }
            }

            // 멤버 복사 (PLAYER 청크에 속한 것만 — IN 으로 거른다)
            // 간단히: ADMIN chunk_key 셋을 만들어 그것만 빼고 복사
            Set<String> adminKeys = new HashSet<>();
            for (ChunkPos p : adminPositions) adminKeys.add(p.toKey());
            try (PreparedStatement read = legacy.prepareStatement(
                     "SELECT chunk_key, member_uuid FROM chunk_members");
                 PreparedStatement write = target.prepareStatement(
                     "INSERT OR IGNORE INTO chunk_members (chunk_key, member_uuid) VALUES (?, ?)");
                 ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("chunk_key");
                    if (adminKeys.contains(key)) continue;
                    write.setString(1, key);
                    write.setString(2, rs.getString("member_uuid"));
                    write.executeUpdate();
                }
            } catch (SQLException ignored) {
                // chunk_members 테이블이 구 DB 에 없는 경우 등은 무시
            }

            // controller_locations 복사
            try (PreparedStatement read = legacy.prepareStatement(
                     "SELECT owner_uuid, world, x, y, z FROM controller_locations");
                 PreparedStatement write = target.prepareStatement(
                     "INSERT OR REPLACE INTO controller_locations (owner_uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?)");
                 ResultSet rs = read.executeQuery()) {
                while (rs.next()) {
                    write.setString(1, rs.getString("owner_uuid"));
                    write.setString(2, rs.getString("world"));
                    write.setInt(3, rs.getInt("x"));
                    write.setInt(4, rs.getInt("y"));
                    write.setInt(5, rs.getInt("z"));
                    write.executeUpdate();
                }
            } catch (SQLException ignored) {
                // controller_locations 테이블이 없을 수도 있음
            }

            target.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "마이그레이션 실패. 새 DB 파일을 삭제하고 land.db 는 보존합니다.", e);
            try { if (target != null) target.close(); } catch (SQLException ignored) {}
            // 부분적으로 만들어진 신규 DB 는 폐기 (다음 부팅에 다시 시도)
            if (newDb.exists()) newDb.delete();
            return;
        } finally {
            try { if (legacy != null) legacy.close(); } catch (SQLException ignored) {}
            try { if (target != null && !target.isClosed()) target.close(); } catch (SQLException ignored) {}
        }

        // ADMIN 청크 → admin_chunks.yml 로 export
        if (!adminPositions.isEmpty()) {
            AdminChunkStore.writeMigratedAdminChunks(plugin, adminPositions);
        }

        // 구 파일 백업
        File backup = new File(legacyDb.getParentFile(), LEGACY_DB_FILENAME + ".bak");
        if (backup.exists()) backup.delete();
        if (legacyDb.renameTo(backup)) {
            plugin.getLogger().info("마이그레이션 완료: PLAYER " + playerRows + "행 → player_chunks.db, "
                    + "ADMIN " + adminRows + "행 → admin_chunks.yml. 구 파일은 land.db.bak 으로 백업됨.");
        } else {
            plugin.getLogger().warning("마이그레이션은 끝났지만 land.db → land.db.bak 리네임에 실패했습니다. 수동으로 옮겨주세요.");
        }
    }

    /** 모든 PLAYER 클레임 데이터 로드 */
    public Map<ChunkPos, ClaimedChunk> loadAll() {
        Map<ChunkPos, ClaimedChunk> map = new HashMap<>();
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT chunk_key, owner_uuid FROM claimed_chunks")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ChunkPos pos = ChunkPos.fromKey(rs.getString("chunk_key"));
                    UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                    map.put(pos, new ClaimedChunk(pos, owner));
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT chunk_key, member_uuid FROM chunk_members")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ChunkPos pos = ChunkPos.fromKey(rs.getString("chunk_key"));
                    UUID member = UUID.fromString(rs.getString("member_uuid"));
                    ClaimedChunk chunk = map.get(pos);
                    if (chunk != null) chunk.addMember(member);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "데이터 로드 실패", e);
        }
        return map;
    }

    public void saveClaim(ClaimedChunk chunk) {
        if (chunk.isAdmin()) {
            // 안전망: ADMIN 청크는 이 DB 에 들어오면 안 됨
            plugin.getLogger().warning("ADMIN 청크가 PLAYER DB 로 saveClaim() 호출됨. 무시.");
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO claimed_chunks (chunk_key, owner_uuid) VALUES (?, ?)")) {
            ps.setString(1, chunk.getPos().toKey());
            ps.setString(2, chunk.getOwner().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "클레임 저장 실패", e);
        }
    }

    public void deleteClaim(ChunkPos pos) {
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM claimed_chunks WHERE chunk_key = ?")) {
                ps.setString(1, pos.toKey());
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM chunk_members WHERE chunk_key = ?")) {
                ps.setString(1, pos.toKey());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "클레임 삭제 실패", e);
        }
    }

    public void saveMember(ChunkPos pos, UUID member) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO chunk_members (chunk_key, member_uuid) VALUES (?, ?)")) {
            ps.setString(1, pos.toKey());
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "멤버 저장 실패", e);
        }
    }

    public void deleteMember(ChunkPos pos, UUID member) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM chunk_members WHERE chunk_key = ? AND member_uuid = ?")) {
            ps.setString(1, pos.toKey());
            ps.setString(2, member.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "멤버 삭제 실패", e);
        }
    }

    public void saveControllerLocation(UUID owner, org.bukkit.Location loc) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO controller_locations (owner_uuid, world, x, y, z) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, loc.getWorld().getName());
            ps.setInt(3, loc.getBlockX());
            ps.setInt(4, loc.getBlockY());
            ps.setInt(5, loc.getBlockZ());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "컨트롤러 위치 저장 실패", e);
        }
    }

    public void deleteControllerLocation(UUID owner) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM controller_locations WHERE owner_uuid = ?")) {
            ps.setString(1, owner.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "컨트롤러 위치 삭제 실패", e);
        }
    }

    public Map<UUID, org.bukkit.Location> loadControllerLocations() {
        Map<UUID, org.bukkit.Location> map = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT owner_uuid, world, x, y, z FROM controller_locations")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(rs.getString("world"));
                if (world != null) {
                    org.bukkit.Location loc = new org.bukkit.Location(world,
                            rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    map.put(owner, loc);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "컨트롤러 위치 로드 실패", e);
        }
        return map;
    }

    /**
     * 모든 PLAYER 청크 + 멤버 + 모든 컨트롤러 위치를 일괄 삭제. ADMIN 은 별도 저장이라 손대지 않음.
     * @return 삭제된 PLAYER 청크 수
     */
    public int resetPlayerClaims() {
        int deleted = 0;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM claimed_chunks");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) deleted = rs.getInt(1);
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM chunk_members");
                stmt.executeUpdate("DELETE FROM claimed_chunks");
                stmt.executeUpdate("DELETE FROM controller_locations");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "PLAYER 청크 일괄 삭제 실패", e);
        }
        return deleted;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "DB 닫기 실패", e);
        }
    }
}
