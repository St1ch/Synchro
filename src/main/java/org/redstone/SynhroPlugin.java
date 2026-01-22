package org.redstone;


import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.redstone.SynchroAPI;
import org.bukkit.attribute.Attribute;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;

public class SynhroPlugin extends JavaPlugin implements Listener {
    private Map<UUID, PlayerData> playerDataMap;
    private Map<UUID, Boolean> respawningPlayers;
    private final AtomicBoolean isSynchronizing = new AtomicBoolean(false);
    private Map<UUID, ItemStack[]> lastKnownInventory;
    private boolean syncEnabled = true;
    private Map<UUID, ItemStack[]> lastKnownArmor;
    private Map<UUID, Map<PotionEffectType, PotionEffect>> lastKnownEffects;
    private Map<UUID, Double> lastKnownHealth;
    private Map<UUID, Integer> lastKnownHunger;
    private Map<UUID, Float> lastKnownSaturation;
    private Map<UUID, Integer> lastKnownExp;
    private Map<UUID, Integer> lastKnownLevel;
    private Map<UUID, Integer> lastKnownFireTicks;
    private final Object masterStateLock = new Object();
    private volatile ItemStack[] masterInventory;
    private volatile ItemStack[] masterArmor;
    private volatile Map<PotionEffectType, PotionEffect> masterEffects;
    private volatile Double masterHealth;
    private volatile Integer masterFoodLevel;
    private volatile Float masterSaturation;
    private volatile Integer masterExp;
    private volatile Integer masterLevel;
    private volatile ItemStack[] masterEnderChest;
    private volatile Integer masterFireTicks;
    private final Set<UUID> advancementSyncInProgress = ConcurrentHashMap.newKeySet();
    private final Object deathSyncLock = new Object();
    private volatile UUID primaryDeathPlayerId;
    private final Object inventorySyncQueueLock = new Object();
    private final java.util.TreeSet<UUID> pendingInventorySync = new java.util.TreeSet<>();
    private boolean inventorySyncFlushScheduled = false;
    private final Map<UUID, Integer> inventoryInteractionDepth = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingInventoryApply = new ConcurrentHashMap<>();
    private static final long INVENTORY_SYNC_QUEUE_DELAY_TICKS = 1L;
    private static final long INVENTORY_SYNC_EXEC_DELAY_TICKS = 1L;
    private static final long INVENTORY_INTERACTION_LOCK_TICKS = 2L;
    private final Map<UUID, Integer> pendingArmorSyncAttempts = new ConcurrentHashMap<>();
    private static final int ARMOR_SYNC_MAX_RETRIES = 40;
    private static final long ARMOR_SYNC_RETRY_DELAY_TICKS = 1L;

    private static final Method POTION_HAS_ICON;
    private static final Constructor<PotionEffect> POTION_CTOR_WITH_ICON;
    private static final Constructor<PotionEffect> POTION_CTOR_NO_ICON;

    static {
        Method hasIcon = null;
        Constructor<PotionEffect> ctorWithIcon = null;
        Constructor<PotionEffect> ctorNoIcon = null;
        try {
            hasIcon = PotionEffect.class.getMethod("hasIcon");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            ctorWithIcon = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class, boolean.class, boolean.class, boolean.class);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            ctorNoIcon = PotionEffect.class.getConstructor(PotionEffectType.class, int.class, int.class, boolean.class, boolean.class);
        } catch (ReflectiveOperationException ignored) {
        }
        POTION_HAS_ICON = hasIcon;
        POTION_CTOR_WITH_ICON = ctorWithIcon;
        POTION_CTOR_NO_ICON = ctorNoIcon;
    }

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
        lastKnownFireTicks = new ConcurrentHashMap<>();
        masterInventory = null;
        masterArmor = null;
        masterEffects = null;
        masterHealth = null;
        masterFoodLevel = null;
        masterSaturation = null;
        masterExp = null;
        masterLevel = null;
        masterEnderChest = null;
        masterFireTicks = null;
        primaryDeathPlayerId = null;
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
                runLater(player, () -> {
                    if (!player.isOnline()) return;
                    player.getInventory().setContents(cloneInventory(data.inventory));
                    player.getInventory().setArmorContents(cloneInventory(data.armor));
                    player.getEnderChest().setContents(cloneInventory(data.enderChest));
                    for (PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                    for (PotionEffect effect : data.effects.values()) {
                        player.addPotionEffect(clonePotionEffect(effect));
                    }
                    player.setHealth(Math.min(data.health, getMaxHealth(player)));
                    player.setFoodLevel(data.foodLevel);
                    player.setSaturation(data.saturation);
                    player.setTotalExperience(data.exp);
                    player.setLevel(data.level);
                }, 1L);
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
        lastKnownFireTicks.clear();
        masterInventory = null;
        masterArmor = null;
        masterEffects = null;
        masterHealth = null;
        masterFoodLevel = null;
        masterSaturation = null;
        masterExp = null;
        masterLevel = null;
        masterEnderChest = null;
        masterFireTicks = null;
        primaryDeathPlayerId = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!syncEnabled) return;
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
        lastKnownFireTicks.put(player.getUniqueId(), player.getFireTicks());
        runLater(player, () -> applyMastersToPlayer(player), 1L);
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
        lastKnownFireTicks.remove(playerId);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (!syncEnabled) return;
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        runLater(player, () -> {
            if (!player.isSleeping()) return;
            long time = player.getWorld().getTime();
            if (time >= 12541L && time <= 23458L) {
                player.getWorld().setStorm(false);
                player.getWorld().setThundering(false);
                player.getWorld().setTime(0L);
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderChestOpen(InventoryOpenEvent event) {
        if (!syncEnabled) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        ItemStack[] current = cloneInventory(player.getEnderChest().getContents());
        ItemStack[] snapshot;
        synchronized (masterStateLock) {
            if (masterEnderChest == null) {
                masterEnderChest = current;
            }
            snapshot = masterEnderChest;
        }
        if (snapshot != null) {
            player.getEnderChest().setContents(cloneInventory(snapshot));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnderChestClose(InventoryCloseEvent event) {
        if (!syncEnabled) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ENDER_CHEST) return;
        ItemStack[] current = cloneInventory(player.getEnderChest().getContents());
        boolean changed;
        synchronized (masterStateLock) {
            if (masterEnderChest == null) {
                masterEnderChest = current;
                changed = true;
            } else if (!areInventoriesEqual(current, masterEnderChest)) {
                masterEnderChest = current;
                changed = true;
            } else {
                changed = false;
            }
        }
        if (!changed) return;
        forEachOnlinePlayer(target -> {
            if (target.isOnline()) {
                runLater(target, () -> target.getEnderChest().setContents(cloneInventory(current)), 1L);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!syncEnabled) return;
        Player source = event.getPlayer();
        if (advancementSyncInProgress.contains(source.getUniqueId())) return;
        Advancement advancement = event.getAdvancement();
        forEachOnlinePlayer(target -> {
            if (target == source || !target.isOnline()) return;
            advancementSyncInProgress.add(target.getUniqueId());
            try {
                AdvancementProgress progress = target.getAdvancementProgress(advancement);
                for (String criterion : advancement.getCriteria()) {
                    if (!progress.getAwardedCriteria().contains(criterion)) {
                        progress.awardCriteria(criterion);
                    }
                }
            } finally {
                advancementSyncInProgress.remove(target.getUniqueId());
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!syncEnabled) return;
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                markInventoryInteraction(player);
                requestInventorySync(player);
                requestArmorSync(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!syncEnabled) return;
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                markInventoryInteraction(player);
                requestInventorySync(player);
                requestArmorSync(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!syncEnabled) return;
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                markInventoryInteraction(player);
                requestInventorySync(player);
                requestArmorSync(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!syncEnabled) return;
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                requestInventorySync(player);
                requestArmorSync(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (respawningPlayers.containsKey(player.getUniqueId())) return;
        if (event.getHand() == null) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Material inHand = event.getHand() == EquipmentSlot.HAND
            ? player.getInventory().getItemInMainHand().getType()
            : player.getInventory().getItemInOffHand().getType();
        if (isVehiclePlacementItem(inHand)) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
        if (isArmorItem(inHand)) {
            markInventoryInteraction(player);
            requestInventorySync(player);
            requestArmorSync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (player == null) return;
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (respawningPlayers.containsKey(player.getUniqueId())) return;

        if (event.getRightClicked() instanceof ItemFrame) {
            markInventoryInteraction(player);
            requestInventorySync(player);
            return;
        }

        if (event.getRightClicked() instanceof ChestedHorse chestedHorse) {
            boolean holdingChest =
                player.getInventory().getItemInMainHand().getType() == Material.CHEST
                    || player.getInventory().getItemInOffHand().getType() == Material.CHEST;
            if (holdingChest && !chestedHorse.isCarryingChest()) {
                markInventoryInteraction(player);
                requestInventorySync(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!syncEnabled) return;
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();
        boolean alreadyRespawning = respawningPlayers.containsKey(playerId);
        respawningPlayers.put(playerId, true);
        boolean isPrimary;
        synchronized (deathSyncLock) {
            if (!alreadyRespawning && primaryDeathPlayerId == null) {
                primaryDeathPlayerId = playerId;
                runGlobal(() -> {
                    synchronized (deathSyncLock) {
                        primaryDeathPlayerId = null;
                    }
                }, 200L);
                isPrimary = true;
            } else {
                isPrimary = playerId.equals(primaryDeathPlayerId);
            }
        }
        if (!isPrimary) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
            lastKnownInventory.put(playerId, cloneInventory(player.getInventory().getContents()));
            lastKnownArmor.put(playerId, cloneInventory(player.getInventory().getArmorContents()));
        } else {
            synchronized (masterStateLock) {
                masterInventory = new ItemStack[player.getInventory().getContents().length];
                masterArmor = new ItemStack[player.getInventory().getArmorContents().length];
            }
            lastKnownInventory.put(playerId, cloneInventory(masterInventory));
            lastKnownArmor.put(playerId, cloneInventory(masterArmor));
        }
        if (alreadyRespawning) return;

        runGlobal(() -> {
            List<Player> toKill = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p != player && p.isOnline() && !p.isDead() && !respawningPlayers.containsKey(p.getUniqueId()))
                .collect(Collectors.toList());

            for (Player target : toKill) {
                respawningPlayers.put(target.getUniqueId(), true);
                runLater(target, () -> {
                    if (target.isOnline()) {
                        target.setHealth(0);
                    }
                }, 1L);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        respawningPlayers.put(player.getUniqueId(), true);
        runLater(player, () -> {
            respawningPlayers.remove(player.getUniqueId());
            synchronizePlayers();
            synchronizeHealth(player);
            synchronizeHunger(player);
            synchronizeExp(player);
        }, 20L);
        synchronized (deathSyncLock) {
            if (player.getUniqueId().equals(primaryDeathPlayerId)) {
                primaryDeathPlayerId = null;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player player) {
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                runLater(player, () -> synchronizeFireTicks(player), 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            Integer last = lastKnownFireTicks.get(player.getUniqueId());
            int current = Math.max(0, player.getFireTicks());
            if (last == null || last != current) {
                runLater(player, () -> synchronizeFireTicks(player), 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHealthChange(EntityDamageEvent event) {
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                EntityDamageEvent.DamageCause cause = event.getCause();
                if (cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
                    synchronizeFireTicks(player);
                }
                synchronizeHealth(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHungerChange(FoodLevelChangeEvent event) {
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                synchronizeHunger(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            requestInventorySync(player);
            runLater(player, () -> synchronizeArmor(player), 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                markInventoryInteraction(player);
                requestInventorySync(player);
                runLater(player, () -> synchronizeArmor(player), 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            markInventoryInteraction(player);
            requestInventorySync(player);
            runLater(player, () -> {
                synchronizeArmor(player);
                synchronizeHunger(player);
            }, 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            requestInventorySync(player);
            runLater(player, () -> {
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
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            requestInventorySync(player);
            runLater(player, () -> {
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
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                runLater(player, () -> synchronizeEffects(player), 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!syncEnabled) return;
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (!respawningPlayers.containsKey(player.getUniqueId())) {
                runLater(player, () -> synchronizeHealth(player), 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExpChange(PlayerExpChangeEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            runLater(player, () -> synchronizeExp(player), 1L);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLevelChange(PlayerLevelChangeEvent event) {
        if (!syncEnabled) return;
        Player player = event.getPlayer();
        if (!respawningPlayers.containsKey(player.getUniqueId())) {
            runLater(player, () -> synchronizeExp(player), 1L);
        }
    }

    private void requestInventorySync(Player player) {
        if (!syncEnabled) return;
        if (player == null || !player.isOnline()) return;

        boolean shouldScheduleFlush = false;
        synchronized (inventorySyncQueueLock) {
            pendingInventorySync.add(player.getUniqueId());
            if (!inventorySyncFlushScheduled) {
                inventorySyncFlushScheduled = true;
                shouldScheduleFlush = true;
            }
        }

        if (shouldScheduleFlush) {
            runGlobal(this::flushInventorySyncQueue, INVENTORY_SYNC_QUEUE_DELAY_TICKS);
        }
    }

    private void flushInventorySyncQueue() {
        if (!syncEnabled) {
            synchronized (inventorySyncQueueLock) {
                pendingInventorySync.clear();
                inventorySyncFlushScheduled = false;
            }
            return;
        }
        List<UUID> batch;
        synchronized (inventorySyncQueueLock) {
            batch = new java.util.ArrayList<>(pendingInventorySync);
            pendingInventorySync.clear();
            inventorySyncFlushScheduled = false;
        }

        java.util.ArrayList<UUID> remaining = new java.util.ArrayList<>();
        UUID selected = null;
        for (UUID playerId : batch) {
            Player source = Bukkit.getPlayer(playerId);
            if (selected == null && source != null && source.isOnline() && !respawningPlayers.containsKey(playerId)) {
                selected = playerId;
            } else {
                remaining.add(playerId);
            }
        }

        if (selected == null) return;

        if (!remaining.isEmpty()) {
            synchronized (inventorySyncQueueLock) {
                pendingInventorySync.addAll(remaining);
                if (!inventorySyncFlushScheduled) {
                    inventorySyncFlushScheduled = true;
                    runGlobal(this::flushInventorySyncQueue, 1L);
                }
            }
        }

        Player selectedPlayer = Bukkit.getPlayer(selected);
        if (selectedPlayer != null && selectedPlayer.isOnline() && !respawningPlayers.containsKey(selected)) {
            runLater(selectedPlayer, () -> synchronizeInventory(selectedPlayer), INVENTORY_SYNC_EXEC_DELAY_TICKS);
        }
    }

    private boolean isVehiclePlacementItem(Material material) {
        return switch (material) {
            case OAK_BOAT,
                 SPRUCE_BOAT,
                 BIRCH_BOAT,
                 JUNGLE_BOAT,
                 ACACIA_BOAT,
                 DARK_OAK_BOAT,
                 MINECART,
                 CHEST_MINECART,
                 FURNACE_MINECART,
                 HOPPER_MINECART,
                 TNT_MINECART,
                 COMMAND_BLOCK_MINECART -> true;
            default -> false;
        };
    }

    private boolean isArmorItem(Material material) {
        return switch (material) {
            case LEATHER_HELMET,
                 LEATHER_CHESTPLATE,
                 LEATHER_LEGGINGS,
                 LEATHER_BOOTS,
                 CHAINMAIL_HELMET,
                 CHAINMAIL_CHESTPLATE,
                 CHAINMAIL_LEGGINGS,
                 CHAINMAIL_BOOTS,
                 IRON_HELMET,
                 IRON_CHESTPLATE,
                 IRON_LEGGINGS,
                 IRON_BOOTS,
                 GOLDEN_HELMET,
                 GOLDEN_CHESTPLATE,
                 GOLDEN_LEGGINGS,
                 GOLDEN_BOOTS,
                 DIAMOND_HELMET,
                 DIAMOND_CHESTPLATE,
                 DIAMOND_LEGGINGS,
                 DIAMOND_BOOTS,
                 NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS,
                 TURTLE_HELMET,
                 ELYTRA,
                 CARVED_PUMPKIN,
                 CREEPER_HEAD,
                 DRAGON_HEAD,
                 PLAYER_HEAD,
                 SKELETON_SKULL,
                 WITHER_SKELETON_SKULL,
                 ZOMBIE_HEAD -> true;
            default -> false;
        };
    }

    private void requestArmorSync(Player player) {
        if (!syncEnabled) return;
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (pendingArmorSyncAttempts.put(playerId, 0) != null) return;
        runLater(player, () -> attemptArmorSync(player), ARMOR_SYNC_RETRY_DELAY_TICKS);
    }

    private void attemptArmorSync(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (respawningPlayers.containsKey(playerId)) {
            pendingArmorSyncAttempts.remove(playerId);
            return;
        }
        ItemStack[] current = player.getInventory().getArmorContents();
        ItemStack[] last = lastKnownArmor.get(playerId);
        if (last == null || !areInventoriesEqual(current, last)) {
            pendingArmorSyncAttempts.remove(playerId);
            synchronizeArmor(player);
            return;
        }
        Integer attempt = pendingArmorSyncAttempts.get(playerId);
        if (attempt == null || attempt >= ARMOR_SYNC_MAX_RETRIES) {
            pendingArmorSyncAttempts.remove(playerId);
            return;
        }
        pendingArmorSyncAttempts.put(playerId, attempt + 1);
        runLater(player, () -> attemptArmorSync(player), ARMOR_SYNC_RETRY_DELAY_TICKS);
    }

    private void synchronizePlayers() {
        if (!syncEnabled) return;
        if (!isSynchronizing.compareAndSet(false, true)) return;
        try {
            ItemStack[] currentMaster;
            synchronized (masterStateLock) {
                currentMaster = masterInventory;
            }
            Player source = null;
            if (currentMaster == null || isInventoryEmpty(currentMaster)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnline()
                        && !respawningPlayers.containsKey(player.getUniqueId())
                        && !isInventoryEmpty(player.getInventory().getContents())) {
                        source = player;
                        break;
                    }
                }
            }
            if (source == null) {
                source = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            }
            if (source == null) {
                isSynchronizing.set(false);
                return;
            }
            final Player finalSource = source;
            runLater(finalSource, () -> {
                initializeMastersFromPlayerIfNeeded(finalSource);
                applyMastersToAllPlayers();
                isSynchronizing.set(false);
            }, 1L);
        } catch (Throwable ignored) {
            isSynchronizing.set(false);
        }
    }

    private void synchronizeInventory(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        
        ItemStack[] currentContents = source.getInventory().getContents();
        ItemStack[] newMaster = cloneInventory(currentContents);
        boolean changed;
        synchronized (masterStateLock) {
            if (masterInventory == null) {
                masterInventory = newMaster;
                changed = true;
            } else if (!areInventoriesEqual(currentContents, masterInventory)) {
                masterInventory = newMaster;
                changed = true;
            } else {
                changed = false;
            }
        }

        if (!changed) return;

        lastKnownInventory.put(source.getUniqueId(), cloneInventory(newMaster));
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    applyMasterInventoryToPlayer(target);
                }, 1L);
            }
        });
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

    private static boolean isInventoryEmpty(ItemStack[] inventory) {
        for (ItemStack item : inventory) {
            if (item != null) return false;
        }
        return true;
    }

    private void synchronizeHealth(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline() || source.isDead()) return;
        double health = source.getHealth();
        Double last = lastKnownHealth.get(source.getUniqueId());
        if (last != null && last == health) return;
        lastKnownHealth.put(source.getUniqueId(), health);
        synchronized (masterStateLock) {
            masterHealth = health;
        }
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId()) && !target.isDead()) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId()) || target.isDead()) return;
                    target.setHealth(Math.min(health, getMaxHealth(target)));
                    lastKnownHealth.put(target.getUniqueId(), health);
                }, 1L);
            }
        });
    }

    private void synchronizeHunger(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        
        int foodLevel = source.getFoodLevel();
        float saturation = source.getSaturation();
        Integer lastFood = lastKnownHunger.get(source.getUniqueId());
        Float lastSat = lastKnownSaturation.get(source.getUniqueId());
        if (lastFood != null && lastSat != null && lastFood == foodLevel && lastSat == saturation) return;
        lastKnownHunger.put(source.getUniqueId(), foodLevel);
        lastKnownSaturation.put(source.getUniqueId(), saturation);
        synchronized (masterStateLock) {
            masterFoodLevel = foodLevel;
            masterSaturation = saturation;
        }
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    target.setFoodLevel(foodLevel);
                    target.setSaturation(saturation);
                    lastKnownHunger.put(target.getUniqueId(), foodLevel);
                    lastKnownSaturation.put(target.getUniqueId(), saturation);
                }, 1L);
            }
        });
    }

    private void synchronizeArmor(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        ItemStack[] currentArmor = source.getInventory().getArmorContents();
        ItemStack[] newMaster = cloneInventory(currentArmor);
        boolean changed;
        synchronized (masterStateLock) {
            if (masterArmor == null) {
                masterArmor = newMaster;
                changed = true;
            } else if (!areInventoriesEqual(currentArmor, masterArmor)) {
                masterArmor = newMaster;
                changed = true;
            } else {
                changed = false;
            }
        }

        if (!changed) return;

        ItemStack[] snapshot = newMaster;
        lastKnownArmor.put(source.getUniqueId(), cloneInventory(snapshot));
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    target.getInventory().setArmorContents(cloneInventory(snapshot));
                    lastKnownArmor.put(target.getUniqueId(), cloneInventory(snapshot));
                }, 1L);
            }
        });
    }

    private void synchronizeEffects(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        Map<PotionEffectType, PotionEffect> current = getEffectsMap(source);
        Map<PotionEffectType, PotionEffect> last = lastKnownEffects.get(source.getUniqueId());
        if (last != null && current.equals(last)) {
            return;
        }
        lastKnownEffects.put(source.getUniqueId(), new HashMap<>(current));
        synchronized (masterStateLock) {
            masterEffects = new HashMap<>(current);
        }
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    for (PotionEffect effect : target.getActivePotionEffects()) {
                        target.removePotionEffect(effect.getType());
                    }
                    for (PotionEffect effect : current.values()) {
                        target.addPotionEffect(clonePotionEffect(effect));
                    }
                    lastKnownEffects.put(target.getUniqueId(), new HashMap<>(current));
                }, 1L);
            }
        });
    }

    private void synchronizeExp(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        int exp = source.getTotalExperience();
        int level = source.getLevel();
        Integer lastExp = lastKnownExp.get(source.getUniqueId());
        Integer lastLevel = lastKnownLevel.get(source.getUniqueId());
        if (lastExp != null && lastLevel != null && lastExp == exp && lastLevel == level) return;
        lastKnownExp.put(source.getUniqueId(), exp);
        lastKnownLevel.put(source.getUniqueId(), level);
        synchronized (masterStateLock) {
            masterExp = exp;
            masterLevel = level;
        }
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    target.setTotalExperience(exp);
                    target.setLevel(level);
                    lastKnownExp.put(target.getUniqueId(), exp);
                    lastKnownLevel.put(target.getUniqueId(), level);
                }, 1L);
            }
        });
    }

    private static class PlayerData {
        private final ItemStack[] inventory;
        private final ItemStack[] armor;
        private final ItemStack[] enderChest;
        private final Map<PotionEffectType, PotionEffect> effects;
        private final double health;
        private final int foodLevel;
        private final float saturation;
        private final int exp;
        private final int level;

        public PlayerData(Player player) {
            this.inventory = cloneInventory(player.getInventory().getContents());
            this.armor = cloneInventory(player.getInventory().getArmorContents());
            this.enderChest = cloneInventory(player.getEnderChest().getContents());
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

    private void applyMastersToPlayer(Player player) {
        if (!syncEnabled) return;
        if (player == null || !player.isOnline()) return;
        ItemStack[] inv;
        ItemStack[] armor;
        Map<PotionEffectType, PotionEffect> effects;
        Double health;
        Integer foodLevel;
        Float saturation;
        Integer exp;
        Integer level;
        synchronized (masterStateLock) {
            inv = masterInventory;
            armor = masterArmor;
            effects = masterEffects;
            health = masterHealth;
            foodLevel = masterFoodLevel;
            saturation = masterSaturation;
            exp = masterExp;
            level = masterLevel;
        }

        if (inv != null) {
            applyMasterInventoryToPlayer(player);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(cloneInventory(armor));
            lastKnownArmor.put(player.getUniqueId(), cloneInventory(armor));
        }
        if (masterEnderChest != null) {
            player.getEnderChest().setContents(cloneInventory(masterEnderChest));
        }
        if (effects != null) {
            for (PotionEffect effect : player.getActivePotionEffects()) {
                player.removePotionEffect(effect.getType());
            }
            for (PotionEffect effect : effects.values()) {
                player.addPotionEffect(clonePotionEffect(effect));
            }
            lastKnownEffects.put(player.getUniqueId(), new HashMap<>(effects));
        }
        if (health != null && !player.isDead()) {
            player.setHealth(Math.min(health, getMaxHealth(player)));
            lastKnownHealth.put(player.getUniqueId(), health);
        }
        if (foodLevel != null && saturation != null) {
            player.setFoodLevel(foodLevel);
            player.setSaturation(saturation);
            lastKnownHunger.put(player.getUniqueId(), foodLevel);
            lastKnownSaturation.put(player.getUniqueId(), saturation);
        }
        if (exp != null && level != null) {
            player.setTotalExperience(exp);
            player.setLevel(level);
            lastKnownExp.put(player.getUniqueId(), exp);
            lastKnownLevel.put(player.getUniqueId(), level);
        }
    }

    private void applyMastersToAllPlayers() {
        forEachOnlinePlayer(target -> {
            if (target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> applyMastersToPlayer(target), 1L);
            }
        });
    }

    private void initializeMastersFromPlayerIfNeeded(Player player) {
        if (player == null || !player.isOnline()) return;
        synchronized (masterStateLock) {
            ItemStack[] currentInventory = player.getInventory().getContents();
            ItemStack[] currentArmor = player.getInventory().getArmorContents();
            if (masterInventory == null || (isInventoryEmpty(masterInventory) && !isInventoryEmpty(currentInventory))) {
                masterInventory = cloneInventory(currentInventory);
            }
            if (masterArmor == null || (isInventoryEmpty(masterArmor) && !isInventoryEmpty(currentArmor))) {
                masterArmor = cloneInventory(currentArmor);
            }
            if (masterEffects == null) masterEffects = new HashMap<>(getEffectsMap(player));
            if (masterHealth == null) masterHealth = player.isDead() ? null : player.getHealth();
            if (masterFoodLevel == null) masterFoodLevel = player.getFoodLevel();
            if (masterSaturation == null) masterSaturation = player.getSaturation();
            if (masterExp == null) masterExp = player.getTotalExperience();
            if (masterLevel == null) masterLevel = player.getLevel();
            if (masterEnderChest == null) masterEnderChest = cloneInventory(player.getEnderChest().getContents());
        }
    }

    private void synchronizeFireTicks(Player source) {
        if (!syncEnabled) return;
        if (source == null || !source.isOnline()) return;
        int fireTicks = Math.max(0, source.getFireTicks());
        Integer last = lastKnownFireTicks.get(source.getUniqueId());
        if (last != null && last == fireTicks) return;
        lastKnownFireTicks.put(source.getUniqueId(), fireTicks);
        synchronized (masterStateLock) {
            masterFireTicks = fireTicks;
        }
        forEachOnlinePlayer(target -> {
            if (target != source && target.isOnline() && !respawningPlayers.containsKey(target.getUniqueId())) {
                runLater(target, () -> {
                    if (!target.isOnline() || respawningPlayers.containsKey(target.getUniqueId())) return;
                    target.setFireTicks(fireTicks);
                    lastKnownFireTicks.put(target.getUniqueId(), fireTicks);
                }, 1L);
            }
        });
    }

    private double getMaxHealth(Player player) {
        try {
            if (player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                return player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            }
        } catch (Throwable ignored) {
        }
        return 20.0;
    }

    private PotionEffect clonePotionEffect(PotionEffect effect) {
        boolean icon = true;
        if (POTION_HAS_ICON != null) {
            try {
                icon = (boolean) POTION_HAS_ICON.invoke(effect);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (POTION_CTOR_WITH_ICON != null) {
            try {
                return POTION_CTOR_WITH_ICON.newInstance(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), icon);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        if (POTION_CTOR_NO_ICON != null) {
            try {
                return POTION_CTOR_NO_ICON.newInstance(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles());
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return effect;
    }

    private void runLater(Player player, Runnable task, long delayTicks) {
        if (player != null && tryRunOnEntityScheduler(player, task, delayTicks)) return;
        if (delayTicks <= 0L) {
            Bukkit.getScheduler().runTask(this, task);
        } else {
            Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
        }
    }

    private void runGlobal(Runnable task, long delayTicks) {
        if (tryRunOnGlobalScheduler(task, delayTicks)) return;
        if (delayTicks <= 0L) {
            Bukkit.getScheduler().runTask(this, task);
        } else {
            Bukkit.getScheduler().runTaskLater(this, task, delayTicks);
        }
    }

    private boolean tryRunOnEntityScheduler(Player player, Runnable task, long delayTicks) {
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(player);
            if (delayTicks <= 1L) {
                Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                Consumer<Object> consumer = ignored -> task.run();
                run.invoke(scheduler, this, consumer, null);
            } else {
                Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                Consumer<Object> consumer = ignored -> task.run();
                runDelayed.invoke(scheduler, this, consumer, null, delayTicks);
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean tryRunOnGlobalScheduler(Runnable task, long delayTicks) {
        try {
            Object server = Bukkit.getServer();
            Method getGlobalRegionScheduler = server.getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobalRegionScheduler.invoke(server);
            if (delayTicks <= 1L) {
                Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                Consumer<Object> consumer = ignored -> task.run();
                run.invoke(scheduler, this, consumer);
            } else {
                Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                Consumer<Object> consumer = ignored -> task.run();
                runDelayed.invoke(scheduler, this, consumer, delayTicks);
            }
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void forEachOnlinePlayer(Consumer<Player> action) {
        runGlobal(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                action.accept(player);
            }
        }, 1L);
    }

    private void markInventoryInteraction(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        inventoryInteractionDepth.merge(playerId, 1, Integer::sum);
        runLater(player, () -> inventoryInteractionDepth.compute(playerId, (key, value) -> {
            if (value == null || value <= 1) return null;
            return value - 1;
        }), INVENTORY_INTERACTION_LOCK_TICKS);
    }

    private boolean isInventoryLocked(Player player) {
        if (player == null) return false;
        Integer depth = inventoryInteractionDepth.get(player.getUniqueId());
        return depth != null && depth > 0;
    }

    private void applyMasterInventoryToPlayer(Player player) {
        if (!syncEnabled) return;
        if (player == null || !player.isOnline()) return;
        if (respawningPlayers.containsKey(player.getUniqueId())) return;
        if (isInventoryLocked(player)) {
            schedulePendingInventoryApply(player);
            return;
        }
        ItemStack[] invSnapshot;
        synchronized (masterStateLock) {
            invSnapshot = masterInventory;
        }
        if (invSnapshot == null) return;
        ItemStack[] clone = cloneInventory(invSnapshot);
        player.getInventory().setContents(clone);
        lastKnownInventory.put(player.getUniqueId(), cloneInventory(clone));
    }

    private void schedulePendingInventoryApply(Player player) {
        if (!syncEnabled) return;
        if (player == null || !player.isOnline()) return;
        UUID playerId = player.getUniqueId();
        if (pendingInventoryApply.putIfAbsent(playerId, true) != null) return;
        runLater(player, () -> {
            pendingInventoryApply.remove(playerId);
            if (!player.isOnline() || respawningPlayers.containsKey(playerId)) return;
            if (isInventoryLocked(player)) {
                schedulePendingInventoryApply(player);
                return;
            }
            ItemStack[] invSnapshot;
            synchronized (masterStateLock) {
                invSnapshot = masterInventory;
            }
            if (invSnapshot == null) return;
            ItemStack[] clone = cloneInventory(invSnapshot);
            player.getInventory().setContents(clone);
            lastKnownInventory.put(playerId, cloneInventory(clone));
        }, INVENTORY_INTERACTION_LOCK_TICKS);
    }

    private Map<PotionEffectType, PotionEffect> getEffectsMap(Player player) {
        Map<PotionEffectType, PotionEffect> map = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            map.put(effect.getType(), effect);
        }
        return map;
    }
} 
