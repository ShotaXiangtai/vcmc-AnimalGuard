package net.vcmc.animalguard.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.vcmc.animalguard.data.MobRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private final Logger logger;
    private HikariDataSource dataSource;

    public DatabaseManager(Logger logger) {
        this.logger = logger;
    }

    public void init(String dbPath) throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setMaximumPoolSize(1); // SQLite is single-writer
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("AnimalGuard-Pool");
        config.setConnectionInitSql("PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;");

        dataSource = new HikariDataSource(config);

        // Ensure table exists (compatible with original AnimalGuard schema)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ag_mobs (
                    mob_uuid VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(64),
                    mob_name TEXT,
                    reg_date BIGINT
                )
            """);
        }
        logger.info("Database connected: " + dbPath);
    }

    public List<MobRecord> loadAll() throws SQLException {
        List<MobRecord> records = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ag_mobs")) {
            while (rs.next()) {
                try {
                    records.add(new MobRecord(
                            UUID.fromString(rs.getString("mob_uuid")),
                            UUID.fromString(rs.getString("owner_uuid")),
                            rs.getString("owner_name"),
                            rs.getString("mob_name"),
                            rs.getLong("reg_date")
                    ));
                } catch (IllegalArgumentException e) {
                    logger.warning("Skipping invalid UUID in database: " + rs.getString("mob_uuid"));
                }
            }
        }
        return records;
    }

    public void insertMob(MobRecord record) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR IGNORE INTO ag_mobs (mob_uuid, owner_uuid, owner_name, mob_name, reg_date) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, record.mobUuid().toString());
            ps.setString(2, record.ownerUuid().toString());
            ps.setString(3, record.ownerName());
            ps.setString(4, record.mobName());
            ps.setLong(5, record.regDate());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert mob record: " + record.mobUuid(), e);
        }
    }

    public void deleteMob(UUID mobUuid) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ag_mobs WHERE mob_uuid = ?")) {
            ps.setString(1, mobUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete mob record: " + mobUuid, e);
        }
    }

    public void updateMobName(UUID mobUuid, String newName) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ag_mobs SET mob_name = ? WHERE mob_uuid = ?")) {
            ps.setString(1, newName);
            ps.setString(2, mobUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update mob name: " + mobUuid, e);
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection closed.");
        }
    }
}
