package net.vcmc.animalguard.listener

import net.vcmc.animalguard.VcmcAnimalGuard
import net.vcmc.animalguard.data.MobRecord
import net.vcmc.animalguard.manager.GuardManager
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityCombustEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.player.PlayerBucketEntityEvent

class EntityProtectListener(
    private val plugin: VcmcAnimalGuard,
    private val guardManager: GuardManager
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        val victim = event.entity as? Mob ?: return
        val victimUuid = victim.uniqueId
        if (!guardManager.isProtected(victimUuid)) return

        val cause = event.cause

        // プレイヤーおよび飛び道具・着火TNTによるダメージ
        if (event is EntityDamageByEntityEvent) {
            var damager: Entity = event.damager

            if (damager is TNTPrimed) {
                val source = damager.source
                if (source != null) {
                    damager = source
                }
            }
            if (damager is Projectile) {
                val shooter = damager.shooter
                if (shooter is Entity) {
                    damager = shooter
                }
            }

            if (damager is Player) {
                if (!plugin.config.getBoolean("protection.player-damage", true)) return
                if (damager.hasPermission("vcmc.animalguard.bypass")) return
                if (guardManager.isOwner(victimUuid, damager.uniqueId)) return

                event.isCancelled = true
                damager.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("protected")))
                return
            }

            // クリーパー・TNT・エンドクリスタル等の爆発ダメージは protection.explosion 設定を優先
            if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
                if (plugin.config.getBoolean("protection.explosion", true)) {
                    event.isCancelled = true
                }
                return
            }

            // モブからの攻撃
            if (plugin.config.getBoolean("protection.mob-damage", true)) {
                event.isCancelled = true
            }
            return
        }

        // ブロック爆発ダメージ (ベッド・リスポーンアンカー等)
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (plugin.config.getBoolean("protection.explosion", true)) {
                event.isCancelled = true
            }
            return
        }

        // 環境ダメージ
        if (plugin.config.getBoolean("protection.environmental", true)) {
            if (cause != EntityDamageEvent.DamageCause.VOID &&
                cause != EntityDamageEvent.DamageCause.KILL &&
                cause != EntityDamageEvent.DamageCause.SUICIDE) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityCombust(event: EntityCombustEvent) {
        if (plugin.config.getBoolean("protection.environmental", true)) {
            val entity = event.entity
            if (entity is Mob && guardManager.isProtected(entity.uniqueId)) {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        val uuid = event.entity.uniqueId
        if (!guardManager.isProtected(uuid)) return

        guardManager.remove(uuid)
        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.databaseManager.deleteMob(uuid)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityTransform(event: EntityTransformEvent) {
        val oldUuid = event.entity.uniqueId
        val record = guardManager.getRecord(oldUuid) ?: return

        val newEntities = event.transformedEntities
        guardManager.remove(oldUuid)

        plugin.server.asyncScheduler.runNow(plugin) { _ ->
            plugin.databaseManager.deleteMob(oldUuid)
        }

        for (newEntity in newEntities) {
            if (newEntity is Mob) {
                val newUuid = newEntity.uniqueId
                val newRecord = MobRecord(newUuid, record.ownerUuid, record.ownerName, record.mobName, record.regDate)
                guardManager.add(newRecord)

                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    plugin.databaseManager.insertMob(newRecord)
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerBucketEntity(event: PlayerBucketEntityEvent) {
        val mobUuid = event.entity.uniqueId
        if (guardManager.isProtected(mobUuid)) {
            val player = event.player
            if (player.hasPermission("vcmc.animalguard.bypass") || guardManager.isOwner(mobUuid, player.uniqueId)) {
                guardManager.remove(mobUuid)
                plugin.server.asyncScheduler.runNow(plugin) { _ ->
                    plugin.databaseManager.deleteMob(mobUuid)
                }
            } else {
                event.isCancelled = true
                player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("protected")))
            }
        }
    }
}
