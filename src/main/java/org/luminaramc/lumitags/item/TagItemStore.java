package org.luminaramc.lumitags.item;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.luminaramc.lumitags.LumiTags;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Accessed only on the server thread. Stores exact items, independently for each tag type. */
public final class TagItemStore implements Listener {
    private final File file;
    private final YamlConfiguration data;

    public TagItemStore(LumiTags plugin) {
        this(new File(plugin.getDataFolder(), "tag-items.yml"));
    }

    TagItemStore(File file) {
        this.file = file;
        data = new YamlConfiguration();
        if (file.exists()) {
            try { data.load(file); }
            catch (Exception error) { throw new IllegalStateException("Could not read tag item ownership", error); }
        }
    }

    private String active(UUID uuid, String type) { return "players." + uuid + ".active." + type; }
    private String refunds(UUID uuid) { return "players." + uuid + ".refunds"; }

    public void replace(UUID uuid, String type, ItemStack replacement) throws IOException {
        String snapshot = data.saveToString();
        ItemStack previous = data.getItemStack(active(uuid, type));
        List<ItemStack> queue = queue(uuid);
        if (previous != null) queue.add(previous.clone());
        data.set(active(uuid, type), replacement == null ? null : replacement.clone());
        data.set(refunds(uuid), queue);
        try {
            save();
        } catch (IOException error) {
            try { data.loadFromString(snapshot); } catch (Exception restoreError) { error.addSuppressed(restoreError); }
            throw error;
        }
        try { deliver(uuid); }
        catch (IOException error) { Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Could not deliver returned tag items", error); }
    }

    public void refund(UUID uuid, ItemStack item) throws IOException {
        String snapshot = data.saveToString();
        List<ItemStack> queue = queue(uuid);
        queue.add(item.clone());
        data.set(refunds(uuid), queue);
        try { save(); }
        catch (IOException error) {
            try { data.loadFromString(snapshot); } catch (Exception restoreError) { error.addSuppressed(restoreError); }
            throw error;
        }
        try { deliver(uuid); }
        catch (IOException error) { Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Could not deliver returned tag items", error); }
    }

    private List<ItemStack> queue(UUID uuid) {
        List<ItemStack> items = new ArrayList<>();
        for (Object value : data.getList(refunds(uuid), List.of())) {
            if (value instanceof ItemStack item) items.add(item.clone());
        }
        return items;
    }

    public void deliver(UUID uuid) throws IOException {
        var player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        String snapshot = data.saveToString();
        ItemStack[] inventory = java.util.Arrays.stream(player.getInventory().getStorageContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : queue(uuid)) remaining.addAll(player.getInventory().addItem(item).values());
        data.set(refunds(uuid), remaining);
        try { save(); }
        catch (IOException error) {
            player.getInventory().setStorageContents(inventory);
            try { data.loadFromString(snapshot); } catch (Exception restoreError) { error.addSuppressed(restoreError); }
            throw error;
        }
        if (!remaining.isEmpty()) player.sendMessage("Make inventory space and use /lumitags claim to collect returned tag items.");
    }

    private void save() throws IOException {
        Files.createDirectories(file.toPath().getParent());
        var temporary = file.toPath().resolveSibling(file.getName() + ".tmp");
        data.save(temporary.toFile());
        try {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException error) {
            Files.move(temporary, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try { deliver(event.getPlayer().getUniqueId()); }
        catch (IOException error) { Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Could not deliver returned tag items", error); }
    }
}
