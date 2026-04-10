package mcbesser.communityevent;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MaterialNames {
    private static final Map<Material, String> GERMAN_NAMES = Map.ofEntries(
            Map.entry(Material.STONE, "Stein"),
            Map.entry(Material.COBBLESTONE, "Bruchstein"),
            Map.entry(Material.DIORITE, "Diorit"),
            Map.entry(Material.ANDESITE, "Andesit"),
            Map.entry(Material.GRANITE, "Granit"),
            Map.entry(Material.OAK_LOG, "Eichenstamm"),
            Map.entry(Material.SPRUCE_LOG, "Fichtenstamm"),
            Map.entry(Material.BIRCH_LOG, "Birkenstamm"),
            Map.entry(Material.JUNGLE_LOG, "Tropenholzstamm"),
            Map.entry(Material.DARK_OAK_LOG, "Schwarzeichenstamm"),
            Map.entry(Material.CHERRY_LOG, "Kirschstamm"),
            Map.entry(Material.PALE_OAK_LOG, "Blasseichenstamm"),
            Map.entry(Material.OAK_PLANKS, "Eichenholzbretter"),
            Map.entry(Material.SPRUCE_PLANKS, "Fichtenholzbretter"),
            Map.entry(Material.BIRCH_PLANKS, "Birkenholzbretter"),
            Map.entry(Material.JUNGLE_PLANKS, "Tropenholzbretter"),
            Map.entry(Material.DARK_OAK_PLANKS, "Schwarzeichenholzbretter"),
            Map.entry(Material.CHERRY_PLANKS, "Kirschholzbretter"),
            Map.entry(Material.PALE_OAK_PLANKS, "Blasseichenholzbretter"),
            Map.entry(Material.WHEAT_SEEDS, "Weizensamen"),
            Map.entry(Material.BEETROOT_SEEDS, "Rote-Bete-Samen"),
            Map.entry(Material.MELON_SEEDS, "Melonensamen"),
            Map.entry(Material.PUMPKIN_SEEDS, "K\u00fcrbissamen"),
            Map.entry(Material.COCOA_BEANS, "Kakaobohnen"),
            Map.entry(Material.WHEAT, "Weizen"),
            Map.entry(Material.CARROT, "Karotte"),
            Map.entry(Material.POTATO, "Kartoffel"),
            Map.entry(Material.BEETROOT, "Rote Bete"),
            Map.entry(Material.MELON_SLICE, "Melonenscheibe"),
            Map.entry(Material.MELON, "Melonenblock"),
            Map.entry(Material.PUMPKIN, "K\u00fcrbis")
    );

    private MaterialNames() {
    }

    public static String forPlayer(Player player, Material material) {
        return GERMAN_NAMES.getOrDefault(material, fallback(material));
    }

    private static String fallback(Material material) {
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
