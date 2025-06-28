package org.redstone;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SynchroCommand implements CommandExecutor {
    private final SynhroPlugin plugin;

    public SynchroCommand(SynhroPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("synchro.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /synchro <enable|disable|sync|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "enable":
                plugin.setSyncEnabled(true);
                sender.sendMessage("§aSynchronization enabled.");
                break;
            case "disable":
                plugin.setSyncEnabled(false);
                sender.sendMessage("§cSynchronization disabled.");
                break;
            case "sync":
                plugin.manualSync();
                sender.sendMessage("§aManual synchronization triggered.");
                break;
            case "status":
                sender.sendMessage("§eSynchronization is " + (plugin.isSyncEnabled() ? "§aENABLED" : "§cDISABLED"));
                break;
            default:
                sender.sendMessage("§eUsage: /synchro <enable|disable|sync|status>");
        }
        return true;
    }
} 