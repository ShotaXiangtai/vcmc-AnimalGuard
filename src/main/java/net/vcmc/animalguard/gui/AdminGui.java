package net.vcmc.animalguard.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.vcmc.animalguard.VcmcAnimalGuard;
import net.vcmc.animalguard.data.MobRecord;
import net.vcmc.animalguard.manager.GuardManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AdminGui implements Listener {

    public enum SortMode {
        DATE("日時順"), NAME("名前順"), OWNER("オーナー順");

        final String label;
        SortMode(String label) { this.label = label; }
        SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy/MM/dd HH:mm")
            .withZone(ZoneId.systemDefault());

    // Track open GUI sessions: player UUID -> GuiSession
    private final Map<UUID, GuiSession> sessions = new ConcurrentHashMap<>();
    private final VcmcAnimalGuard plugin;
    private final GuardManager guardManager;

    public AdminGui(VcmcAnimalGuard plugin, GuardManager guardManager) {
        this.plugin = plugin;
        this.guardManager = guardManager;
    }

    // ---- Open GUI ----

    public void open(Player player) {
        GuiSession session = sessions.computeIfAbsent(player.getUniqueId(),
                k -> new GuiSession(0, SortMode.DATE));
        session.page = 0;
        buildAndOpen(player, session);
    }

    private void buildAndOpen(Player player, GuiSession session) {
        List<MobRecord> sorted = getSorted(session.sortMode, player.getLocation());
        session.records = sorted;
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (int) Math.ceil(sorted.size() / (double) itemsPerPage));
        session.page = Math.min(session.page, totalPages - 1);

        String titleStr = plugin.getConfig().getString("gui.title",
                "<dark_gray>AnimalGuard 管理パネル");
        Inventory inv = Bukkit.createInventory(null, 54,
                MM.deserialize(titleStr + " <gray>[" + (session.page + 1) + "/" + totalPages + "]"));

        // Fill mob items (slots 0-44)
        int start = session.page * itemsPerPage;
        for (int i = 0; i < itemsPerPage; i++) {
            int idx = start + i;
            if (idx >= sorted.size()) break;
            inv.setItem(i, buildMobItem(sorted.get(idx)));
        }

        // Bottom control bar (row 6: slots 45-53)
        fillControlBar(inv, session, totalPages);

        session.inventory = inv;
        player.openInventory(inv);
    }

    private void fillControlBar(Inventory inv, GuiSession session, int totalPages) {
        // Slot 45: Previous page
        if (session.page > 0) {
            inv.setItem(45, buildControlItem(Material.ARROW,
                    "<yellow>前のページ", "<gray>クリックで前ページへ"));
        } else {
            inv.setItem(45, buildControlItem(Material.GRAY_STAINED_GLASS_PANE,
                    "<gray>前のページ", ""));
        }

        // Slot 49: Sort button
        inv.setItem(49, buildControlItem(Material.COMPASS,
                "<aqua>並び替え: <white>" + session.sortMode.label,
                "<gray>クリックで切り替え"));

        // Slot 53: Next page
        if (session.page < totalPages - 1) {
            inv.setItem(53, buildControlItem(Material.ARROW,
                    "<yellow>次のページ", "<gray>クリックで次ページへ"));
        } else {
            inv.setItem(53, buildControlItem(Material.GRAY_STAINED_GLASS_PANE,
                    "<gray>次のページ", ""));
        }

        // Slot 47: Total count info
        inv.setItem(47, buildControlItem(Material.BOOK,
                "<white>保護Mob数: <gold>" + guardManager.size(),
                "<gray>登録されている保護Mob総数"));

        // Fill remaining bottom slots with glass panes
        for (int s : new int[]{46, 48, 50, 51, 52}) {
            inv.setItem(s, buildPane());
        }
    }

    // ---- Item Builders ----

    private ItemStack buildMobItem(MobRecord record) {
        // Try to find entity for type-specific icon
        Entity entity = Bukkit.getEntity(record.mobUuid());
        Material mat = Material.NAME_TAG;
        if (entity != null) {
            mat = getSpawnEgg(entity.getType());
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // Name
        Component name = MM.deserialize("<yellow>" +
                (record.mobName() != null ? record.mobName() : "名称不明"));
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        // Lore
        List<Component> lore = new ArrayList<>();
        lore.add(plain("<gray>オーナー: <white>" + record.ownerName()));
        lore.add(plain("<gray>UUID: <dark_gray>" + record.mobUuid().toString().substring(0, 8) + "..."));
        lore.add(plain("<gray>保護日時: <white>" + DATE_FMT.format(Instant.ofEpochMilli(record.regDate()))));
        if (entity != null) {
            lore.add(plain("<green>● ロード済み"));
            lore.add(plain("<gray>ワールド: <white>" + entity.getWorld().getName()));
        } else {
            lore.add(plain("<red>● アンロード済み"));
        }
        lore.add(Component.empty());
        lore.add(plain("<red>左クリック: <white>保護解除"));
        lore.add(plain("<aqua>右クリック: <white>テレポート"));
        lore.add(plain("<yellow>中クリック: <white>グロウ切替"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildControlItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(List.of(MM.deserialize(lore).decoration(TextDecoration.ITALIC, false)));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildPane() {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.empty());
        pane.setItemMeta(meta);
        return pane;
    }

    private Component plain(String miniMsg) {
        return MM.deserialize(miniMsg).decoration(TextDecoration.ITALIC, false);
    }

    // ---- Click Handler ----

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory == null) return;
        if (!event.getInventory().equals(session.inventory)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // Control row (45-53)
        if (slot >= 45) {
            handleControlClick(player, session, slot);
            return;
        }

        // Mob item slots (0-44)
        int idx = session.page * 45 + slot;
        if (idx >= session.records.size()) return;

        MobRecord record = session.records.get(idx);

        switch (event.getClick()) {
            case LEFT, SHIFT_LEFT -> handleDelete(player, session, record);
            case RIGHT, SHIFT_RIGHT -> handleTeleport(player, record);
            case MIDDLE -> handleGlow(player, record);
            default -> {}
        }
    }

    private void handleControlClick(Player player, GuiSession session, int slot) {
        if (slot == 45 && session.page > 0) {
            session.page--;
            buildAndOpen(player, session);
        } else if (slot == 53) {
            int totalPages = Math.max(1, (int) Math.ceil((double) session.records.size() / 45));
            if (session.page < totalPages - 1) {
                session.page++;
                buildAndOpen(player, session);
            }
        } else if (slot == 49) {
            session.sortMode = session.sortMode.next();
            buildAndOpen(player, session);
        }
    }

    private void handleDelete(Player player, GuiSession session, MobRecord record) {
        guardManager.remove(record.mobUuid());
        plugin.getServer().getAsyncScheduler().runNow(plugin,
                t -> plugin.getDatabaseManager().deleteMob(record.mobUuid()));
        player.sendMessage(plugin.parseMiniMessage(plugin.buildMessage("remove-success")));
        // Refresh GUI
        buildAndOpen(player, session);
    }

    private void handleTeleport(Player player, MobRecord record) {
        player.closeInventory();
        sessions.remove(player.getUniqueId());

        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            Entity entity = Bukkit.getEntity(record.mobUuid());
            if (entity == null) {
                player.getScheduler().run(plugin, st ->
                        player.sendMessage(plugin.parseMiniMessage(
                                plugin.buildMessage("entity-not-found"))), null);
                return;
            }
            // Schedule on entity's region to safely get location
            entity.getScheduler().run(plugin, et -> {
                Location loc = entity.getLocation();
                player.teleportAsync(loc).thenAccept(success -> {
                    if (success) {
                        player.getScheduler().run(plugin, st ->
                                player.sendMessage(plugin.parseMiniMessage(
                                        plugin.buildMessage("teleport"))), null);
                    }
                });
            }, null);
        });
    }

    private void handleGlow(Player player, MobRecord record) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, t -> {
            Entity entity = Bukkit.getEntity(record.mobUuid());
            if (entity == null) {
                player.getScheduler().run(plugin, st ->
                        player.sendMessage(plugin.parseMiniMessage(
                                plugin.buildMessage("entity-not-found"))), null);
                return;
            }
            entity.getScheduler().run(plugin, et -> {
                entity.setGlowing(!entity.isGlowing());
                boolean glow = entity.isGlowing();
                player.getScheduler().run(plugin, st ->
                        player.sendMessage(plugin.parseMiniMessage(
                                "<gray>グロウ: " + (glow ? "<green>ON" : "<red>OFF"))), null);
            }, null);
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session != null && event.getInventory().equals(session.inventory)) {
            sessions.remove(player.getUniqueId());
        }
    }

    // ---- Sorting ----

    private List<MobRecord> getSorted(SortMode mode, Location playerLoc) {
        List<MobRecord> list = new ArrayList<>(guardManager.getAll());
        return switch (mode) {
            case NAME -> list.stream()
                    .sorted(Comparator.comparing(r -> r.mobName() != null ? r.mobName() : ""))
                    .collect(Collectors.toList());
            case DATE -> list.stream()
                    .sorted(Comparator.comparingLong(MobRecord::regDate).reversed())
                    .collect(Collectors.toList());
            case OWNER -> list.stream()
                    .sorted(Comparator.comparing(r -> r.ownerName() != null ? r.ownerName() : ""))
                    .collect(Collectors.toList());
        };
    }

    // ---- Helpers ----

    private Material getSpawnEgg(org.bukkit.entity.EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException e) {
            return Material.NAME_TAG;
        }
    }

    // ---- Inner session class ----

    private static class GuiSession {
        int page;
        SortMode sortMode;
        Inventory inventory;
        List<MobRecord> records = new ArrayList<>();

        GuiSession(int page, SortMode sortMode) {
            this.page = page;
            this.sortMode = sortMode;
        }
    }
}
