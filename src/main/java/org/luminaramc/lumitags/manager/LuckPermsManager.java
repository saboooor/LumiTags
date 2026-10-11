package org.luminaramc.lumitags.manager;

import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.luminaramc.lumitags.LumiTags;

import java.util.UUID;

public class LuckPermsManager {

    private final LumiTags plugin;

    public LuckPermsManager(LumiTags plugin) {
        this.plugin = plugin;
    }

    /**
     * Sets a player's rank nickname as a prefix node in LuckPerms asynchronously.
     *
     * @param uuid      The target player's UUID.
     * @param rankName  The rank prefix to set.
     * @param onSuccess Callback to run on the Bukkit main thread after saving is complete.
     */
    public void setLumiTag(UUID uuid, String rankName, Runnable onSuccess) {
        setFormattedLumiTag(uuid, plugin.applyTransformations(rankName)).thenRun(() -> {
            if (onSuccess != null) Bukkit.getScheduler().runTask(plugin, onSuccess);
        });
    }

    public java.util.concurrent.CompletableFuture<Void> setFormattedLumiTag(UUID uuid, String prefix) {
        int priority = plugin.getConfig().getInt("prefix-weight", 500);
        return plugin.getLuckPerms().getUserManager().loadUser(uuid).thenCompose(user -> {
            user.data().clear(NodeType.PREFIX.predicate(node -> node.getPriority() == priority));
            user.data().add(PrefixNode.builder(prefix, priority).build());
            return plugin.getLuckPerms().getUserManager().saveUser(user);
        });
    }

    public java.util.concurrent.CompletableFuture<Void> setFormattedSuffix(UUID uuid, String suffix) {
        int priority = plugin.getConfig().getInt("suffix-weight", 500);
        return plugin.getLuckPerms().getUserManager().loadUser(uuid).thenCompose(user -> {
            user.data().clear(NodeType.SUFFIX.predicate(node -> node.getPriority() == priority));
            user.data().add(SuffixNode.builder(suffix, priority).build());
            return plugin.getLuckPerms().getUserManager().saveUser(user);
        });
    }

    /**
     * Removes the rank nickname prefix from LuckPerms asynchronously.
     *
     * @param uuid      The target player's UUID.
     * @param onSuccess Callback to run on the Bukkit main thread after saving is complete.
     */
    public void removeLumiTag(UUID uuid, Runnable onSuccess) {
        int priority = plugin.getConfig().getInt("prefix-weight", 500);

        plugin.getLuckPerms().getUserManager().loadUser(uuid).thenAcceptAsync(user -> {
            // Remove any existing prefix at our priority level
            user.data().clear(NodeType.PREFIX.predicate(node -> node.getPriority() == priority));

            // Save the user data and fire success callback on the main server thread
            plugin.getLuckPerms().getUserManager().saveUser(user).thenRun(() -> {
                if (onSuccess != null) {
                    Bukkit.getScheduler().runTask(plugin, onSuccess);
                }
            });
        });
    }
}
