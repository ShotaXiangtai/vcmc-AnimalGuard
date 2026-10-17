package net.vcmc.animalguard.listener

import io.papermc.paper.event.player.PlayerNameEntityEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.vcmc.animalguard.VcmcAnimalGuard
import net.vcmc.animalguard.data.MobRecord
import net.vcmc.animalguard.manager.GuardManager
import org.bukkit.entity.Mob
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

class NameTagListener(
    private val plugin: VcmcAnimalGuard,
    private val guardManager: GuardManager
) : Listener {

    companion object {
        val DEFAULT_PROTECTABLE_MOBS: Set<String> = setOf(
            "ALLAY",
            "ARMADILLO",
            "AXOLOTL",
            "BAT",
            "BEE",
            "CAMEL",
            "CAMEL_HUSK",
            "CAT",
            "CHICKEN",
            "COD",
            "COPPER_GOLEM",
            "COW",
            "DOLPHIN",
            "DONKEY",
            "FOX",
            "FROG",
            "GLOW_SQUID",
            "GOAT",
            "HAPPY_GHAST",
            "HORSE",
            "IRON_GOLEM",
            "LLAMA",
            "MOOSHROOM",
            "MULE",
            "NAUTILUS",
            "OCELOT",
            "PANDA",
            "PARROT",
            "PIG",
            "POLAR_BEAR",
            "PUFFERFISH",
            "RABBIT",
            "SALMON",
            "SHEEP",
            "SKELETON_HORSE",
            "SNIFFER",
            "SNOW_GOLEM",
            "SQUID",
            "STRIDER",
            "SULFUR_CUBE",
            "TADPOLE",
            "TRADER_LLAMA",
            "TROPICAL_FISH",
            "TURTLE",
            "VILLAGER",
            "WANDERING_TRADER",
            "WOLF",
            "ZOMBIE_HORSE",
            "ZOMBIE_NAUTILUS"
        )
    }

    private fun isProtectableMob(mob: Mob): Boolean {
        if (!plugin.config.getBoolean("protection.only-listed-mobs", true)) {
            return true
        }
        val configured = plugin.config.getStringList("protectable-mobs")
        val allowed = if (configured.isEmpty()) {
            DEFAULT_PROTECTABLE_MOBS
        } else {
            configured.mapTo(HashSet()) { it.trim().uppercase() }
        }
        return mob.type.name.uppercase() in allowed
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onPlayerNameEntity(event: PlayerNameEntityEvent) {
        val mob = event.entity as? Mob ?: return
        val player = event.player

        if (!player.hasPermission("vcmc.animalguard.use")) return

        val nameComp = event.name ?: return
        val displayName = PlainTextComponentSerializer.plainText().serialize(nameComp)
        if (displayName.isBlank()) return

        val mobUuid = mob.uniqueId

        // すでに他人に保護されているか？
        if (guardManager.isProtected(mobUuid)) {
            if (!guardManager.isOwner(mobUuid, player.uniqueId)) {
                event.isCancelled = true
                player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("already-protected")))
                return
            }
            // オーナーによる名札の付け直し — 名前が変わった場合は更新
            val existing = guardManager.getRecord(mobUuid)
            if (existing != null && displayName != existing.mobName) {
                val updated = MobRecord(
                    mobUuid = mobUuid,
                    ownerUuid = existing.ownerUuid,
                    ownerName = existing.ownerName,
                    mobName = displayName,
                    regDate = existing.regDate
                )
                guardManager.add(updated)
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    plugin.databaseManager.updateMobName(mobUuid, displayName)
                }
            }
            return
        }

        // 保護対象の友好・中立・騎乗Mobかチェック
        if (!isProtectableMob(mob)) return

        // 新規保護登録
        val record = MobRecord(
            mobUuid = mobUuid,
            ownerUuid = player.uniqueId,
            ownerName = player.name,
            mobName = displayName,
            regDate = System.currentTimeMillis()
        )
        guardManager.add(record)

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.databaseManager.insertMob(record)
        }

        val safeName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(displayName)
        val msg = plugin.buildMessage("protect-success").replace("{name}", safeName)
        player.sendMessage(plugin.parseMiniMessage(msg))
    }
}

