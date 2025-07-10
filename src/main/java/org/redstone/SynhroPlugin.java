package org.redstone;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.redstone.SynchroAPI;
import org.bukkit.attribute.Attribute;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.List;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;

public class SynhroPlugin extends JavaPlugin implements Listener {
    private Map<UUID, PlayerData> playerDataMap;
    private Map<UUID, Boolean> respawningPlayers;
    private boolean isSynchronizing = false;
    private Map<UUID, ItemStack[]> lastKnownInventory;
    private boolean syncEnabled = true;
    private Map<UUID, ItemStack[]> lastKnownArmor;
    private Map<UUID, Map<PotionEffectType, PotionEffect>> lastKnownEffects;
    private Map<UUID, Double> lastKnownHealth;
    private Map<UUID, Integer> lastKnownHunger;
    private Map<UUID, Float> lastKnownSaturation;
    private Map<UUID, Integer> lastKnownExp;
    private Map<UUID, Integer> lastKnownLevel;

    @Override
    public void onEnable() {
        playerDataMap = new ConcurrentHashMap<>();
        respawningPlayers = new ConcurrentHashMap<>();
        lastKnownInventory = new ConcurrentHashMap<>();
        lastKnownArmor = new ConcurrentHashMap<>();
        lastKnownEffects = new ConcurrentHashMap<>();
        lastKnownHealth = new ConcurrentHashMap<>();
        lastKnownHunger = new ConcurrentHashMap<>();
        lastKnownSaturation = new ConcurrentHashMap<>();
        lastKnownExp = new ConcurrentHashMap<>();
        lastKnownLevel = new ConcurrentHashMap<>();
        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("synchro").setExecutor(new SynchroCommand(this));
        SynchroAPI.setPlugin(this);
    }

    @Override
    public void onDisable() {
        // Восстанавливаем индивидуальное состояние для всех онлайн-игроков
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataMap.get(player.getUniqueId());
            if (data != null) {
                player.getInventory().setContents(cloneInventory(data.inventory));
                player.getInventory().setArmorContents(cloneInventory(data.armor));
                for (PotionEffect effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                for (PotionEffect effect : data.effects.values()) {
                    player.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
                }
                player.setHealth(Math.min(data.health, player.getMaxHealth()));
                player.setFoodLevel(data.foodLevel);
                player.setSaturation(data.saturation);
                player.setTotalExperience(data.exp);
                player.setLevel(data.level);
            }
        }
        playerDataMap.clear();
        respawningPlayers.clear();
        lastKnownInventory.clear();
        lastKnownArmor.clear();
        lastKnownEffects.clear();
        lastKnownHealth.clear();
        lastKnownHunger.clear();
        lastKnownSaturation.clear();
        lastKnownExp.clear();
        lastKnownLevel.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Сохраняем индивидуальное состояние до синхронизации
        playerDataMap.put(player.getUniqueId(), new PlayerData(player));
        respawningPlayers.remove(player.getUniqueId());
        lastKnownInventory.put(player.getUniqueId(), player.getInventory().getContents());
        lastKnownArmor.put(player.getUniqueId(), player.getInventory().getArmorContents());
        lastKnownEffects.put(player.getUniqueId(), getEffectsMap(player));
        lastKnownHealth.put(player.getUniqueId(), player.getHealth());
        lastKnownHunger.put(player.getUniqueId(), player.getFoodLevel());
        lastKnownSaturation.put(player.getUniqueId(), player.getSaturation());
        synchronizeHealth(player);
        synchronizeHunger(player);
        synchronizeExp(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerDataMap.remove(playerId);
        respawningPlayers.remove(playerId);
        lastKnownInventory.remove(playerId);
        lastKnownArmor.remove(playerId);
        lastKnownEffects.remove(playerId);
        lastKnownHealth.remove(playerId);
        lastKnownHunger.remove(playerId);
        lastKnownSaturation.remove(playerId);
        lastKnownExp.remove(playerId);
        lastKnownLevel.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    synchronizeInventory(player);
                }, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (respawningPlayers.containsKey(player.getUniqueId())) return;
        respawningPlayers.put(player.getUniqueId(), true);

        // Собираем всех живых игроков, кроме умершего
        List<Player> toKill = Bukkit.getOnlinePlayers().stream()
            .filter(p -> p != player && p.isOnline() && !p.isDead() && !respawningPlayers.containsKey(p.getUniqueId()))
            .collect(Collectors.toList());

        for (Player target : toKill) {
            respawningPlayers.put(target.getUniqueId(), true);
            target.setHealth(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        respawningPlayers.put(player.getUniqueId(), true);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            respawningPlayers.remove(player.getUniqueId());
            synchronizePlayers();
            synchronizeHealth(player);
            synchronizeHunger(player);
            synchronizeExp(player);
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthChange(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                synchronizeHealth(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                synchronizeHunger(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
                synchronizeArmor(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    synchronizeInventory(player);
                    synchronizeArmor(player);
                }, 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
                synchronizeArmor(player);
                synchronizeHunger(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
                synchronizeArmor(player);
                synchronizeEffects(player);
                synchronizeHealth(player);
                synchronizeHunger(player);
                synchronizeExp(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeInventory(player);
                synchronizeArmor(player);
                synchronizeEffects(player);
                synchronizeHealth(player);
                synchronizeHunger(player);
                synchronizeExp(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    synchronizeEffects(player);
                }, 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    synchronizeHealth(player);
                }, 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeExp(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                synchronizeExp(player);
            }, 1L);
        }
    }

    private void synchronizePlayers() {
        if (!syncEnabled) return;
        if (isSynchronizing) return;
        isSynchronizing = true;
        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!respawningPlayers.containsKey(player.getUniqueId())) {
                    synchronizeInventory(player);
                    synchronizeArmor(player);
                    synchronizeHealth(player);
                    synchronizeHunger(player);
                    synchronizeEffects(player);
                }
            }
        } finally {
            isSynchronizing = false;
        }
    }

    private void synchronizeInventory(Player source) {
        if (source == null || !source.isOnline()) return;
        
        ItemStack[] currentContents = source.getInventory().getContents();
        ItemStack[] lastContents = lastKnownInventory.get(source.getUniqueId());
        
        // Check if inventory actually changed
        if (lastContents != null && areInventoriesEqual(currentContents, lastContents)) {
            return;
        }
        
        // Update last known inventory
        lastKnownInventory.put(source.getUniqueId(), cloneInventory(currentContents));
        
        // Synchronize with other players
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                target.getInventory().setContents(cloneInventory(currentContents));
                lastKnownInventory.put(target.getUniqueId(), cloneInventory(currentContents));
            }
        }
    }

    private boolean areInventoriesEqual(ItemStack[] inv1, ItemStack[] inv2) {
        if (inv1.length != inv2.length) return false;
        
        for (int i = 0; i < inv1.length; i++) {
            ItemStack item1 = inv1[i];
            ItemStack item2 = inv2[i];
            
            if (item1 == null && item2 == null) continue;
            if (item1 == null || item2 == null) return false;
            
            if (!item1.equals(item2)) return false;
        }
        return true;
    }

    private static ItemStack[] cloneInventory(ItemStack[] inventory) {
        ItemStack[] clone = new ItemStack[inventory.length];
        for (int i = 0; i < inventory.length; i++) {
            clone[i] = inventory[i] != null ? inventory[i].clone() : null;
        }
        return clone;
    }

    private void synchronizeHealth(Player source) {
        if (source == null || !source.isOnline() || source.isDead()) return;
        double health = source.getHealth();
        Double last = lastKnownHealth.get(source.getUniqueId());
        if (last != null && last == health) return;
        lastKnownHealth.put(source.getUniqueId(), health);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId()) && !target.isDead()) {
                target.setHealth(health);
                lastKnownHealth.put(target.getUniqueId(), health);
            }
        }
    }

    private void synchronizeHunger(Player source) {
        if (source == null || !source.isOnline()) return;
        
        int foodLevel = source.getFoodLevel();
        float saturation = source.getSaturation();
        Integer lastFood = lastKnownHunger.get(source.getUniqueId());
        Float lastSat = lastKnownSaturation.get(source.getUniqueId());
        if (lastFood != null && lastSat != null && lastFood == foodLevel && lastSat == saturation) return;
        lastKnownHunger.put(source.getUniqueId(), foodLevel);
        lastKnownSaturation.put(source.getUniqueId(), saturation);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                target.setFoodLevel(foodLevel);
                target.setSaturation(saturation);
                lastKnownHunger.put(target.getUniqueId(), foodLevel);
                lastKnownSaturation.put(target.getUniqueId(), saturation);
            }
        }
    }

    private void synchronizeArmor(Player source) {
        if (source == null || !source.isOnline()) return;
        ItemStack[] currentArmor = source.getInventory().getArmorContents();
        ItemStack[] lastArmor = lastKnownArmor.get(source.getUniqueId());
        if (lastArmor != null && areInventoriesEqual(currentArmor, lastArmor)) {
            return;
        }
        lastKnownArmor.put(source.getUniqueId(), cloneInventory(currentArmor));
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                target.getInventory().setArmorContents(cloneInventory(currentArmor));
                lastKnownArmor.put(target.getUniqueId(), cloneInventory(currentArmor));
            }
        }
    }

    private void synchronizeEffects(Player source) {
        if (source == null || !source.isOnline()) return;
        Map<PotionEffectType, PotionEffect> current = getEffectsMap(source);
        Map<PotionEffectType, PotionEffect> last = lastKnownEffects.get(source.getUniqueId());
        if (last != null && current.equals(last)) {
            return;
        }
        lastKnownEffects.put(source.getUniqueId(), new HashMap<>(current));
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    target.removePotionEffect(effect.getType());
                }
                for (PotionEffect effect : current.values()) {
                    target.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
                }
                lastKnownEffects.put(target.getUniqueId(), new HashMap<>(current));
            }
        }
    }

    private void synchronizeExp(Player source) {
        if (source == null || !source.isOnline()) return;
        int exp = source.getTotalExperience();
        int level = source.getLevel();
        Integer lastExp = lastKnownExp.get(source.getUniqueId());
        Integer lastLevel = lastKnownLevel.get(source.getUniqueId());
        if (lastExp != null && lastLevel != null && lastExp == exp && lastLevel == level) return;
        lastKnownExp.put(source.getUniqueId(), exp);
        lastKnownLevel.put(source.getUniqueId(), level);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                target.setTotalExperience(exp);
                target.setLevel(level);
                lastKnownExp.put(target.getUniqueId(), exp);
                lastKnownLevel.put(target.getUniqueId(), level);
            }
        }
    }

    private static class PlayerData {
        private final ItemStack[] inventory;
        private final ItemStack[] armor;
        private final Map<PotionEffectType, PotionEffect> effects;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final int exp;
        private final int level;

        public PlayerData(Player player) {
            this.inventory = cloneInventory(player.getInventory().getContents());
            this.armor = cloneInventory(player.getInventory().getArmorContents());
            this.effects = new HashMap<>();
            for (PotionEffect effect : player.getActivePotionEffects()) {
                this.effects.put(effect.getType(), effect);
            }
            this.health = player.getHealth();
            this.foodLevel = player.getFoodLevel();
            this.saturation = player.getSaturation();
            this.exp = player.getTotalExperience();
            this.level = player.getLevel();
        }
    }

    public boolean isSyncEnabled() {
        return syncEnabled;
    }

    public void setSyncEnabled(boolean enabled) {
        this.syncEnabled = enabled;
    }

    public void manualSync() {
        synchronizePlayers();
    }

    private Map<PotionEffectType, PotionEffect> getEffectsMap(Player player) {
        Map<PotionEffectType, PotionEffect> map = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            map.put(effect.getType(), effect);
        }
        return map;
    }
} 