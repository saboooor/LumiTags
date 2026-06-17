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

        // Subcommand: off (self)
        if (args[0].equalsIgnoreCase("off")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This subcommand can only be executed by a player.");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(usePerm)) {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.no-permission")));
                return true;
            }

            plugin.getLuckPermsManager().removeLumiTag(player.getUniqueId(), () -> {
                player.sendMessage(plugin.colorize(plugin.getConfig().getString("messages.rank-removed-self")));
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
        if (args[1].equalsIgnoreCase("off")) {
            plugin.getLuckPermsManager().removeLumiTag(target.getUniqueId(), () -> {
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
        String usePerm = plugin.getConfig().getString("permissions.use", "lumitags.use");
        String modPerm = plugin.getConfig().getString("permissions.moderator", "group.moderator");
        String adminPerm = plugin.getConfig().getString("permissions.admin", "lumitags.admin");

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            if (sender.hasPermission(usePerm)) {
                if ("set".startsWith(input)) completions.add("set");
                if ("off".startsWith(input)) completions.add("off");
            }
            if (sender.hasPermission(adminPerm)) {
                if ("reload".startsWith(input)) completions.add("reload");
            }
            if (sender.hasPermission(modPerm)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        completions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 2) {
            String input = args[1].toLowerCase();
            if (!args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("off") && !args[0].equalsIgnoreCase("reload")) {
                // First arg was likely a player name, check if sender is moderator
                if (sender.hasPermission(modPerm)) {
                    if ("off".startsWith(input)) completions.add("off");
                }
            }
        }

        return completions;
    }

    private void sendUsage(CommandSender sender, String modPerm) {
        String msgKey = sender.hasPermission(modPerm) ? "messages.invalid-format-moderator" : "messages.invalid-format";
        String usage = plugin.getConfig().getString(msgKey);
        sender.sendMessage(plugin.colorize(usage));
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
