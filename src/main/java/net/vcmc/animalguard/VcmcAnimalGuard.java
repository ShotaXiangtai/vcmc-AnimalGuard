package net.vcmc.animalguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.vcmc.animalguard.command.AgCommand;
import net.vcmc.animalguard.database.DatabaseManager;
import net.vcmc.animalguard.gui.AdminGui;
import net.vcmc.animalguard.listener.EntityProtectListener;
import net.vcmc.animalguard.listener.NameTagListener;
import net.vcmc.animalguard.manager.GuardManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

public class VcmcAnimalGuard extends JavaPlugin {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DatabaseManager databaseManager;
    private GuardManager guardManager;
    private AdminGui adminGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(getLogger());
        guardManager = new GuardManager();

        // Load DB synchronously on enable (before any ticks)
        try {
            reloadDatabase();
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
            getLogger().severe("Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register GUI (also a listener)
        adminGui = new AdminGui(this, guardManager);

        // Register listeners
        getServer().getPluginManager().registerEvents(
                new EntityProtectListener(this, guardManager), this);
        getServer().getPluginManager().registerEvents(
                new NameTagListener(this, guardManager), this);
        getServer().getPluginManager().registerEvents(adminGui, this);

        // Register command
        AgCommand agCommand = new AgCommand(this, adminGui);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("ag", "AnimalGuard admin command", agCommand));

        getLogger().info("vcmc-AnimalGuard enabled. Protected mobs: " + guardManager.size());
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("vcmc-AnimalGuard disabled.");
    }

    /**
     * (Re)connects to the database and loads all records into GuardManager.
     * Must be called from an async context after initial onEnable.
     */
    public void reloadDatabase() throws SQLException {
        String dbPath = resolveDbPath();
        getLogger().info("Using database: " + dbPath);

        if (databaseManager != null) {
            databaseManager.close();
        }
        databaseManager = new DatabaseManager(getLogger());
        databaseManager.init(dbPath);

        List<net.vcmc.animalguard.data.MobRecord> records = databaseManager.loadAll();
        guardManager.loadAll(records);
        getLogger().info("Loaded " + records.size() + " mob records from database.");
    }

    private String resolveDbPath() {
        String configured = getConfig().getString("database-path", "plugins/AnimalGuard/animalguard.db");
        File f = new File(configured);
        if (!f.isAbsolute()) {
            f = new File(getServer().getWorldContainer(), configured);
        }
        // Create parent dirs if needed (for fresh installs)
        if (!f.getParentFile().exists()) {
            f.getParentFile().mkdirs();
        }
        return f.getAbsolutePath();
    }

    // ---- Message helpers ----

    /**
     * Returns raw message string from config (with prefix prepended).
     */
    public String buildMessage(String key) {
        String prefix = getConfig().getString("messages.prefix", "<gray>[<gold>AG</gold>]</gray> ");
        String msg = getConfig().getString("messages." + key, key);
        return prefix + msg;
    }

    /**
     * Parses a MiniMessage string into a Component.
     */
    public Component parseMiniMessage(String text) {
        return MM.deserialize(text);
    }

    // ---- Getters ----

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public GuardManager getGuardManager() {
        return guardManager;
    }
}
