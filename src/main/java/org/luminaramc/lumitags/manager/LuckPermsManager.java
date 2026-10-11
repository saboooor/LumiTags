package org.luminaramc.lumitags.manager;

import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.luminaramc.lumitags.LumiTags;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public class LuckPermsManager {
    private final LumiTags plugin;
    private final Set<UUID> pending = new HashSet<>();

    public LuckPermsManager(LumiTags plugin) { this.plugin = plugin; }

    public void setLumiTag(UUID uuid, String rankName, Runnable onSuccess) {
        String path = "presets." + rankName.toLowerCase(java.util.Locale.ROOT);
        String suffix = plugin.getConfig().getString(path + ".suffix");
        String prefix = plugin.getConfig().getString(path + ".prefix");
        var save = suffix != null ? setFormattedSuffix(uuid, suffix)
                : setFormattedLumiTag(uuid, prefix != null ? prefix : plugin.applyTransformations(rankName));
        finishCommand(uuid, save, onSuccess);
    }

    public CompletableFuture<Void> setFormattedLumiTag(UUID uuid, String prefix) {
        return change(uuid, false, prefix, null);
    }

    public CompletableFuture<Void> setFormattedSuffix(UUID uuid, String suffix) {
        return change(uuid, true, suffix, null);
    }

    public CompletableFuture<Void> applyItem(UUID uuid, boolean suffix, String text, ItemStack item) {
        return change(uuid, suffix, text, item);
    }

    public void removeLumiTag(UUID uuid, Runnable onSuccess) {
        removeLumiTag(uuid, "prefix", onSuccess);
    }

    public void removeLumiTag(UUID uuid, String type, Runnable onSuccess) {
        finishCommand(uuid, change(uuid, type.equals("suffix"), null, null), onSuccess);
    }

    private void finishCommand(UUID uuid, CompletableFuture<Void> save, Runnable onSuccess) {
        save.whenComplete((ignored, error) -> {
            if (error == null) {
                if (onSuccess != null) onSuccess.run();
            } else {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not change LumiTag for " + uuid, error);
                var player = Bukkit.getPlayer(uuid);
                if (player != null) player.sendMessage("Could not change your tag. Please try again.");
            }
        });
    }

    /** All entry points run on the server thread; the lock covers commands and items alike. */
    private CompletableFuture<Void> change(UUID uuid, boolean suffix, String text, ItemStack sourceItem) {
        if (!pending.add(uuid)) return CompletableFuture.failedFuture(new IllegalStateException("A tag change is already pending"));
        int priority = plugin.getConfig().getInt(suffix ? "suffix-weight" : "prefix-weight", 500);
        String type = suffix ? "suffix" : "prefix";
        ItemStack replacement = sourceItem == null ? null : sourceItem.clone();
        if (replacement != null) replacement.setAmount(1);
        Predicate<Node> matches = suffix
                ? NodeType.SUFFIX.predicate(node -> node.getPriority() == priority)
                : NodeType.PREFIX.predicate(node -> node.getPriority() == priority);
        CompletableFuture<Void> result = new CompletableFuture<>();
        plugin.getLuckPerms().getUserManager().loadUser(uuid).thenCompose(user -> {
            var oldNodes = user.data().toCollection().stream().filter(matches).toList();
            user.data().clear(matches);
            if (text != null) user.data().add(suffix ? SuffixNode.builder(text, priority).build() : PrefixNode.builder(text, priority).build());
            return plugin.getLuckPerms().getUserManager().saveUser(user).handle((ignored, saveError) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Throwable error = saveError;
                    if (error == null) {
                        try { plugin.getTagItemStore().replace(uuid, type, replacement); }
                        catch (Exception storeError) { error = storeError; }
                    }
                    if (error == null) {
                        pending.remove(uuid);
                        result.complete(null);
                    } else {
                        Throwable failure = error;
                        user.data().clear(matches);
                        oldNodes.forEach(node -> user.data().add(node));
                        plugin.getLuckPerms().getUserManager().saveUser(user).whenComplete((nothing, rollbackError) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (rollbackError != null) failure.addSuppressed(rollbackError);
                                    pending.remove(uuid);
                                    result.completeExceptionally(failure);
                                }));
                    }
                });
                return null;
            });
        }).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(uuid);
                result.completeExceptionally(error);
            });
            return null;
        });
        return result;
    }
}
