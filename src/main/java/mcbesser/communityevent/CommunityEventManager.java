package mcbesser.communityevent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class CommunityEventManager {
    private static final int TUBE_SEGMENTS = 10;
    private static final float TOTAL_TUBE_HEIGHT = 3.0F;
    private static final int PROGRESS_ITEMS = TUBE_SEGMENTS * 2;
    private static final float COLUMN_WIDTH = 0.34F;
    private static final float TOP_POT_SCALE = 0.44F;
    private static final float ITEM_SCALE = 0.24F;
    private static final float ORBIT_ITEM_SCALE = 0.28F;
    private static final double TUBE_BASE_Y = 1.24D;
    private static final double TOP_POT_Y = TUBE_BASE_Y + TOTAL_TUBE_HEIGHT;
    private static final double ORBIT_ITEM_Y = TOP_POT_Y + 0.35D;
    private static final double PARTICLE_BASE_Y = 0.20D;
    private static final double PARTICLE_HEIGHT_SPAN = (TUBE_BASE_Y + TOTAL_TUBE_HEIGHT) - PARTICLE_BASE_Y;
    private static final double PARTICLE_FIRST_PROGRESS = 0.25D;
    private static final double PARTICLE_TOP_PROGRESS = 0.95D;
    private static final int PARTICLE_STEPS = 4;
    private static final double REWARD_HOLOGRAM_Y = 1.45D;
    private static final float REWARD_HOLOGRAM_SCALE = 0.42F;
    private static final long REWARD_HOLOGRAM_DURATION_TICKS = 100L;

    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, EventData> events = new HashMap<>();
    private final Map<UUID, Integer> pendingKeys = new HashMap<>();
    private final NamespacedKey keyMarker;
    private final List<Material> itemPool = List.of(
            Material.STONE,
            Material.COBBLESTONE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.GRANITE,
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.BIRCH_LOG,
            Material.JUNGLE_LOG,
            Material.DARK_OAK_LOG,
            Material.CHERRY_LOG,
            Material.PALE_OAK_LOG,
            Material.OAK_PLANKS,
            Material.SPRUCE_PLANKS,
            Material.BIRCH_PLANKS,
            Material.JUNGLE_PLANKS,
            Material.DARK_OAK_PLANKS,
            Material.CHERRY_PLANKS,
            Material.PALE_OAK_PLANKS,
            Material.WHEAT_SEEDS,
            Material.BEETROOT_SEEDS,
            Material.MELON_SEEDS,
            Material.PUMPKIN_SEEDS,
            Material.COCOA_BEANS,
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.BEETROOT,
            Material.MELON_SLICE,
            Material.MELON,
            Material.PUMPKIN
    );
    private final List<ItemStack> rewardPool = List.of(
            new ItemStack(Material.NETHERITE_HELMET),
            new ItemStack(Material.NETHERITE_CHESTPLATE),
            new ItemStack(Material.NETHERITE_LEGGINGS),
            new ItemStack(Material.NETHERITE_BOOTS),
            new ItemStack(Material.NETHERITE_PICKAXE),
            new ItemStack(Material.NETHERITE_AXE),
            new ItemStack(Material.NETHERITE_SHOVEL),
            new ItemStack(Material.NETHERITE_SWORD),
            new ItemStack(Material.BUDDING_AMETHYST),
            new ItemStack(Material.SMALL_AMETHYST_BUD, 8),
            new ItemStack(Material.MEDIUM_AMETHYST_BUD, 6),
            new ItemStack(Material.LARGE_AMETHYST_BUD, 4),
            new ItemStack(Material.AMETHYST_CLUSTER, 3),
            new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2),
            new ItemStack(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new ItemStack(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
            new ItemStack(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE)
    );
    private final File dataFile;
    private YamlConfiguration storage;

    public CommunityEventManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyMarker = new NamespacedKey(plugin, "trail_key");
        this.dataFile = new File(plugin.getDataFolder(), "events.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Konnte events.yml nicht erstellen: " + e.getMessage());
            }
        }

        storage = YamlConfiguration.loadConfiguration(dataFile);
        events.clear();
        pendingKeys.clear();

        ConfigurationSection eventSection = storage.getConfigurationSection("events");
        if (eventSection != null) {
            for (String id : eventSection.getKeys(false)) {
                ConfigurationSection section = eventSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                EventData data = EventData.fromConfig(id, section);
                if (data.getPotLocation() != null) {
                    events.put(id, data);
                }
            }
        }

        ConfigurationSection pendingSection = storage.getConfigurationSection("pendingKeys");
        if (pendingSection != null) {
            for (String uuid : pendingSection.getKeys(false)) {
                try {
                    pendingKeys.put(UUID.fromString(uuid), pendingSection.getInt(uuid, 0));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        if (storage == null) {
            storage = new YamlConfiguration();
        }
        storage.set("events", null);
        storage.set("pendingKeys", null);

        ConfigurationSection eventSection = storage.createSection("events");
        for (EventData event : events.values()) {
            ConfigurationSection section = eventSection.createSection(event.getId());
            event.save(section);
        }

        ConfigurationSection pendingSection = storage.createSection("pendingKeys");
        for (Map.Entry<UUID, Integer> entry : pendingKeys.entrySet()) {
            if (entry.getValue() > 0) {
                pendingSection.set(entry.getKey().toString(), entry.getValue());
            }
        }

        try {
            storage.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Eventdaten nicht speichern: " + e.getMessage());
        }
    }

    public void syncAllDisplays() {
        for (EventData event : events.values()) {
            syncVisuals(event);
        }
    }

    public void animateVisuals(long tick) {
        for (EventData event : events.values()) {
            if (!isEventChunkLoaded(event)) {
                continue;
            }
            if (event.isCompleted()) {
                continue;
            }
            animateOrbitItem(event, tick);
            spawnSpiralParticles(event, tick);
        }
    }

    public void clearLoadedVisuals() {
        for (World world : Bukkit.getWorlds()) {
            Collection<Entity> entities = new ArrayList<>(world.getEntities());
            for (Entity entity : entities) {
                if (entity.getScoreboardTags().contains("communityevent")) {
                    entity.remove();
                }
            }
        }
    }

    public void deliverPendingKeys(Player player) {
        int amount = pendingKeys.getOrDefault(player.getUniqueId(), 0);
        if (amount <= 0) {
            return;
        }
        pendingKeys.remove(player.getUniqueId());
        player.getInventory().addItem(createTrailKey(amount));
        player.sendMessage("Du hast " + amount + " Community-Event Schl\u00fcssel erhalten.");
        save();
    }

    public Optional<EventData> findEvent(Block block) {
        if (block == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(events.get(toId(block.getLocation())));
    }

    public boolean isEventVault(Block block) {
        if (block == null || block.getType() != Material.VAULT) {
            return false;
        }
        Block below = block.getRelative(0, -1, 0);
        return findEvent(below).map(EventData::isCompleted).orElse(false);
    }

    public void tryCreateEvent(Location potLocation) {
        if (potLocation == null || potLocation.getWorld() == null) {
            return;
        }

        Block potBlock = potLocation.getBlock();
        if (potBlock.getType() != Material.DECORATED_POT) {
            return;
        }
        String id = toId(potLocation);
        if (events.containsKey(id)) {
            return;
        }

        Block glassBlock = potBlock.getRelative(0, 1, 0);
        if (!isGlass(glassBlock.getType())) {
            return;
        }

        ItemFrame frame = findFlowerPotFrame(glassBlock);
        if (frame == null) {
            return;
        }

        frame.remove();
        glassBlock.setType(Material.AIR, false);

        Material target = itemPool.get(random.nextInt(itemPool.size()));
        int required = 24576 + random.nextInt(24577);
        EventData event = new EventData(id, potLocation.getWorld().getName(), potLocation.getBlockX(), potLocation.getBlockY(), potLocation.getBlockZ(), target, required);
        events.put(id, event);
        refreshVisuals(event);
        save();
    }

    public void tryCreateNearbyEvent(Location origin) {
        if (origin == null || origin.getWorld() == null) {
            return;
        }

        World world = origin.getWorld();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        for (int x = baseX - 2; x <= baseX + 2; x++) {
            for (int y = baseY - 2; y <= baseY + 2; y++) {
                for (int z = baseZ - 2; z <= baseZ + 2; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() == Material.DECORATED_POT) {
                        tryCreateEvent(block.getLocation());
                    }
                }
            }
        }
    }

    public int deposit(Player player, EventData event) {
        Material target = event.getTargetMaterial();
        int remaining = event.getRemainingAmount();
        if (remaining <= 0) {
            return 0;
        }

        int moved = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != target) {
                continue;
            }
            int accepted = Math.min(stack.getAmount(), remaining - moved);
            if (accepted <= 0) {
                break;
            }
            stack.setAmount(stack.getAmount() - accepted);
            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
            moved += accepted;
            if (moved >= remaining) {
                break;
            }
        }
        player.getInventory().setStorageContents(contents);

        if (moved > 0) {
            event.addParticipant(player.getUniqueId());
            event.addContribution(player.getUniqueId(), moved);
            event.addProgress(moved);
            refreshVisuals(event);
            if (event.isCompleted()) {
                completeEvent(event);
            }
            save();
        }
        return moved;
    }

    public boolean hasDiscoveredTarget(EventData event) {
        return event.getCollectedAmount() > 0;
    }

    public List<Map.Entry<UUID, Integer>> getTopContributors(EventData event, int limit) {
        return event.getContributions().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    public String getPlayerName(UUID uuid) {
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }
        return Optional.ofNullable(Bukkit.getOfflinePlayer(uuid).getName()).orElse("Unbekannt");
    }

    public boolean tryUseRewardKey(Player player, Block clickedBlock, ItemStack handItem) {
        if (clickedBlock == null || clickedBlock.getType() != Material.VAULT || handItem == null || !isTrailKey(handItem)) {
            return false;
        }

        Block potBlock = clickedBlock.getRelative(0, -1, 0);
        Optional<EventData> eventOptional = findEvent(potBlock);
        if (eventOptional.isEmpty() || !eventOptional.get().isCompleted()) {
            player.sendMessage("Diesen Community-Event Schl\u00fcssel kannst du hier nicht benutzen.");
            return true;
        }

        handItem.setAmount(handItem.getAmount() - 1);
        if (handItem.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }

        ItemStack reward = rewardPool.get(random.nextInt(rewardPool.size())).clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
        if (!leftover.isEmpty()) {
            clickedBlock.getWorld().dropItemNaturally(clickedBlock.getLocation().add(0.5, 1.0, 0.5), reward);
        }
        showRewardHologram(eventOptional.get(), reward);
        player.sendMessage("Der Community-Tresor hat dir " + formatItemStack(player, reward) + " gegeben.");
        return true;
    }

    public boolean isTrailKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.TRIAL_KEY) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(keyMarker, PersistentDataType.BYTE);
    }

    public ItemStack createTrailKey(int amount) {
        ItemStack key = new ItemStack(Material.TRIAL_KEY, amount);
        ItemMeta meta = key.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Community Event Schl\u00fcssel");
            meta.setLore(List.of(
                    "Nur f\u00fcr Community-Event Tresore.",
                    "Normale Trial-Tresore akzeptieren diesen Schl\u00fcssel nicht."
            ));
            meta.getPersistentDataContainer().set(keyMarker, PersistentDataType.BYTE, (byte) 1);
            key.setItemMeta(meta);
        }
        return key;
    }

    public void removeEvent(Block potBlock, boolean dropFrameAndPot) {
        findEvent(potBlock).ifPresent(event -> {
            clearVisuals(event);
            Block above = potBlock.getRelative(0, 1, 0);
            if (above.getType() == Material.VAULT) {
                above.setType(Material.AIR, false);
            }
            events.remove(event.getId());
            save();

            if (dropFrameAndPot) {
                World world = potBlock.getWorld();
                Location dropLocation = potBlock.getLocation().add(0.5, 0.8, 0.5);
                world.dropItemNaturally(dropLocation, new ItemStack(Material.ITEM_FRAME));
                world.dropItemNaturally(dropLocation, new ItemStack(Material.FLOWER_POT));
            }
        });
    }

    private void completeEvent(EventData event) {
        clearVisuals(event);
        Location abovePot = event.getAbovePotLocation();
        if (abovePot != null) {
            abovePot.getBlock().setType(Material.VAULT, false);
            configureVault(event);
        }

        for (UUID participant : event.getParticipants()) {
            Player player = Bukkit.getPlayer(participant);
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(createTrailKey(1));
                player.sendMessage("Das Community-Event ist abgeschlossen. Du hast einen Schl\u00fcssel erhalten.");
            } else {
                pendingKeys.merge(participant, 1, Integer::sum);
            }
        }
    }

    private void refreshVisuals(EventData event) {
        if (!isEventChunkLoaded(event)) {
            return;
        }
        Location potLocation = event.getPotLocation();
        if (!hasNearbyViewer(potLocation)) {
            clearVisuals(event);
            clearRewardHologram(event);
            return;
        }
        clearVisuals(event);
        if (event.isCompleted()) {
            Location abovePot = event.getAbovePotLocation();
            if (abovePot != null && abovePot.getBlock().getType() != Material.VAULT) {
                abovePot.getBlock().setType(Material.VAULT, false);
            }
            configureVault(event);
            return;
        }
        if (potLocation == null || potLocation.getWorld() == null) {
            return;
        }

        for (int i = 1; i <= TUBE_SEGMENTS; i++) {
            float segmentHeight = TOTAL_TUBE_HEIGHT / TUBE_SEGMENTS;
            double segmentBase = TUBE_BASE_Y + ((i - 1) * segmentHeight);
            Location displayLocation = potLocation.clone().add(0.5, segmentBase, 0.5);
            BlockDisplay glassSegment = potLocation.getWorld().spawn(displayLocation, BlockDisplay.class);
            glassSegment.setBlock(Bukkit.createBlockData(Material.GLASS));
            glassSegment.setPersistent(false);
            glassSegment.setBillboard(Display.Billboard.FIXED);
            glassSegment.setTransformation(new Transformation(
                    new Vector3f(-COLUMN_WIDTH / 2.0F, 0.0F, -COLUMN_WIDTH / 2.0F),
                    new AxisAngle4f(),
                    new Vector3f(COLUMN_WIDTH, segmentHeight, COLUMN_WIDTH),
                    new AxisAngle4f()
            ));
            tag(glassSegment, event.getId(), "tube");
        }

        int visibleItems = (int) Math.ceil(event.getProgress() * PROGRESS_ITEMS);
        if (event.getCollectedAmount() > 0 && visibleItems == 0) {
            visibleItems = 1;
        }

        for (int i = 0; i < visibleItems; i++) {
            double yOffset = TUBE_BASE_Y + ((TOTAL_TUBE_HEIGHT / PROGRESS_ITEMS) * (i + 0.5D));
            Location itemLocation = potLocation.clone().add(0.5, yOffset, 0.5);
            ItemDisplay itemDisplay = potLocation.getWorld().spawn(itemLocation, ItemDisplay.class);
            itemDisplay.setItemStack(new ItemStack(event.getTargetMaterial()));
            itemDisplay.setPersistent(false);
            itemDisplay.setBillboard(Display.Billboard.FIXED);
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            float rotationX = (float) (-Math.PI / 2.0D + seededRange(event.getId(), i, 0.38D));
            float rotationY = (float) seededRange(event.getId(), i + 31, Math.PI);
            float rotationZ = (float) seededRange(event.getId(), i + 67, 0.38D);
            itemDisplay.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Quaternionf()
                            .rotateX(rotationX)
                            .rotateY(rotationY)
                            .rotateZ(rotationZ),
                    new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                    new Quaternionf()
            ));
            tag(itemDisplay, event.getId(), "progress");
        }

        Location topPotLocation = potLocation.clone().add(0.5, TOP_POT_Y, 0.5);
        BlockDisplay topPot = potLocation.getWorld().spawn(topPotLocation, BlockDisplay.class);
        topPot.setBlock(Bukkit.createBlockData(Material.FLOWER_POT));
        topPot.setPersistent(false);
        topPot.setBillboard(Display.Billboard.FIXED);
        topPot.setTransformation(new Transformation(
                new Vector3f(-TOP_POT_SCALE / 2.0F, 0.0F, -TOP_POT_SCALE / 2.0F),
                new AxisAngle4f(),
                new Vector3f(TOP_POT_SCALE, TOP_POT_SCALE, TOP_POT_SCALE),
                new AxisAngle4f()
        ));
        tag(topPot, event.getId(), "top_pot");

        if (event.getCollectedAmount() > 0) {
            Location orbitLocation = potLocation.clone().add(0.5, ORBIT_ITEM_Y, 0.5);
            ItemDisplay orbitItem = potLocation.getWorld().spawn(orbitLocation, ItemDisplay.class);
            orbitItem.setItemStack(new ItemStack(event.getTargetMaterial()));
            orbitItem.setPersistent(false);
            orbitItem.setBillboard(Display.Billboard.FIXED);
            orbitItem.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            orbitItem.setInterpolationDuration(2);
            orbitItem.setInterpolationDelay(0);
            orbitItem.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new AxisAngle4f(),
                    new Vector3f(ORBIT_ITEM_SCALE, ORBIT_ITEM_SCALE, ORBIT_ITEM_SCALE),
                    new AxisAngle4f()
            ));
            tag(orbitItem, event.getId(), "orbit_item");
        }
    }

    private void syncVisuals(EventData event) {
        if (!isEventChunkLoaded(event)) {
            return;
        }
        Location potLocation = event.getPotLocation();
        if (!hasNearbyViewer(potLocation)) {
            if (hasAnyTaggedVisuals(event)) {
                clearVisuals(event);
            }
            clearRewardHologram(event);
            return;
        }
        if (event.isCompleted()) {
            if (hasAnyTaggedVisuals(event)) {
                clearVisuals(event);
            }
            Location abovePot = event.getAbovePotLocation();
            if (abovePot != null && abovePot.getBlock().getType() != Material.VAULT) {
                abovePot.getBlock().setType(Material.VAULT, false);
            }
            configureVault(event);
            return;
        }
        if (!hasExpectedVisualCount(event)) {
            refreshVisuals(event);
        }
    }

    private void clearVisuals(EventData event) {
        Location potLocation = event.getPotLocation();
        if (potLocation == null || potLocation.getWorld() == null) {
            return;
        }
        String idTag = tagFor(event.getId());
        Collection<Entity> entities = new ArrayList<>(potLocation.getWorld().getEntities());
        for (Entity entity : entities) {
            if (entity.getScoreboardTags().contains(idTag)) {
                entity.remove();
            }
        }
    }

    private boolean hasAnyTaggedVisuals(EventData event) {
        return countTaggedVisuals(event) > 0;
    }

    private boolean hasExpectedVisualCount(EventData event) {
        return countTaggedVisuals(event) == expectedVisualCount(event);
    }

    private int countTaggedVisuals(EventData event) {
        Location potLocation = event.getPotLocation();
        if (potLocation == null || potLocation.getWorld() == null) {
            return 0;
        }
        String idTag = tagFor(event.getId());
        int count = 0;
        for (Entity entity : potLocation.getWorld().getNearbyEntities(potLocation.clone().add(0.5D, 1.8D, 0.5D), 2.0D, 3.5D, 2.0D)) {
            if (!entity.getScoreboardTags().contains(idTag)) {
                continue;
            }
            if (entity.getScoreboardTags().contains("communityevent:tube")
                    || entity.getScoreboardTags().contains("communityevent:progress")
                    || entity.getScoreboardTags().contains("communityevent:top_pot")
                    || entity.getScoreboardTags().contains("communityevent:orbit_item")) {
                count++;
            }
        }
        return count;
    }

    private int expectedVisualCount(EventData event) {
        if (event.isCompleted()) {
            return 0;
        }
        int count = TUBE_SEGMENTS + 1;
        int visibleItems = (int) Math.ceil(event.getProgress() * PROGRESS_ITEMS);
        if (event.getCollectedAmount() > 0 && visibleItems == 0) {
            visibleItems = 1;
        }
        count += visibleItems;
        if (event.getCollectedAmount() > 0) {
            count++;
        }
        return count;
    }

    private boolean hasNearbyViewer(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        double maxDistance = plugin.getConfig().getDouble("display.max-view-distance", 64.0D);
        double maxDistanceSquared = maxDistance * maxDistance;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getWorld() != location.getWorld()) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    private boolean isEventChunkLoaded(EventData event) {
        Location potLocation = event.getPotLocation();
        return potLocation != null
                && potLocation.getWorld() != null
                && potLocation.getWorld().isChunkLoaded(potLocation.getBlockX() >> 4, potLocation.getBlockZ() >> 4);
    }

    private void animateOrbitItem(EventData event, long tick) {
        Location potLocation = event.getPotLocation();
        if (potLocation == null || potLocation.getWorld() == null || event.getCollectedAmount() <= 0
                || !hasNearbyViewer(potLocation)) {
            return;
        }

        ItemDisplay orbitItem = null;
        String orbitTag = "communityevent:orbit_item";
        String idTag = tagFor(event.getId());
        for (Entity entity : potLocation.getWorld().getNearbyEntities(potLocation.clone().add(0.5, ORBIT_ITEM_Y, 0.5), 1.2, 1.0, 1.2)) {
            if (entity instanceof ItemDisplay itemDisplay
                    && entity.getScoreboardTags().contains(idTag)
                    && entity.getScoreboardTags().contains(orbitTag)) {
                orbitItem = itemDisplay;
                break;
            }
        }

        if (orbitItem == null) {
            return;
        }

        double angle = tick * 0.085D;
        Location orbitLocation = potLocation.clone().add(0.5D, ORBIT_ITEM_Y, 0.5D);
        orbitLocation.setYaw((float) Math.toDegrees(-angle));
        orbitLocation.setPitch(0.0F);
        orbitItem.teleport(orbitLocation);
    }

    private void configureVault(EventData event) {
        Location abovePot = event.getAbovePotLocation();
        if (abovePot == null) {
            return;
        }

        Block vaultBlock = abovePot.getBlock();
        if (vaultBlock.getType() != Material.VAULT || !(vaultBlock.getState() instanceof Vault vault)) {
            return;
        }

        vault.setKeyItem(createTrailKey(1));
        vault.setDisplayedItem(getPreviewReward(event));
        vault.update(true, false);
    }

    private ItemStack getPreviewReward(EventData event) {
        if (rewardPool.isEmpty()) {
            return new ItemStack(Material.BARRIER);
        }
        int index = Math.floorMod(event.getId().hashCode(), rewardPool.size());
        return rewardPool.get(index).clone();
    }

    private void showRewardHologram(EventData event, ItemStack reward) {
        Location abovePot = event.getAbovePotLocation();
        if (abovePot == null || abovePot.getWorld() == null) {
            return;
        }

        clearRewardHologram(event);

        Location hologramLocation = abovePot.clone().add(0.5D, REWARD_HOLOGRAM_Y, 0.5D);
        ItemDisplay rewardDisplay = abovePot.getWorld().spawn(hologramLocation, ItemDisplay.class);
        rewardDisplay.setItemStack(reward.clone());
        rewardDisplay.setPersistent(false);
        rewardDisplay.setBillboard(Display.Billboard.CENTER);
        rewardDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        rewardDisplay.setCustomName(formatItemStack(null, reward));
        rewardDisplay.setCustomNameVisible(true);
        rewardDisplay.setTransformation(new Transformation(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new AxisAngle4f(),
                new Vector3f(REWARD_HOLOGRAM_SCALE, REWARD_HOLOGRAM_SCALE, REWARD_HOLOGRAM_SCALE),
                new AxisAngle4f()
        ));
        tag(rewardDisplay, event.getId(), "reward_hologram");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (rewardDisplay.isValid()) {
                rewardDisplay.remove();
            }
        }, REWARD_HOLOGRAM_DURATION_TICKS);
    }

    private void clearRewardHologram(EventData event) {
        Location abovePot = event.getAbovePotLocation();
        if (abovePot == null || abovePot.getWorld() == null) {
            return;
        }

        String idTag = tagFor(event.getId());
        String hologramTag = "communityevent:reward_hologram";
        for (Entity entity : abovePot.getWorld().getNearbyEntities(abovePot.clone().add(0.5D, REWARD_HOLOGRAM_Y, 0.5D), 1.4D, 1.0D, 1.4D)) {
            if (entity instanceof ItemDisplay
                    && entity.getScoreboardTags().contains(idTag)
                    && entity.getScoreboardTags().contains(hologramTag)) {
                entity.remove();
            }
        }
    }

    private String formatItemStack(Player player, ItemStack itemStack) {
        String materialName = MaterialNames.forPlayer(player, itemStack.getType());
        if (itemStack.getAmount() <= 1) {
            return materialName;
        }
        return itemStack.getAmount() + "x " + materialName;
    }

    private void spawnSpiralParticles(EventData event, long tick) {
        Location potLocation = event.getPotLocation();
        if (potLocation == null || potLocation.getWorld() == null) {
            return;
        }

        World world = potLocation.getWorld();
        Location center = potLocation.clone().add(0.5, PARTICLE_BASE_Y, 0.5);
        double baseAngle = tick * 0.18D;

        for (int strand = 0; strand < 2; strand++) {
            double strandOffset = strand * Math.PI;
            double particleStepSize = (PARTICLE_TOP_PROGRESS - PARTICLE_FIRST_PROGRESS) / (PARTICLE_STEPS - 1);
            for (int step = 0; step < PARTICLE_STEPS; step++) {
                double progress = PARTICLE_FIRST_PROGRESS + (particleStepSize * step);
                double angle = baseAngle + strandOffset + (progress * Math.PI * 1.7D);
                double radius = 0.38D + (0.04D * Math.sin(baseAngle + progress));
                double x = center.getX() + (Math.cos(angle) * radius);
                double y = center.getY() + (progress * PARTICLE_HEIGHT_SPAN);
                double z = center.getZ() + (Math.sin(angle) * radius);
                world.spawnParticle(Particle.ENCHANT, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private ItemFrame findFlowerPotFrame(Block glassBlock) {
        Location center = glassBlock.getLocation().add(0.5, 0.5, 0.5);
        for (Entity entity : glassBlock.getWorld().getNearbyEntities(center, 1.5, 1.5, 1.5)) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            if (frame.getItem().getType() != Material.FLOWER_POT) {
                continue;
            }
            Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
            if (attached.getLocation().getBlockX() == glassBlock.getX()
                    && attached.getLocation().getBlockY() == glassBlock.getY()
                    && attached.getLocation().getBlockZ() == glassBlock.getZ()) {
                return frame;
            }
        }
        return null;
    }

    private boolean isGlass(Material material) {
        return Arrays.asList(
                Material.GLASS,
                Material.WHITE_STAINED_GLASS,
                Material.LIGHT_GRAY_STAINED_GLASS
        ).contains(material);
    }

    private double seededRange(String eventId, int index, double range) {
        long seed = Objects.hash(eventId, index);
        Random seededRandom = new Random(seed);
        return (seededRandom.nextDouble() * 2.0D * range) - range;
    }

    private void tag(Entity entity, String eventId, String type) {
        entity.addScoreboardTag("communityevent");
        entity.addScoreboardTag(tagFor(eventId));
        entity.addScoreboardTag("communityevent:" + type);
    }

    private String tagFor(String eventId) {
        return "communityevent:" + eventId;
    }

    private String toId(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
