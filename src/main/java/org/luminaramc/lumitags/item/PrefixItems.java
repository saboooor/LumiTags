package org.luminaramc.lumitags.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.luminaramc.lumitags.LumiTags;

import java.util.List;
import java.util.Locale;

public final class PrefixItems implements Listener {
    private final java.util.Set<java.util.UUID> pending = new java.util.HashSet<>();
    private final LumiTags plugin;
    private final NamespacedKey prefixKey;
    private final NamespacedKey typeKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    public PrefixItems(LumiTags plugin) {
        this.plugin = plugin;
        prefixKey = new NamespacedKey(plugin, "prefix");
        typeKey = new NamespacedKey(plugin, "tag_type");
    }

    public ItemStack create(String input) {
        String id = input.toLowerCase(Locale.ROOT);
        String suffixPreset = plugin.getConfig().getString("presets." + id + ".suffix");
        boolean suffix = suffixPreset != null;
        String type = suffix ? "suffix" : "prefix";
        String preset = suffix ? suffixPreset : plugin.getConfig().getString("presets." + id + ".prefix");
        String formatted = preset != null ? preset : plugin.applyTransformations(input);
        // Legacy text is kept for existing custom prefixes; presets use MiniMessage.
        Component display = preset != null || formatted.contains("<")
                ? miniMessage.deserialize(formatted) : legacy.deserialize(plugin.colorize(formatted));
        Material material = preset == null ? Material.NAME_TAG
                : Material.matchMaterial(plugin.getConfig().getString("presets." + id + ".material", "NAME_TAG"));
        ItemStack item = new ItemStack(material == null || !material.isItem() || material.isAir() ? Material.NAME_TAG : material);
        var meta = item.getItemMeta();
        meta.displayName(display);
        meta.lore(List.of(Component.text("Right-click to apply this " + type + "."),
                Component.text("Replaces your current " + type + ". Returned when replaced or unset."),
                Component.text("You can trade this item before applying it.")));
        meta.getPersistentDataContainer().set(prefixKey, PersistentDataType.STRING, formatted);
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(prefixKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Right-click the air or a block to apply this tag.");
        }
    }

    @EventHandler
    public void onApply(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || event.getHand() == null) return;
        String prefix = item.getItemMeta().getPersistentDataContainer().get(prefixKey, PersistentDataType.STRING);
        if (prefix == null) return;
        String storedType = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        boolean suffix = "suffix".equals(storedType);
        // Recognize emoji items issued before item types were introduced.
        if (storedType == null) {
            var presets = plugin.getConfig().getConfigurationSection("presets");
            if (presets != null) for (String id : presets.getKeys(false)) {
                if (prefix.equals(presets.getString(id + ".suffix"))) suffix = true;
            }
        }
        String type = suffix ? "suffix" : "prefix";
        event.setCancelled(true);
        var player = event.getPlayer();
        if (!pending.add(player.getUniqueId())) return;
        ItemStack consumed = item.clone();
        consumed.setAmount(1);
        // Reserve the item before the asynchronous save to prevent repeated redemption.
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(event.getHand(), item);
        var save = plugin.getLuckPermsManager().applyItem(player.getUniqueId(), suffix, prefix, consumed);
        save.whenComplete((ignored, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    pending.remove(player.getUniqueId());
                    if (error != null) {
                        try { plugin.getTagItemStore().refund(player.getUniqueId(), consumed); }
                        catch (java.io.IOException refundError) {
                            player.getInventory().addItem(consumed).values().forEach(leftover ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not persist returned item", refundError);
                        }
                        player.sendMessage("Could not apply your " + type + ". Your item has been returned.");
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to apply " + type + " for " + player.getUniqueId(), error);
                        return;
                    }
                    player.sendMessage("Your " + type + " has been applied.");
                    for (String command : plugin.getConfig().getStringList("commands.on-set")) {
                        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(),
                                command.replace("%player%", player.getName()).replace("%rank%", prefix));
                    }
                }));
    }
}
