package org.luminaramc.lumitags.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.luminaramc.lumitags.LumiTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LumiTagCommand implements CommandExecutor, TabCompleter {

    private final LumiTags plugin;

    public LumiTagCommand(LumiTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String usePerm = plugin.getConfig().getString("permissions.use", "lumitags.use");
        String modPerm = plugin.getConfig().getString("permissions.moderator", "group.moderator");
        String adminPerm = plugin.getConfig().getString("permissions.admin", "lumitags.admin");

        if (args.length == 0) {
            sendUsage(sender, modPerm);
            return true;
        }

        // Subcommand: reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(adminPerm)) {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            plugin.reloadPluginConfig();
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.reload-success")));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            String givePerm = plugin.getConfig().getString("permissions.give", "lumitags.give");
            if (!sender.hasPermission(givePerm)) {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("Usage: /lumitags give <player> <preset or prefix>");
                return true;
            }
            Player recipient = Bukkit.getPlayerExact(args[1]);
            if (recipient == null) {
                sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.player-not-found")
                        .replace("%player%", args[1])));
                return true;
            }
            String prefix = joinArgs(args, 2);
            var item = plugin.getPrefixItems().create(prefix);
            if (recipient.getInventory().firstEmpty() == -1) {
                sender.sendMessage("The player's inventory is full.");
                return true;
            }
            recipient.getInventory().addItem(item);
            sender.sendMessage("Gave a prefix item to " + recipient.getName() + ".");
            return true;
        }

        if (args[0].equalsIgnoreCase("claim")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This subcommand can only be executed by a player.");
                return true;
            }
            try {
                plugin.getTagItemStore().deliver(player.getUniqueId());
                player.sendMessage("Collected available returned tag items.");
            } catch (java.io.IOException error) {
                player.sendMessage("Could not collect returned items. Please try again.");
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not collect tag items", error);
            }
            return true;
        }

        // Removing your own tag never requires custom-tag permission.
        if (isUnset(args[0])) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This subcommand can only be executed by a player.");
                return true;
            }
            String type = args.length == 1 ? "prefix" : args[1].toLowerCase(Locale.ROOT);
            if (args.length > 2 || !isTagType(type)) {
                sender.sendMessage("Usage: /lumitags unset [prefix|suffix]");
                return true;
            }
            plugin.getLuckPermsManager().removeLumiTag(player.getUniqueId(), type, () -> {
                player.sendMessage("Your " + type + " has been removed. Any applied item has been returned.");
                runConsoleCommands(false, player, "");
            });
            return true;
        }

        // Subcommand: set <rank>
        if (args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This subcommand can only be executed by a player.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(usePerm)) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }

            if (args.length < 2) {
                sendUsage(sender, modPerm);
                return true;
            }

            // Join arguments to support spaces in rank nickname
            String rankName = joinArgs(args, 1);
            plugin.getLuckPermsManager().setLumiTag(player.getUniqueId(), rankName, () -> {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.rank-changed-self")));
                runConsoleCommands(true, player, rankName);
            });
            return true;
        }

        // Otherwise, assume target player + rank nick (moderator command)
        if (!sender.hasPermission(modPerm)) {
            sender.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission-other")));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            String notFound = plugin.getConfig().getString("messages.player-not-found", "&cPlayer '%player%' is not online.")
                    .replace("%player%", args[0]);
            sender.sendMessage(plugin.colorize(notFound));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender, modPerm);
            return true;
        }

        // Handle off for target player
        if (isUnset(args[1])) {
            String type = args.length == 2 ? "prefix" : args[2].toLowerCase(Locale.ROOT);
            if (args.length > 3 || !isTagType(type)) {
                sender.sendMessage("Usage: /lumitags <player> unset [prefix|suffix]");
                return true;
            }
            plugin.getLuckPermsManager().removeLumiTag(target.getUniqueId(), type, () -> {
                String removedOther = plugin.getConfig().getString("messages.rank-removed-other", "&cRanknick removed for %player%.")
                        .replace("%player%", target.getName());
                sender.sendMessage(plugin.colorize(removedOther));
                runConsoleCommands(false, target, "");
            });
            return true;
        }

        // Set rank nick for target player
        String rankName = joinArgs(args, 1);
        plugin.getLuckPermsManager().setLumiTag(target.getUniqueId(), rankName, () -> {
            String changedOther = plugin.getConfig().getString("messages.rank-changed-other", "&a%player%'s Rank successfully changed!")
                    .replace("%player%", target.getName());
            sender.sendMessage(plugin.colorize(changedOther));
            runConsoleCommands(true, target, rankName);
        });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 0) return completions;
        String usePerm = plugin.getConfig().getString("permissions.use", "lumitags.use");
        String modPerm = plugin.getConfig().getString("permissions.moderator", "group.moderator");
        String adminPerm = plugin.getConfig().getString("permissions.admin", "lumitags.admin");

        String givePerm = plugin.getConfig().getString("permissions.give", "lumitags.give");
        if (args[0].equalsIgnoreCase("give") && sender.hasPermission(givePerm)) {
            if (args.length == 2) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        completions.add(player.getName());
                }
            } else if (args.length == 3) {
                addPresetCompletions(completions, args[2]);
            }
            if (args.length > 1) return completions;
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission(givePerm) && "give".startsWith(input)) completions.add("give");
            if (sender.hasPermission(usePerm)) {
                if ("set".startsWith(input)) completions.add("set");
            }
            if (sender instanceof Player) {
                for (String option : List.of("off", "unset", "claim")) {
                    if (option.startsWith(input)) completions.add(option);
                }
            }
            if (sender.hasPermission(adminPerm)) {
                if ("reload".startsWith(input)) completions.add("reload");
            }
            if (sender.hasPermission(modPerm)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);
            if (isUnset(args[0]) && sender instanceof Player) {
                addTagTypeCompletions(completions, input);
            } else if (args[0].equalsIgnoreCase("set") && sender.hasPermission(usePerm)) {
                addPresetCompletions(completions, input);
            } else if (!args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("set")
                    && !isUnset(args[0]) && !args[0].equalsIgnoreCase("claim") && !args[0].equalsIgnoreCase("reload")) {
                // First arg was likely a player name, check if sender is moderator
                if (sender.hasPermission(modPerm)) {
                    if ("off".startsWith(input)) completions.add("off");
                    if ("unset".startsWith(input)) completions.add("unset");
                    addPresetCompletions(completions, input);
                }
            }
        } else if (args.length == 3 && isUnset(args[1]) && sender.hasPermission(modPerm)
                && !List.of("give", "set", "off", "unset", "claim", "reload").contains(args[0].toLowerCase(Locale.ROOT))) {
            addTagTypeCompletions(completions, args[2]);
        }

        return completions;
    }

    private boolean isUnset(String value) {
        return value.equalsIgnoreCase("off") || value.equalsIgnoreCase("unset");
    }

    private boolean isTagType(String value) {
        return value.equals("prefix") || value.equals("suffix");
    }

    private void addTagTypeCompletions(List<String> completions, String input) {
        for (String type : List.of("prefix", "suffix")) {
            if (type.startsWith(input.toLowerCase(Locale.ROOT))) completions.add(type);
        }
    }

    private void addPresetCompletions(List<String> completions, String input) {
        var presets = plugin.getConfig().getConfigurationSection("presets");
        if (presets == null) return;
        String partial = input.toLowerCase(Locale.ROOT);
        presets.getKeys(false).stream()
                .filter(preset -> preset.toLowerCase(Locale.ROOT).startsWith(partial))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(completions::add);
    }

    private void sendUsage(CommandSender sender, String modPerm) {
        String msgKey = sender.hasPermission(modPerm) ? "messages.invalid-format-moderator" : "messages.invalid-format";
        String usage = plugin.getConfig().getString(msgKey);
        sender.sendMessage(plugin.colorize(usage));
        sender.sendMessage("/lumitags unset [prefix|suffix]\n/lumitags claim");
        if (sender.hasPermission(plugin.getConfig().getString("permissions.give", "lumitags.give")))
            sender.sendMessage("/lumitags give <player> <preset or prefix>");
    }

    private String joinArgs(String[] args, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void runConsoleCommands(boolean isSet, Player player, String rank) {
        String listKey = isSet ? "commands.on-set" : "commands.on-off";
        List<String> cmdList = plugin.getConfig().getStringList(listKey);
        for (String cmd : cmdList) {
            String formattedCmd = cmd.replace("%player%", player.getName())
                                     .replace("%rank%", rank != null ? rank : "");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), formattedCmd);
        }
    }
}
