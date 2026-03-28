package mcbesser.communityevent;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommunityEventPlugin extends JavaPlugin implements Listener {
    private CommunityEventManager eventManager;

    @Override
    public void onEnable() {
        eventManager = new CommunityEventManager(this);
        eventManager.load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, eventManager::syncAllDisplays);
        getServer().getScheduler().runTaskTimer(this, () -> eventManager.animateVisuals(getServer().getCurrentTick()), 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().isOp()) {
            return;
        }

        if (event.getBlockPlaced().getType() == Material.DECORATED_POT || isAcceptedGlass(event.getBlockPlaced().getType())) {
            getServer().getScheduler().runTask(this, () -> eventManager.tryCreateNearbyEvent(event.getBlockPlaced().getLocation()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null || !event.getPlayer().isOp()) {
            return;
        }

        if (event.getEntity() instanceof ItemFrame) {
            getServer().getScheduler().runTask(this, () -> eventManager.tryCreateNearbyEvent(event.getBlock().getLocation()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (clicked.getType() == Material.VAULT && handItem != null && eventManager.isTrailKey(handItem)) {
            if (eventManager.tryUseRewardKey(player, clicked, handItem)) {
                event.setCancelled(true);
            }
            return;
        }

        if (clicked.getType() != Material.DECORATED_POT) {
            return;
        }

        eventManager.findEvent(clicked).ifPresent(data -> {
            event.setCancelled(true);
            if (data.isCompleted()) {
                player.sendMessage("Dieses Event ist abgeschlossen. Nutze den Tresor ueber dem Topf.");
                return;
            }

            int moved = eventManager.deposit(player, data);
            if (moved <= 0) {
                player.sendMessage("Gesucht wird aktuell " + beautify(data.getTargetMaterial()) + ". Es fehlen noch " + data.getRemainingAmount() + ".");
                return;
            }

            int percent = (int) Math.floor(data.getProgress() * 100.0D);
            player.sendMessage("Du hast " + moved + "x " + beautify(data.getTargetMaterial()) + " eingelagert. Fortschritt: " + percent + "% (" + data.getCollectedAmount() + "/" + data.getRequiredAmount() + ").");
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.DECORATED_POT) {
            if (eventManager.findEvent(block).isPresent() && !event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("Nur OPs duerfen diese Community-Konstruktion abbauen.");
                return;
            }
            eventManager.removeEvent(block, true);
        } else if (block.getType() == Material.VAULT && eventManager.isEventVault(block)) {
            if (!event.getPlayer().isOp()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("Nur OPs duerfen diese Community-Konstruktion abbauen.");
                return;
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage("Baue den dekorierten Topf ab, um die Community-Konstruktion zu entfernen.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isOp()) {
            return;
        }

        if (event.getRightClicked() instanceof ItemFrame frame) {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand.getType() == Material.FLOWER_POT || frame.getItem().getType() == Material.FLOWER_POT) {
                getServer().getScheduler().runTaskLater(this, () -> eventManager.tryCreateNearbyEvent(frame.getLocation()), 1L);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        eventManager.deliverPendingKeys(event.getPlayer());
    }

    private boolean isAcceptedGlass(Material material) {
        return material == Material.GLASS
                || material == Material.WHITE_STAINED_GLASS
                || material == Material.LIGHT_GRAY_STAINED_GLASS;
    }

    private String beautify(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
