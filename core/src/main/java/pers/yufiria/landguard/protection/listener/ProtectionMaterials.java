package pers.yufiria.landguard.protection.listener;

import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.EnumSet;
import java.util.Set;

/**
 * 方块/物品 → flag 的材料映射。以 1.20+ Tag 为主，名称集合兜底跨版本差异（缺失名称自动跳过）。
 */
final class ProtectionMaterials {

    private static final Set<Material> CROPS = named(
        "WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "SWEET_BERRY_BUSH",
        "SUGAR_CANE", "KELP", "KELP_PLANT", "BAMBOO", "BAMBOO_SAPLING", "CACTUS",
        "MELON", "PUMPKIN", "MELON_STEM", "PUMPKIN_STEM",
        "ATTACHED_MELON_STEM", "ATTACHED_PUMPKIN_STEM",
        "TORCHFLOWER", "TORCHFLOWER_CROP", "PITCHER_PLANT", "PITCHER_CROP",
        "CAVE_VINES", "CAVE_VINES_PLANT"
    );

    private static final Set<Material> CONTAINERS = named(
        "CHEST", "TRAPPED_CHEST", "ENDER_CHEST", "BARREL", "HOPPER", "DISPENSER", "DROPPER",
        "FURNACE", "BLAST_FURNACE", "SMOKER", "BREWING_STAND",
        "SHULKER_BOX", "WHITE_SHULKER_BOX", "ORANGE_SHULKER_BOX", "MAGENTA_SHULKER_BOX",
        "LIGHT_BLUE_SHULKER_BOX", "YELLOW_SHULKER_BOX", "LIME_SHULKER_BOX", "PINK_SHULKER_BOX",
        "GRAY_SHULKER_BOX", "LIGHT_GRAY_SHULKER_BOX", "CYAN_SHULKER_BOX", "PURPLE_SHULKER_BOX",
        "BLUE_SHULKER_BOX", "BROWN_SHULKER_BOX", "GREEN_SHULKER_BOX", "RED_SHULKER_BOX",
        "BLACK_SHULKER_BOX"
    );

    private static final Set<Material> CRAFTING = named(
        "CRAFTING_TABLE", "CARTOGRAPHY_TABLE", "STONECUTTER", "LOOM", "GRINDSTONE",
        "SMITHING_TABLE", "ANVIL", "CHIPPED_ANVIL", "DAMAGED_ANVIL", "ENCHANTING_TABLE"
    );

    private static final Set<Material> REDSTONE_EXTRA = named(
        "LEVER", "REPEATER", "COMPARATOR", "DAYLIGHT_DETECTOR",
        "LIGHT_WEIGHTED_PRESSURE_PLATE", "HEAVY_WEIGHTED_PRESSURE_PLATE",
        "TRIPWIRE_HOOK", "BEACON", "NOTE_BLOCK", "JUKEBOX"
    );

    private ProtectionMaterials() {
    }

    private static Set<Material> named(String... names) {
        EnumSet<Material> set = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                set.add(material);
            }
        }
        return set;
    }

    static boolean isCrop(Material material) {
        return CROPS.contains(material);
    }

    static boolean isContainer(Material material) {
        return CONTAINERS.contains(material);
    }

    static boolean isCrafting(Material material) {
        return CRAFTING.contains(material);
    }

    static boolean isDoorLike(Material material) {
        return Tag.DOORS.isTagged(material) || Tag.TRAPDOORS.isTagged(material) || Tag.FENCE_GATES.isTagged(material);
    }

    static boolean isRedstone(Material material) {
        return Tag.BUTTONS.isTagged(material)
            || Tag.WOODEN_PRESSURE_PLATES.isTagged(material)
            || REDSTONE_EXTRA.contains(material);
    }

    /**
     * 手持物品是否为种植投入物（种子/幼苗/骨粉）。
     */
    static boolean isPlantingItem(Material material) {
        return material == Material.BONE_MEAL
            || material.name().endsWith("_SEEDS")
            || material == Material.BAMBOO || material == Material.SUGAR_CANE
            || material == Material.KELP || material.name().equals("TORCHFLOWER_SEEDS")
            || material.name().equals("PITCHER_POD");
    }

}
