package net.vcmc.animalguard

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.vcmc.animalguard.command.AgCommand
import net.vcmc.animalguard.database.DatabaseManager
import net.vcmc.animalguard.gui.AdminGui
import net.vcmc.animalguard.listener.EntityProtectListener
import net.vcmc.animalguard.listener.NameTagListener
import net.vcmc.animalguard.manager.GuardManager
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.SQLException

class VcmcAnimalGuard : JavaPlugin() {

    companion object {
        private val MM = MiniMessage.miniMessage()
    }

    @Volatile
    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var guardManager: GuardManager
        private set

    lateinit var adminGui: AdminGui
        private set

    override fun onEnable() {
        saveDefaultConfig()

        guardManager = GuardManager()

        try {
            reloadDatabase()
        } catch (e: SQLException) {
            logger.severe("Failed to connect to database: ${e.message}")
            logger.severe("Plugin will be disabled.")
            server.pluginManager.disablePlugin(this)
            return
        }

        adminGui = AdminGui(this, guardManager)

        server.pluginManager.registerEvents(EntityProtectListener(this, guardManager), this)
        server.pluginManager.registerEvents(NameTagListener(this, guardManager), this)
        server.pluginManager.registerEvents(adminGui, this)

        val agCommand = AgCommand(this, adminGui)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register("ag", "AnimalGuard admin command", agCommand)
        }

        logger.info("vcmc-AnimalGuard enabled. Protected mobs: ${guardManager.size}")
    }

    override fun onDisable() {
        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }
        logger.info("vcmc-AnimalGuard disabled.")
    }

    @Synchronized
    @Throws(SQLException::class)
    fun reloadDatabase() {
        val dbPath = resolveDbPath()
        logger.info("Using database: $dbPath")

        val newDb = DatabaseManager(logger)
        newDb.init(dbPath)
        val records = newDb.loadAll()

        val oldDb = if (::databaseManager.isInitialized) databaseManager else null
        databaseManager = newDb
        oldDb?.close()

        guardManager.loadAll(records)
        logger.info("Loaded ${records.size} mob records from database.")
    }

    private fun resolveDbPath(): String {
        val configured = config.getString("database-path", "plugins/AnimalGuard/animalguard.db") ?: "plugins/AnimalGuard/animalguard.db"
        var f = File(configured)
        if (!f.isAbsolute) {
            f = File(server.worldContainer, configured)
        }
        f.parentFile?.mkdirs()
        return f.absolutePath
    }

    fun buildMessage(key: String): String {
        val prefix = config.getString("messages.prefix", "<gray>[<gold>AG</gold>]</gray> ") ?: "<gray>[<gold>AG</gold>]</gray> "
        val msg = config.getString("messages.$key", key) ?: key
        return prefix + msg
    }

    fun parseMiniMessage(text: String): Component {
        return MM.deserialize(text)
    }
}
