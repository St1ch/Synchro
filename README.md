# Synchro

![Minecraft](https://img.shields.io/badge/Minecraft-1.18--1.21.x-67D08B?style=for-the-badge)
![Platforms](https://img.shields.io/badge/Platforms-Bukkit%20%7C%20Spigot%20%7C%20Paper%20%7C%20Folia-6AD3FF?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17-FFD166?style=for-the-badge)
![Version](https://img.shields.io/badge/Version-1.7.0-8FF7D2?style=for-the-badge)
![Config](https://img.shields.io/badge/Config-YAML-E6F0FF?style=for-the-badge&color=3A4D66)
![License](https://img.shields.io/badge/License-MIT-C7A4FF?style=for-the-badge)

Synchro turns multiplayer survival into one shared player state. Inventories, armor, health, hunger, effects, XP, advancements, ender chests, fire state, sleep, and death can all be synchronized so a group plays as one combined character.

If you want a co-op challenge where every action matters for everyone, Synchro is built for that exact server style.

## Highlights

- Shared inventory, armor, ender chest, advancements, health, hunger, saturation, XP, and potion effects
- One-player sleep for skipping night without requiring the whole group to enter beds
- Synchronized fire ticks, death, respawn handling, and post-death inventory state
- Race-condition protection with versioned shared state and delayed-apply guards
- Safer inventory handling for crafting, furnace extraction, hoppers, pickups, drops, buckets, vehicles, item frames, and `/give`
- Per-module `config.yml` toggles with `/synchro reload`
- Admin tools for force sync, restore, debug logging, status, and version checks
- Optional update notifications from Modrinth and GitHub for players with admin permission
- Folia-aware scheduling paths with Bukkit/Paper fallback

## Commands

- `/synchro enable`
- `/synchro disable`
- `/synchro sync [module]`
- `/synchro status`
- `/synchro reload`
- `/synchro restore`
- `/synchro debug <on|off>`
- `/synchro version`

Supported sync modules are `all`, `inventory`, `armor`, `effects`, `potion-effects`, `health`, `hunger`, `experience`, `xp`, `ender-chest`, and `fire-ticks`.

## Permissions

- `synchro.admin` - Use Synchro admin commands and receive update notifications

## Configuration

Every module is enabled by default. Set a module to `false` in `config.yml` and run `/synchro reload` to disable only that part of synchronization.

```yaml
sync:
  inventory: true
  armor: true
  potion-effects: true
  health: true
  hunger: true
  experience: true
  death: true
  ender-chest: true
  advancements: true
  one-player-sleep: true
  fire-ticks: true

debug:
  enabled: false

update-checker:
  enabled: true
  check-interval-minutes: 60
  notify-permission: synchro.admin
  modrinth-project: "I0rM9fEI"
  github-repo: "St1ch/Synchro"
```

## Typical Workflow

1. Install Synchro and start the server once to generate `config.yml`.
2. Join with two or more players and run `/synchro status`.
3. Use `/synchro sync` to initialize the shared state if needed.
4. Adjust module toggles in `config.yml` for the type of co-op challenge you want.
5. Run `/synchro reload` after config changes.
6. Use `/synchro debug on` while testing edge cases, then turn it off for normal play.

## Update Notifications

When a newer version is found, players with `synchro.admin` are notified when they join. Modrinth checks use project `I0rM9fEI`, and GitHub release checks use `St1ch/Synchro`.

The checker is cached by `update-checker.check-interval-minutes`, so it does not call remote APIs on every join.

## API Usage

Other plugins can use Synchro's API:

```java
import org.redstone.SynchroAPI;

if (SynchroAPI.isSyncEnabled()) {
    // Do something.
}
SynchroAPI.setSyncEnabled(false);
SynchroAPI.manualSync();
```

## Development

Build the plugin with:

```bash
.\gradlew.bat build
```

The built jar will be available in `build/libs`.

## Compatibility

- Minecraft: `1.18` to `1.21.x`
- Cores: Bukkit, Spigot, Paper, Folia
- Java: `17`

## Notes

- Synchro is designed around shared survival state, not per-player independent inventories.
- The update checker uses Modrinth and GitHub public APIs and only notifies players with the configured permission.
- Some complex inventory behavior depends on server tick timing; debug logging can help diagnose unusual plugin-stack interactions.

## License

This project is distributed under the [MIT License](LICENSE).
