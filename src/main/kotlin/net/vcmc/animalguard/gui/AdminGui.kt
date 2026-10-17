package net.vcmc.animalguard.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.vcmc.animalguard.VcmcAnimalGuard
import net.vcmc.animalguard.data.MobRecord
import net.vcmc.animalguard.manager.GuardManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class AdminGui(
    private val plugin: VcmcAnimalGuard,
    private val guardManager: GuardManager
) : Listener {

    enum class SortMode(val label: String) {
        DATE("日時順"),
        NAME("名前順"),
        OWNER("オーナー順");

        fun next(): SortMode = values()[(ordinal + 1) % values().size]
    }

    private class GuiSession(
        var page: Int,
        var sortMode: SortMode
    ) {
        var inventory: Inventory? = null
        var records: List<MobRecord> = emptyList()
    }

    companion object {
        private val MM = MiniMessage.miniMessage()
        private val DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    private val sessions = ConcurrentHashMap<UUID, GuiSession>()

    fun open(player: Player) {
        val session = sessions.computeIfAbsent(player.uniqueId) {
            GuiSession(0, SortMode.DATE)
        }
        session.page = 0
        buildAndOpen(player, session)
    }

    private fun buildAndOpen(player: Player, session: GuiSession) {
        val sorted = getSorted(session.sortMode)
        session.records = sorted
        val itemsPerPage = 45
        val totalPages = max(1, ceil(sorted.size / itemsPerPage.toDouble()).toInt())
        session.page = min(session.page, totalPages - 1)

        val titleStr = plugin.config.getString("gui.title", "<dark_gray>AnimalGuard 管理パネル") ?: "<dark_gray>AnimalGuard 管理パネル"
        val inv = Bukkit.createInventory(
            null,
            54,
            MM.deserialize("$titleStr <gray>[${session.page + 1}/$totalPages]")
        )

        val start = session.page * itemsPerPage
        for (i in 0 until itemsPerPage) {
            val idx = start + i
            if (idx >= sorted.size) break
            inv.setItem(i, buildMobItem(sorted[idx]))
        }

        fillControlBar(inv, session, totalPages)

        session.inventory = inv
        player.openInventory(inv)
    }

    private fun fillControlBar(inv: Inventory, session: GuiSession, totalPages: Int) {
        // Slot 45: Previous page
        if (session.page > 0) {
            inv.setItem(45, buildControlItem(Material.ARROW, "<yellow>前のページ", "<gray>クリックで前ページへ"))
        } else {
            inv.setItem(45, buildControlItem(Material.GRAY_STAINED_GLASS_PANE, "<gray>前のページ", ""))
        }

        // Slot 47: Total count info
        inv.setItem(47, buildControlItem(Material.BOOK, "<white>保護Mob数: <gold>${guardManager.size}", "<gray>登録されている保護Mob総数"))

        // Slot 49: Sort button
        inv.setItem(49, buildControlItem(Material.COMPASS, "<aqua>並び替え: <white>${session.sortMode.label}", "<gray>クリックで切り替え"))

        // Slot 53: Next page
        if (session.page < totalPages - 1) {
            inv.setItem(53, buildControlItem(Material.ARROW, "<yellow>次のページ", "<gray>クリックで次ページへ"))
        } else {
            inv.setItem(53, buildControlItem(Material.GRAY_STAINED_GLASS_PANE, "<gray>次のページ", ""))
        }

        for (s in intArrayOf(46, 48, 50, 51, 52)) {
            inv.setItem(s, buildPane())
        }
    }

    private fun buildMobItem(record: MobRecord): ItemStack {
        val entity = runCatching { Bukkit.getEntity(record.mobUuid) }.getOrNull()
        val entityType = runCatching { entity?.type }.getOrNull()
        val worldName = runCatching { entity?.world?.name }.getOrNull()
        val mat = if (entityType != null) getSpawnEgg(entityType) else Material.NAME_TAG

        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item

        val safeMobName = MM.escapeTags(record.mobName ?: "名称不明")
        val safeOwnerName = MM.escapeTags(record.ownerName)
        val name = MM.deserialize("<yellow>$safeMobName")
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        val lore = mutableListOf<Component>()
        lore.add(plain("<gray>オーナー: <white>$safeOwnerName"))
        lore.add(plain("<gray>UUID: <dark_gray>${record.mobUuid.toString().take(8)}..."))
        lore.add(plain("<gray>保護日時: <white>${DATE_FMT.format(Instant.ofEpochMilli(record.regDate))}"))
        if (entity != null && worldName != null) {
            lore.add(plain("<green>● ロード済み"))
            lore.add(plain("<gray>ワールド: <white>${MM.escapeTags(worldName)}"))
        } else {
            lore.add(plain("<red>● アンロード済み"))
        }
        lore.add(Component.empty())
        lore.add(plain("<red>左クリック: <white>保護解除"))
        lore.add(plain("<aqua>右クリック: <white>テレポート"))
        lore.add(plain("<yellow>中クリック: <white>グロウ切替"))

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun buildControlItem(mat: Material, name: String, lore: String): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta ?: return item
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false))
        if (lore.isNotEmpty()) {
            meta.lore(listOf(MM.deserialize(lore).decoration(TextDecoration.ITALIC, false)))
        }
        item.itemMeta = meta
        return item
    }

    private fun buildPane(): ItemStack {
        val pane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = pane.itemMeta ?: return pane
        meta.displayName(Component.empty())
        pane.itemMeta = meta
        return pane
    }

    private fun plain(miniMsg: String): Component {
        return MM.deserialize(miniMsg).decoration(TextDecoration.ITALIC, false)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        val currentInv = session.inventory ?: return

        if (event.view.topInventory == currentInv) {
            event.isCancelled = true

            val slot = event.rawSlot
            if (slot !in 0..53) return

            if (slot >= 45) {
                handleControlClick(player, session, slot)
                return
            }

            val idx = session.page * 45 + slot
            if (idx >= session.records.size) return

            val record = session.records[idx]
            when (event.click) {
                ClickType.LEFT, ClickType.SHIFT_LEFT -> handleDelete(player, session, record)
                ClickType.RIGHT, ClickType.SHIFT_RIGHT -> handleTeleport(player, record)
                ClickType.MIDDLE -> handleGlow(player, record)
                else -> {}
            }
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = sessions[player.uniqueId] ?: return
        if (session.inventory != null && event.view.topInventory == session.inventory) {
            event.isCancelled = true
        }
    }

    private fun handleControlClick(player: Player, session: GuiSession, slot: Int) {
        if (slot == 45 && session.page > 0) {
            session.page--
            buildAndOpen(player, session)
        } else if (slot == 53) {
            val totalPages = max(1, ceil(session.records.size / 45.0).toInt())
            if (session.page < totalPages - 1) {
                session.page++
                buildAndOpen(player, session)
            }
        } else if (slot == 49) {
            session.sortMode = session.sortMode.next()
            buildAndOpen(player, session)
        }
    }

    private fun handleDelete(player: Player, session: GuiSession, record: MobRecord) {
        guardManager.remove(record.mobUuid)
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.databaseManager.deleteMob(record.mobUuid)
        }
        player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("remove-success")))
        buildAndOpen(player, session)
    }

    private fun handleTeleport(player: Player, record: MobRecord) {
        player.closeInventory()
        sessions.remove(player.uniqueId)

        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val entity = Bukkit.getEntity(record.mobUuid)
            if (entity == null) {
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("entity-not-found")))
                }, null)
                return@run
            }
            entity.scheduler.run(plugin, { _ ->
                val loc = entity.location
                player.teleportAsync(loc).thenAccept { success ->
                    if (success) {
                        player.scheduler.run(plugin, { _ ->
                            player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("teleport")))
                        }, null)
                    }
                }
            }, null)
        }
    }

    private fun handleGlow(player: Player, record: MobRecord) {
        plugin.server.globalRegionScheduler.run(plugin) { _ ->
            val entity = Bukkit.getEntity(record.mobUuid)
            if (entity == null) {
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("entity-not-found")))
                }, null)
                return@run
            }
            entity.scheduler.run(plugin, { _ ->
                entity.isGlowing = !entity.isGlowing
                val glow = entity.isGlowing
                player.scheduler.run(plugin, { _ ->
                    player.sendMessage(plugin.parseMiniMessage("<gray>グロウ: " + (if (glow) "<green>ON" else "<red>OFF")))
                }, null)
            }, null)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val session = sessions[player.uniqueId]
        if (session != null && event.inventory == session.inventory) {
            sessions.remove(player.uniqueId)
        }
    }

    private fun getSorted(mode: SortMode): List<MobRecord> {
        val list = ArrayList(guardManager.all)
        return when (mode) {
            SortMode.NAME -> list.sortedBy { it.mobName ?: "" }
            SortMode.DATE -> list.sortedByDescending { it.regDate }
            SortMode.OWNER -> list.sortedBy { it.ownerName }
        }
    }

    private fun getSpawnEgg(type: EntityType): Material {
        return try {
            Material.valueOf("${type.name}_SPAWN_EGG")
        } catch (e: IllegalArgumentException) {
            Material.NAME_TAG
        }
    }
}
