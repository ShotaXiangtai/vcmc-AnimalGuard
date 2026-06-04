package net.vcmc.animalguard.listener;

import net.vcmc.animalguard.VcmcAnimalGuard;
import net.vcmc.animalguard.data.MobRecord;
import net.vcmc.animalguard.manager.GuardManager;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.UUID;

public class EntityProtectListener implements Listener {

    private final VcmcAnimalGuard plugin;
    private final GuardManager guardManager;

    public EntityProtectListener(VcmcAnimalGuard plugin, GuardManager guardManager) {
        this.plugin = plugin;
        this.guardManager = guardManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Mob)) return;

        UUID victimUuid = victim.getUniqueId();
        if (!guardManager.isProtected(victimUuid)) return;

        EntityDamageEvent.DamageCause cause = event.getCause();

        // Handle player and projectile damage
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();

            // Unwrap projectiles
            if (damager instanceof Projectile projectile) {
                ProjectileSource source = projectile.getShooter();
                if (source instanceof Entity shooter) {
                    damager = shooter;
                }
            }

            if (damager instanceof Player player) {
                // Bypass permission check
                if (player.hasPermission("vcmc.animalguard.bypass")) return;
                // Owner can damage their own mob
                if (guardManager.isOwner(victimUuid, player.getUniqueId())) return;

                event.setCancelled(true);
                player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("protected")));
                return;
            }

            // Non-player entity damage (mobs attacking)
            if (plugin.getConfig().getBoolean("protection.mob-damage", true)) {
                event.setCancelled(true);
            }
            return;
        }

        // Explosion protection
        if (cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            if (plugin.getConfig().getBoolean("protection.explosion", true)) {
                event.setCancelled(true);
            }
            return;
        }

        // Environmental protection (fire, lava, drowning, suffocation, etc.)
        if (plugin.getConfig().getBoolean("protection.environmental", true)) {
            switch (cause) {
                case FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION,
                     FALL, VOID, LIGHTNING, MELTING, FREEZE,
                     WITHER, POISON, MAGIC, HOT_FLOOR -> event.setCancelled(true);
                default -> { /* allow */ }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (!guardManager.isProtected(uuid)) return;

        // Mob died somehow (e.g. owner killed it) — remove from DB
        guardManager.remove(uuid);
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                t -> plugin.getDatabaseManager().deleteMob(uuid));
    }
}
