package net.vcmc.animalguard.command

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.vcmc.animalguard.VcmcAnimalGuard
import net.vcmc.animalguard.gui.AdminGui
import org.bukkit.entity.Player
import java.sql.SQLException

class AgCommand(
    private val plugin: VcmcAnimalGuard,
    private val adminGui: AdminGui
) : BasicCommand {

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender

        if (sender !is Player) {
            sender.sendMessage(Component.text("This command can only be run by a player."))
            return
        }

        if (!sender.hasPermission("vcmc.animalguard.admin")) {
            sender.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("no-permission")))
            return
        }

        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            handleReload(sender)
            return
        }

        adminGui.open(sender)
    }

    private fun handleReload(player: Player) {
        plugin.reloadConfig()
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            try {
                plugin.reloadDatabase()
                val count = plugin.guardManager.size
                player.scheduler.run(plugin, { _ ->
                    val msg = plugin.buildMessage("reloaded").replace("{count}", count.toString())
                    player.sendMessage(plugin.parseMiniMessage(msg))
                }, null)
            } catch (e: SQLException) {
                plugin.logger.severe("Failed to reload database: ${e.message}")
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(plugin.parseMiniMessage("<red>データベースのリロードに失敗しました: ${e.message}"))
                }, null)
            }
        }
    }

    override fun suggest(stack: CommandSourceStack, args: Array<out String>): Collection<String> {
        if (args.size <= 1 && stack.sender.hasPermission("vcmc.animalguard.admin")) {
            val prefix = if (args.isEmpty()) "" else args[0].lowercase()
            return listOf("reload").filter { it.startsWith(prefix) }
        }
        return emptyList()
    }
}
