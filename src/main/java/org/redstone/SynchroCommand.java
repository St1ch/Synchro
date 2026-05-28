package org.redstone;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SynchroCommand implements TabExecutor {
    private static final List<String> COMMANDS = Arrays.asList(
        "enable", "disable", "sync", "status", "reload", "restore", "debug", "version"
    );
    private static final List<String> MODULES = Arrays.asList(
        "all", "inventory", "armor", "effects", "potion-effects", "health", "hunger", "experience", "xp",
        "ender-chest", "fire-ticks"
    );
    private static final List<String> DEBUG_STATES = Arrays.asList("on", "off");

    private final SynhroPlugin plugin;

    public SynchroCommand(SynhroPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("synchro.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable":
                plugin.setSyncEnabled(true);
                sender.sendMessage(ChatColor.GREEN + "Synchronization enabled.");
                break;
            case "disable":
                plugin.setSyncEnabled(false);
                sender.sendMessage(ChatColor.RED + "Synchronization disabled.");
                break;
            case "sync": {
                String module = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "all";
                if (!plugin.manualSync(module)) {
                    sender.sendMessage(ChatColor.RED + "Unknown sync module: " + module);
                    sender.sendMessage(ChatColor.YELLOW + "Modules: " + String.join(", ", MODULES));
                    break;
                }
                sender.sendMessage(ChatColor.GREEN + "Manual synchronization triggered for " + module + ".");
                break;
            }
            case "status":
                sender.sendMessage(ChatColor.YELLOW + "Synchronization is "
                    + (plugin.isSyncEnabled() ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED"));
                sender.sendMessage(ChatColor.YELLOW + "Debug logging is "
                    + (plugin.isDebugLogging() ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                break;
            case "reload":
                plugin.reloadSettings();
                sender.sendMessage(ChatColor.GREEN + "Synchro configuration reloaded.");
                break;
            case "restore":
                plugin.restoreAllPlayerData();
                sender.sendMessage(ChatColor.GREEN + "Original player states restored for online players.");
                break;
            case "debug":
                if (args.length < 2 || !DEBUG_STATES.contains(args[1].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(ChatColor.YELLOW + "Usage: /synchro debug <on|off>");
                    break;
                }
                boolean enabled = args[1].equalsIgnoreCase("on");
                plugin.setDebugLogging(enabled);
                sender.sendMessage(ChatColor.GREEN + "Debug logging " + (enabled ? "enabled." : "disabled."));
                break;
            case "version":
                sender.sendMessage(ChatColor.YELLOW + "Synchro version " + ChatColor.GREEN + plugin.getPluginVersion());
                break;
            default:
                sendUsage(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("synchro.admin")) return List.of();
        if (args.length == 1) return matching(COMMANDS, args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("sync")) return matching(MODULES, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) return matching(DEBUG_STATES, args[1]);
        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW
            + "Usage: /synchro <enable|disable|sync|status|reload|restore|debug|version>");
    }

    private List<String> matching(List<String> values, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(lowerPrefix)) {
                result.add(value);
            }
        }
        return result;
    }
}
