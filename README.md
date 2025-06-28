# Synchro

A Minecraft plugin that synchronizes **inventory, armor, potion effects, health, and hunger** between all players in survival mode. Includes admin commands and a public Java API for integration with other plugins.

## Features

- **Inventory Synchronization**: All players share the same inventory in real time
- **Armor Synchronization**: All players wear the same armor
- **Potion Effects Synchronization**: All active potion effects are shared between players
- **Health Synchronization**: Players' health is synchronized
- **Hunger Synchronization**: Players' hunger and saturation levels are synchronized
- **Death Synchronization**: When one player dies, all players die together
- **Respawn Handling**: Proper synchronization after player respawn
- **Admin Commands**: Enable/disable sync, force sync, check status
- **Public Java API**: Other plugins can control and check sync status programmatically

## Requirements

- Minecraft 1.21
- Paper/Spigot server
- Java 21 or higher

## Installation

1. Download the latest version from Modrinth
2. Place the .jar file in your server's `plugins` folder
3. Restart your server or use a plugin manager to load it

## Usage

The plugin works automatically after installation. No configuration needed!

- All players' inventories, armor, and potion effects will be synchronized
- Health and hunger levels will be shared between all players
- When one player dies, all players will die together
- After respawn, all players will be properly synchronized

### Admin Commands

- `/synchro enable` — Enable synchronization
- `/synchro disable` — Disable synchronization
- `/synchro sync` — Force manual synchronization
- `/synchro status` — Show current sync status

### API Usage

Other plugins can use Synchro's API:

```java
import org.redstone.SynchroAPI;

if (SynchroAPI.isSyncEnabled()) {
    // Do something
}
SynchroAPI.setSyncEnabled(false);
SynchroAPI.manualSync();
```
Just add Synchro as a dependency in your build system.

## Support

If you encounter any issues or have suggestions, please create an issue on the Modrinth page.

## License

This project is licensed under the MIT License - see the LICENSE file for details. 