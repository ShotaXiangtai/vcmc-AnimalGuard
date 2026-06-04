package net.vcmc.animalguard.listener;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.vcmc.animalguard.VcmcAnimalGuard;
import net.vcmc.animalguard.data.MobRecord;
import net.vcmc.animalguard.manager.GuardManager;
import org.bukkit.Material;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;

public class NameTagListener implements Listener {

    private final VcmcAnimalGuard plugin;
    private final GuardManager guardManager;

    public NameTagListener(VcmcAnimalGuard plugin, GuardManager guardManager) {
        this.plugin = plugin;
        this.guardManager = guardManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only handle Mob entities
        if (!(event.getRightClicked() instanceof Mob mob)) return;

        Player player = event.getPlayer();

        // Check permission
        if (!player.hasPermission("vcmc.animalguard.use")) return;

        // Check if player is holding a name tag
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || item.getType() != Material.NAME_TAG) return;

        // Name tag must have a display name (renamed)
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName() || meta.displayName() == null) return;

        String displayName = PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
        if (displayName.isBlank()) return;

        UUID mobUuid = mob.getUniqueId();

        // Already protected by someone else?
        if (guardManager.isProtected(mobUuid)) {
            if (!guardManager.isOwner(mobUuid, player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("already-protected")));
                return;
            }
            // Owner re-applying name tag — update name in record if changed
            MobRecord existing = guardManager.getRecord(mobUuid);
            if (existing != null && !displayName.equals(existing.mobName())) {
                MobRecord updated = new MobRecord(
                        mobUuid, existing.ownerUuid(), existing.ownerName(),
                        displayName, existing.regDate());
                guardManager.add(updated);
                plugin.getServer().getAsyncScheduler().runNow(plugin,
                        t -> plugin.getDatabaseManager().updateMobName(mobUuid, displayName));
            }
            return;
        }

        // Register protection (vanilla rename still happens naturally)
        MobRecord record = new MobRecord(
                mobUuid,
                player.getUniqueId(),
                player.getName(),
                displayName,
                System.currentTimeMillis()
        );
        guardManager.add(record);

        plugin.getServer().getAsyncScheduler().runNow(plugin,
                t -> plugin.getDatabaseManager().insertMob(record));

        String msg = plugin.buildMessage("protect-success")
                .replace("{name}", displayName);
        player.sendMessage(plugin.parseMiniMessage(msg));
    }
}
