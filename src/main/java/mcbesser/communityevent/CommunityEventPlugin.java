package mcbesser.communityevent;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommunityEventPlugin extends JavaPlugin implements Listener {
    private CommunityEventManager eventManager;
    private final Map<UUID, Scoreboard> previousScoreboards = new HashMap<>();
    private final Map<UUID, String> activeSidebarEvents = new HashMap<>();

    @Override
    public void onEnable() {
        eventManager = new CommunityEventManager(this);
        eventManager.load();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTask(this, eventManager::syncAllDisplays);
        getServer().getScheduler().runTaskTimer(this, () -> eventManager.animateVisuals(getServer().getCurrentTick()), 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, this::updateViewedEventScoreboards, 10L, 10L);
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restoreSidebar(player);
        }
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
                if (eventManager.hasDiscoveredTarget(data)) {
                    player.sendMessage("Gesucht wird aktuell " + MaterialNames.forPlayer(player, data.getTargetMaterial()) + ". Es fehlen noch " + data.getRemainingAmount() + ".");
                } else {
                    player.sendMessage("Noch ist unklar, welches Item gebraucht wird. Probiere einfache farmbare Items aus.");
                }
                return;
            }

            if (eventManager.hasDiscoveredTarget(data)) {
                int percent = (int) Math.floor(data.getProgress() * 100.0D);
                player.sendMessage("Du hast " + moved + "x " + MaterialNames.forPlayer(player, data.getTargetMaterial()) + " eingelagert. Fortschritt: " + percent + "% (" + data.getCollectedAmount() + "/" + data.getRequiredAmount() + ").");
            }
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        previousScoreboards.remove(event.getPlayer().getUniqueId());
        activeSidebarEvents.remove(event.getPlayer().getUniqueId());
    }

    private boolean isAcceptedGlass(Material material) {
        return material == Material.GLASS
                || material == Material.WHITE_STAINED_GLASS
                || material == Material.LIGHT_GRAY_STAINED_GLASS;
    }

    private void updateViewedEventScoreboards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Block targetBlock = player.getTargetBlockExact(8);
            if (targetBlock == null || targetBlock.getType() != Material.DECORATED_POT) {
                restoreSidebar(player);
                continue;
            }

            eventManager.findEvent(targetBlock).ifPresentOrElse(
                    event -> showEventSidebar(player, event),
                    () -> restoreSidebar(player)
            );
        }
    }

    private void showEventSidebar(Player player, EventData event) {
        String eventId = event.getId();
        UUID playerId = player.getUniqueId();
        if (!previousScoreboards.containsKey(playerId)) {
            previousScoreboards.put(playerId, player.getScoreboard());
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("community_event", "dummy", ChatColor.GOLD.toString() + ChatColor.BOLD + "Community Event");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines;
        if (!eventManager.hasDiscoveredTarget(event)) {
            lines = List.of(
                    ChatColor.DARK_GRAY + " ",
                    ChatColor.YELLOW + "Ziel: " + ChatColor.WHITE + "???",
                    ChatColor.GRAY + "Fortschritt: " + ChatColor.WHITE + "???",
                    ChatColor.GRAY + " ",
                    ChatColor.WHITE + "Es muessen Items",
                    ChatColor.WHITE + "gesammelt werden,",
                    ChatColor.WHITE + "aber welches?",
                    ChatColor.WHITE + "Finde es heraus",
                    ChatColor.WHITE + "und probier es durch."
            );
        } else {
            int percent = (int) Math.floor(event.getProgress() * 100.0D);
            lines = List.of(
                    ChatColor.DARK_GRAY + " ",
                    ChatColor.YELLOW + "Ziel: " + ChatColor.WHITE + MaterialNames.forPlayer(player, event.getTargetMaterial()),
                    ChatColor.YELLOW + "Fuellung: " + ChatColor.WHITE + event.getCollectedAmount() + "/" + event.getRequiredAmount(),
                    ChatColor.YELLOW + "Fortschritt: " + ChatColor.WHITE + percent + "%",
                    ChatColor.GRAY + " ",
                    ChatColor.GOLD + "Ranking:",
                    rankingLine(event, 0),
                    rankingLine(event, 1),
                    rankingLine(event, 2)
            );
        }

        int score = lines.size();
        for (String line : lines) {
            objective.getScore(makeUnique(line, score)).setScore(score);
            score--;
        }

        player.setScoreboard(scoreboard);
        activeSidebarEvents.put(playerId, eventId);
    }

    private String rankingLine(EventData event, int index) {
        List<Map.Entry<UUID, Integer>> ranking = eventManager.getTopContributors(event, 3);
        if (index >= ranking.size()) {
            return ChatColor.GRAY + "-" + (index + 1) + ". ---";
        }

        Map.Entry<UUID, Integer> entry = ranking.get(index);
        String playerName = eventManager.getPlayerName(entry.getKey());
        return ChatColor.WHITE + "" + (index + 1) + ". " + playerName + ChatColor.GRAY + " - " + entry.getValue();
    }

    private String makeUnique(String line, int score) {
        return line + ChatColor.COLOR_CHAR + Integer.toHexString(score);
    }

    private void restoreSidebar(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard previous = previousScoreboards.remove(playerId);
        activeSidebarEvents.remove(playerId);
        if (previous != null) {
            player.setScoreboard(previous);
        }
    }
}
