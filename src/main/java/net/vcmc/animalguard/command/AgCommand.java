package net.vcmc.animalguard.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.vcmc.animalguard.VcmcAnimalGuard;
import net.vcmc.animalguard.gui.AdminGui;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class AgCommand implements BasicCommand {

    private final VcmcAnimalGuard plugin;
    private final AdminGui adminGui;

    public AgCommand(VcmcAnimalGuard plugin, AdminGui adminGui) {
        this.plugin = plugin;
        this.adminGui = adminGui;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player."));
            return;
        }

        if (!player.hasPermission("vcmc.animalguard.admin")) {
            player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("no-permission")));
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            handleReload(player);
            return;
        }

        // Open admin GUI — must run on player's region thread (already on it for commands)
        adminGui.open(player);
    }

    private void handleReload(Player player) {
        plugin.reloadConfig();
        plugin.getServer().getAsyncScheduler().runNow(plugin, t -> {
            try {
                // Reload database (might have changed db path in config)
                plugin.reloadDatabase();
                int count = plugin.getGuardManager().size();
                player.getScheduler().run(plugin, st -> {
                    String msg = plugin.buildMessage("reloaded").replace("{count}", String.valueOf(count));
                    player.sendMessage(plugin.parseMiniMessage(msg));
                }, null);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to reload database: " + e.getMessage());
                player.getScheduler().run(plugin, st ->
                        player.sendMessage(plugin.parseMiniMessage(
                                "<red>データベースのリロードに失敗しました: " + e.getMessage())), null);
            }
        });
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        if (args.length == 1 && stack.getSender().hasPermission("vcmc.animalguard.admin")) {
            return List.of("reload");
        }
        return List.of();
    }
}
