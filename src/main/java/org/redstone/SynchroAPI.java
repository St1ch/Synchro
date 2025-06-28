package org.redstone;

import org.bukkit.entity.Player;

public class SynchroAPI {
    private static SynhroPlugin plugin;

    public static void setPlugin(SynhroPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static boolean isSyncEnabled() {
        return plugin != null && plugin.isSyncEnabled();
    }

    public static void setSyncEnabled(boolean enabled) {
        if (plugin != null) plugin.setSyncEnabled(enabled);
    }

    public static void manualSync() {
        if (plugin != null) plugin.manualSync();
    }
} 