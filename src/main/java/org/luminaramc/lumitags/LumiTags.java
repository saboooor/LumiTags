package org.luminaramc.lumitags;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.luminaramc.lumitags.command.LumiTagCommand;
import org.luminaramc.lumitags.manager.LuckPermsManager;

import java.util.logging.Level;

public final class LumiTags extends JavaPlugin {

    private LuckPerms luckPerms;
    private LuckPermsManager luckPermsManager;

    @Override
    public void onEnable() {
        // Save default configuration
        saveDefaultConfig();

        // Check and hook LuckPerms
        if (!setupLuckPerms()) {
            getLogger().log(Level.SEVERE, "LuckPerms was not found or is disabled! Disabling LumiTags plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize manager
        this.luckPermsManager = new LuckPermsManager(this);

        // Register command executor
        if (getCommand("lumitag") != null) {
            LumiTagCommand executor = new LumiTagCommand(this);
            getCommand("lumitag").setExecutor(executor);
            getCommand("lumitag").setTabCompleter(executor);
        } else {
            getLogger().log(Level.WARNING, "Failed to register command /lumitag. Check plugin.yml.");
        }

        getLogger().info("LumiTags plugin successfully enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LumiTags plugin disabled.");
    }

    /**
     * Attempts to find and hook the LuckPerms API provider.
     *
     * @return true if successfully hooked, false otherwise.
     */
    private boolean setupLuckPerms() {
        if (getServer().getPluginManager().getPlugin("LuckPerms") == null) {
            return false;
        }
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            return false;
        }
        this.luckPerms = provider.getProvider();
        return this.luckPerms != null;
    }

    /**
     * Reloads the plugin configuration file and refreshes settings.
     */
    public void reloadPluginConfig() {
        reloadConfig();
    }

    /**
     * Helper to translate alternate color codes in messages.
     *
     * @param message The raw message.
     * @return The colorized message.
     */
    public String colorize(String message) {
        if (message == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Applies configured transformations to the rank name before setting it as a prefix.
     * @param rankName The original rank name.
     * @return The transformed rank name.
     */
    public String applyTransformations(String rankName) {
        String transformedRankName = rankName;

        // Apply transformations that are set by default, for example: bold and smallcaps
        for (String transformation : getConfig().getStringList("transformations")) {
            switch (transformation.toLowerCase()) {
                case "uppercase":
                    transformedRankName = transformedRankName.toUpperCase();
                    break;
                case "lowercase":
                    transformedRankName = transformedRankName.toLowerCase();
                    break;
                case "capitalize":
                    transformedRankName = capitalize(transformedRankName);
                    break;
                case "smallcaps":
                    transformedRankName = toSmallCaps(transformedRankName);
                    break;
                case "bold":
                    transformedRankName = ChatColor.BOLD + transformedRankName;
                    break;
                case "italic":
                    transformedRankName = ChatColor.ITALIC + transformedRankName;
                    break;
                case "underline":
                    transformedRankName = ChatColor.UNDERLINE + transformedRankName;
                    break;
                case "strikethrough":
                    transformedRankName = ChatColor.STRIKETHROUGH + transformedRankName;
                    break;
                default:
                    getLogger().warning("Unknown transformation '" + transformation + "' in config. Skipping.");
            }
        }

        return transformedRankName;
    }

    private String toSmallCaps(String transformedRankName) {
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String smallcaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        StringBuilder sb = new StringBuilder();
        for (char c : transformedRankName.toCharArray()) {
            int index = normal.indexOf(c);
            if (index != -1) {
                sb.append(smallcaps.charAt(index));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String capitalize(String transformedRankName) {
        if (transformedRankName.isEmpty()) {
            return transformedRankName;
        }
        return transformedRankName.substring(0, 1).toUpperCase() + transformedRankName.substring(1);
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public LuckPermsManager getLuckPermsManager() {
        return luckPermsManager;
    }
}
