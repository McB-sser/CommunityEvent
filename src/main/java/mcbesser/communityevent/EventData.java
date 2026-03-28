package mcbesser.communityevent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class EventData {
    private final String id;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private Material targetMaterial;
    private int requiredAmount;
    private int collectedAmount;
    private boolean completed;
    private final Set<UUID> participants;
    private final Map<UUID, Integer> contributions;

    public EventData(String id, String worldName, int x, int y, int z, Material targetMaterial, int requiredAmount) {
        this.id = id;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.targetMaterial = targetMaterial;
        this.requiredAmount = requiredAmount;
        this.collectedAmount = 0;
        this.completed = false;
        this.participants = new HashSet<>();
        this.contributions = new HashMap<>();
    }

    public static EventData fromConfig(String id, ConfigurationSection section) {
        EventData data = new EventData(
                id,
                section.getString("world"),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                Material.matchMaterial(section.getString("target", Material.COBBLESTONE.name())),
                section.getInt("required", 1)
        );
        data.collectedAmount = section.getInt("collected", 0);
        data.completed = section.getBoolean("completed", false);
        for (String raw : section.getStringList("participants")) {
            try {
                data.participants.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection contributionSection = section.getConfigurationSection("contributions");
        if (contributionSection != null) {
            for (String key : contributionSection.getKeys(false)) {
                try {
                    data.contributions.put(UUID.fromString(key), contributionSection.getInt(key, 0));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return data;
    }

    public void save(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("target", targetMaterial.name());
        section.set("required", requiredAmount);
        section.set("collected", collectedAmount);
        section.set("completed", completed);
        section.set("participants", participants.stream().map(UUID::toString).toList());
        section.set("contributions", null);
        ConfigurationSection contributionSection = section.createSection("contributions");
        for (Map.Entry<UUID, Integer> entry : contributions.entrySet()) {
            contributionSection.set(entry.getKey().toString(), entry.getValue());
        }
    }

    public String getId() {
        return id;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getPotLocation() {
        if (Bukkit.getWorld(worldName) == null) {
            return null;
        }
        return new Location(Bukkit.getWorld(worldName), x, y, z);
    }

    public Location getAbovePotLocation() {
        Location location = getPotLocation();
        return location == null ? null : location.clone().add(0, 1, 0);
    }

    public Material getTargetMaterial() {
        return targetMaterial;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getCollectedAmount() {
        return collectedAmount;
    }

    public int getRemainingAmount() {
        return Math.max(0, requiredAmount - collectedAmount);
    }

    public boolean isCompleted() {
        return completed;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public void addParticipant(UUID uuid) {
        participants.add(uuid);
    }

    public Map<UUID, Integer> getContributions() {
        return contributions;
    }

    public int getContribution(UUID uuid) {
        return contributions.getOrDefault(uuid, 0);
    }

    public int addProgress(int amount) {
        int accepted = Math.min(amount, getRemainingAmount());
        collectedAmount += accepted;
        if (collectedAmount >= requiredAmount) {
            collectedAmount = requiredAmount;
            completed = true;
        }
        return accepted;
    }

    public void addContribution(UUID uuid, int amount) {
        if (amount <= 0) {
            return;
        }
        contributions.merge(uuid, amount, Integer::sum);
    }

    public double getProgress() {
        if (requiredAmount <= 0) {
            return 0.0D;
        }
        return (double) collectedAmount / (double) requiredAmount;
    }
}
