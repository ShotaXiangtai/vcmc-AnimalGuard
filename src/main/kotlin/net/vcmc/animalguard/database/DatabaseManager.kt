package net.vcmc.animalguard.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import net.vcmc.animalguard.data.MobRecord
import java.sql.SQLException
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

class DatabaseManager(private val logger: Logger) {

    private var dataSource: HikariDataSource? = null

    @Throws(SQLException::class)
    fun init(dbPath: String) {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:$dbPath"
            maximumPoolSize = 1 // SQLite is single-writer
            minimumIdle = 1
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "AnimalGuard-Pool"
            connectionInitSql = "PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;"
        }

        val ds = HikariDataSource(config)
        dataSource = ds

        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS ag_mobs (
                        mob_uuid VARCHAR(36) PRIMARY KEY,
                        owner_uuid VARCHAR(36) NOT NULL,
                        owner_name VARCHAR(64),
                        mob_name TEXT,
                        reg_date BIGINT
                    )
                    """.trimIndent()
                )
            }
        }
        logger.info("Database connected: $dbPath")
    }

    @Throws(SQLException::class)
    fun loadAll(): List<MobRecord> {
        val records = mutableListOf<MobRecord>()
        val ds = dataSource ?: return records
        ds.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT * FROM ag_mobs").use { rs ->
                    while (rs.next()) {
                        try {
                            records.add(
                                MobRecord(
                                    mobUuid = UUID.fromString(rs.getString("mob_uuid")),
                                    ownerUuid = UUID.fromString(rs.getString("owner_uuid")),
                                    ownerName = rs.getString("owner_name") ?: "",
                                    mobName = rs.getString("mob_name"),
                                    regDate = rs.getLong("reg_date")
                                )
                            )
                        } catch (e: IllegalArgumentException) {
                            logger.warning("Skipping invalid UUID in database: " + rs.getString("mob_uuid"))
                        }
                    }
                }
            }
        }
        return records
    }

    fun insertMob(record: MobRecord) {
        val ds = dataSource ?: return
        try {
            ds.connection.use { conn ->
                conn.prepareStatement(
                    "INSERT OR IGNORE INTO ag_mobs (mob_uuid, owner_uuid, owner_name, mob_name, reg_date) VALUES (?, ?, ?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, record.mobUuid.toString())
                    ps.setString(2, record.ownerUuid.toString())
                    ps.setString(3, record.ownerName)
                    ps.setString(4, record.mobName)
                    ps.setLong(5, record.regDate)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.log(Level.SEVERE, "Failed to insert mob record: ${record.mobUuid}", e)
        }
    }

    fun deleteMob(mobUuid: UUID) {
        val ds = dataSource ?: return
        try {
            ds.connection.use { conn ->
                conn.prepareStatement("DELETE FROM ag_mobs WHERE mob_uuid = ?").use { ps ->
                    ps.setString(1, mobUuid.toString())
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.log(Level.SEVERE, "Failed to delete mob record: $mobUuid", e)
        }
    }

    fun updateMobName(mobUuid: UUID, newName: String) {
        val ds = dataSource ?: return
        try {
            ds.connection.use { conn ->
                conn.prepareStatement("UPDATE ag_mobs SET mob_name = ? WHERE mob_uuid = ?").use { ps ->
                    ps.setString(1, newName)
                    ps.setString(2, mobUuid.toString())
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            logger.log(Level.SEVERE, "Failed to update mob name: $mobUuid", e)
        }
    }

    fun close() {
        dataSource?.let {
            if (!it.isClosed) {
                it.close()
                logger.info("Database connection closed.")
            }
        }
    }
}
