package com.server.core.data;

import com.server.core.CorePlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.UUID;

/**
 * 플레이어 데이터 통합 관리자.
 * 모든 플러그인의 플레이어 데이터를 하나의 SQLite DB에서 관리한다.
 * DB 위치: plugins/Core/players.db
 *
 * 현재 테이블:
 *   - players: uuid, name, balance
 *
 * 새 플러그인에서 컬럼/테이블 추가 시 이 클래스에 메서드를 추가할 것.
 */
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
                        balance INTEGER NOT NULL DEFAULT 1000
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

    // ───────────────────────────────────────────
    // 플레이어 등록
    // ───────────────────────────────────────────

    /**
     * 첫 접속 시 플레이어 등록. 이미 있으면 이름만 업데이트.
     * @return true = 신규 등록 (초기 지급), false = 기존 플레이어
     */
    public boolean registerIfAbsent(Player player) {
        String uuid = player.getUniqueId().toString();
        String name = player.getName();

        try {
            // 신규 등록 시도
            String insert = "INSERT OR IGNORE INTO players (uuid, name, balance) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(insert)) {
                ps.setString(1, uuid);
                ps.setString(2, name);
                ps.setLong(3, INITIAL_BALANCE);
                int affected = ps.executeUpdate();
                if (affected > 0) return true; // 신규
            }
            // 기존 플레이어 — 이름만 업데이트 (닉네임 변경 대비)
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

    // ───────────────────────────────────────────
    // 경제 관련
    // ───────────────────────────────────────────

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
        String sql = "UPDATE players SET balance = ? WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
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
}
