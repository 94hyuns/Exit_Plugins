package com.exit.core.data;

import com.exit.core.CorePlugin;
import com.exit.core.events.BalanceChangeEvent;
import com.exit.core.events.ShardChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerDataManager {

    private static final long INITIAL_BALANCE = 1000L;

    private final CorePlugin plugin;
    private Connection connection;

    public PlayerDataManager(CorePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String url = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + "/players.db";
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid    TEXT PRIMARY KEY,
                        name    TEXT NOT NULL,
                        balance INTEGER NOT NULL DEFAULT 1000,
                        shards  INTEGER NOT NULL DEFAULT 0
                    )
                """);
                // 기존 DB 마이그레이션: shards 컬럼 없으면 추가
                // SQLite는 IF NOT EXISTS를 ALTER에서 지원 안 하므로 PRAGMA로 확인
                boolean hasShards = false;
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(players)")) {
                    while (rs.next()) {
                        if ("shards".equalsIgnoreCase(rs.getString("name"))) {
                            hasShards = true;
                            break;
                        }
                    }
                }
                if (!hasShards) {
                    stmt.execute("ALTER TABLE players ADD COLUMN shards INTEGER NOT NULL DEFAULT 0");
                    plugin.getLogger().info("players 테이블에 shards 컬럼 추가 (마이그레이션)");
                }

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_cosmetics (
                        player_uuid  TEXT NOT NULL,
                        cosmetic_id  TEXT NOT NULL,
                        acquired_at  INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, cosmetic_id)
                    )
                """);
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS equipped_cosmetics (
                        player_uuid  TEXT NOT NULL,
                        slot         TEXT NOT NULL,
                        cosmetic_id  TEXT NOT NULL,
                        PRIMARY KEY (player_uuid, slot)
                    )
                """);
            }
            plugin.getLogger().info("PlayerDataManager 초기화 완료");
        } catch (SQLException e) {
            plugin.getLogger().severe("DB 초기화 실패: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("DB 종료 실패: " + e.getMessage());
        }
    }

    public boolean registerIfAbsent(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        try {
            String insert = "INSERT OR IGNORE INTO players (uuid, name, balance) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, uuid);
                ps.setString(2, name);
                ps.setLong(3, INITIAL_BALANCE);
                if (ps.executeUpdate() > 0) return true;
            }
            String update = "UPDATE players SET name = ? WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(update)) {
                ps.setString(1, name);
                ps.setString(2, uuid);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("플레이어 등록 실패: " + e.getMessage());
        }
        return false;
    }

    public long getBalance(UUID uuid) {
        String sql = "SELECT balance FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("잔액 조회 실패: " + e.getMessage());
        }
        return -1L;
    }

    public boolean setBalance(UUID uuid, long amount) {
        if (amount < 0) return false;
        long oldBalance = getBalance(uuid);
        String sql = "UPDATE players SET balance = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            // 잔액 변경 이벤트 발행
            Bukkit.getPluginManager().callEvent(new BalanceChangeEvent(uuid, oldBalance, amount));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("잔액 설정 실패: " + e.getMessage());
            return false;
        }
    }

    public boolean addBalance(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getBalance(uuid);
        if (current < 0) return false;
        return setBalance(uuid, current + amount);
    }

    public boolean subtractBalance(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getBalance(uuid);
        if (current < amount) return false;
        return setBalance(uuid, current - amount);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 가루(shards) 시스템
    // ═══════════════════════════════════════════════════════════════════

    public long getShards(UUID uuid) {
        String sql = "SELECT shards FROM players WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("shards");
        } catch (SQLException e) {
            plugin.getLogger().severe("가루 조회 실패: " + e.getMessage());
        }
        return -1L;
    }

    public boolean setShards(UUID uuid, long amount) {
        if (amount < 0) return false;
        long oldShards = getShards(uuid);
        if (oldShards < 0) return false;
        String sql = "UPDATE players SET shards = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            Bukkit.getPluginManager().callEvent(new ShardChangeEvent(uuid, oldShards, amount));
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("가루 설정 실패: " + e.getMessage());
            return false;
        }
    }

    public boolean addShards(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getShards(uuid);
        if (current < 0) return false;
        return setShards(uuid, current + amount);
    }

    public boolean subtractShards(UUID uuid, long amount) {
        if (amount <= 0) return false;
        long current = getShards(uuid);
        if (current < amount) return false;
        return setShards(uuid, current - amount);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 치장 시스템 (Cosmetics)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 치장 지급. 이미 보유 중이면 false (INSERT OR IGNORE).
     * Shop 구매, 관리자 지급, 이벤트 보상 등에서 호출.
     */
    public boolean grantCosmetic(UUID uuid, String cosmeticId) {
        String sql = "INSERT OR IGNORE INTO player_cosmetics (player_uuid, cosmetic_id, acquired_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, cosmeticId);
            ps.setLong(3, System.currentTimeMillis());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 지급 실패: " + e.getMessage());
            return false;
        }
    }

    public boolean hasCosmetic(UUID uuid, String cosmeticId) {
        String sql = "SELECT 1 FROM player_cosmetics WHERE player_uuid = ? AND cosmetic_id = ? LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, cosmeticId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 보유 확인 실패: " + e.getMessage());
            return false;
        }
    }

    public Set<String> listOwnedCosmetics(UUID uuid) {
        Set<String> result = new HashSet<>();
        String sql = "SELECT cosmetic_id FROM player_cosmetics WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(rs.getString("cosmetic_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 목록 조회 실패: " + e.getMessage());
        }
        return result;
    }

    /** 해당 슬롯에 장착. 기존 슬롯 값은 자동 교체 (UPSERT). */
    public boolean setEquippedCosmetic(UUID uuid, String slot, String cosmeticId) {
        String sql = """
            INSERT INTO equipped_cosmetics (player_uuid, slot, cosmetic_id) VALUES (?, ?, ?)
            ON CONFLICT(player_uuid, slot) DO UPDATE SET cosmetic_id = excluded.cosmetic_id
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, slot.toUpperCase());
            ps.setString(3, cosmeticId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 장착 실패: " + e.getMessage());
            return false;
        }
    }

    /** 해당 슬롯의 장착 해제. 원래 미장착이면 false. */
    public boolean clearEquippedCosmetic(UUID uuid, String slot) {
        String sql = "DELETE FROM equipped_cosmetics WHERE player_uuid = ? AND slot = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, slot.toUpperCase());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 해제 실패: " + e.getMessage());
            return false;
        }
    }

    /** 슬롯별 장착된 cosmeticId 조회. 미장착이면 null. */
    public String getEquippedCosmetic(UUID uuid, String slot) {
        String sql = "SELECT cosmetic_id FROM equipped_cosmetics WHERE player_uuid = ? AND slot = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, slot.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("cosmetic_id");
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 장착 조회 실패: " + e.getMessage());
        }
        return null;
    }

    /** 플레이어가 장착 중인 모든 슬롯→cosmeticId 맵. 이벤트 리스너에서 재적용할 때 사용. */
    public Map<String, String> getAllEquippedCosmetics(UUID uuid) {
        Map<String, String> result = new HashMap<>();
        String sql = "SELECT slot, cosmetic_id FROM equipped_cosmetics WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("slot"), rs.getString("cosmetic_id"));
        } catch (SQLException e) {
            plugin.getLogger().severe("치장 장착 전체 조회 실패: " + e.getMessage());
        }
        return result;
    }
}
