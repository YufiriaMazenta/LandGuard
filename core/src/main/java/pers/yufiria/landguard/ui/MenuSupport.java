package pers.yufiria.landguard.ui;

import crypticlib.chat.BukkitTextProcessor;
import crypticlib.lang.entry.StringLangEntry;
import crypticlib.ui.display.Icon;
import crypticlib.ui.display.IconDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.Languages;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.ProtectionFlag;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GUI 公共工具：语言文本替换、通用图标、玩家名/脚下领地解析、flag 展示元数据。
 */
final class MenuSupport {

    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .localizedBy(Locale.getDefault())
        .withZone(ZoneId.systemDefault());

    /** 行为类 flag 的固定展示顺序与图标材质；自然类见 {@link #NATURAL_MATERIALS}。 */
    static final Map<ProtectionFlag, Material> BEHAVIOR_MATERIALS = new LinkedHashMap<>();
    static final Map<ProtectionFlag, Material> NATURAL_MATERIALS = new LinkedHashMap<>();

    static {
        BEHAVIOR_MATERIALS.put(BuiltinFlags.PLACE, Material.GRASS_BLOCK);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.BREAK, Material.IRON_PICKAXE);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.CONTAINER, Material.CHEST);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.DOOR, Material.OAK_DOOR);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.REDSTONE, Material.REDSTONE);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.CRAFTING, Material.CRAFTING_TABLE);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.VEHICLE, Material.MINECART);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.ANIMAL, Material.GOLDEN_CARROT);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.DISPLAY, Material.ITEM_FRAME);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.PLANTING, Material.WHEAT_SEEDS);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.HARVEST, Material.WHEAT);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.ITEM, Material.HOPPER);
        BEHAVIOR_MATERIALS.put(BuiltinFlags.BANK, Material.GOLD_INGOT);

        NATURAL_MATERIALS.put(BuiltinFlags.PVP, Material.IRON_SWORD);
        NATURAL_MATERIALS.put(BuiltinFlags.EXPLOSION, Material.TNT);
        NATURAL_MATERIALS.put(BuiltinFlags.FIRE_SPREAD, Material.FLINT_AND_STEEL);
        NATURAL_MATERIALS.put(BuiltinFlags.FLUID_FLOW, Material.WATER_BUCKET);
        NATURAL_MATERIALS.put(BuiltinFlags.PISTON, Material.PISTON);
        NATURAL_MATERIALS.put(BuiltinFlags.MOB_SPAWN, Material.SPAWNER);
        NATURAL_MATERIALS.put(BuiltinFlags.MOB_GRIEF, Material.CREEPER_HEAD);
        NATURAL_MATERIALS.put(BuiltinFlags.TRAMPLE, Material.LEATHER_BOOTS);
    }

    private MenuSupport() {
    }

    static String text(@Nullable Player player, @NotNull StringLangEntry entry, @NotNull Map<String, String> replacements) {
        String raw = player == null ? entry.value() : entry.value(player);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            raw = raw.replace(replacement.getKey(), replacement.getValue());
        }
        return BukkitTextProcessor.color(raw);
    }

    static String text(@Nullable Player player, @NotNull StringLangEntry entry) {
        return text(player, entry, Map.of());
    }

    static Icon icon(Material material, @Nullable String name, @Nullable List<String> lore) {
        return new Icon(new IconDisplay(material).setName(name).setLore(lore == null ? null : new ArrayList<>(lore)));
    }

    static Icon glass() {
        return icon(Material.GRAY_STAINED_GLASS_PANE, " ", null);
    }

    static Icon backIcon(Player player, Runnable onClick) {
        Icon icon = icon(Material.ARROW, text(player, Languages.MENU_COMMON_BACK), null);
        icon.setClickAction(event -> onClick.run());
        return icon;
    }

    static Icon arrow(Player player, boolean next, boolean enabled, Runnable onClick) {
        Material material = enabled ? (next ? Material.SPECTRAL_ARROW : Material.ARROW) : Material.GRAY_DYE;
        String name = text(player, next ? Languages.MENU_COMMON_NEXT : Languages.MENU_COMMON_PREV);
        Icon icon = icon(material, enabled ? name : "&8" + stripColor(name), null);
        if (enabled) {
            icon.setClickAction(event -> onClick.run());
        }
        return icon;
    }

    static String stripColor(String value) {
        return value.replaceAll("(?i)&[0-9A-FK-OR]", "");
    }

    static String money(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * 玩家当前脚下区块所属领地 ID；无领地返回 null。
     */
    static @Nullable String standingClaimId(Player player) {
        ChunkLoc standing = ChunkLoc.of(
            player.getWorld().getUID(),
            player.getLocation().getBlockX() >> 4,
            player.getLocation().getBlockZ() >> 4
        );
        return DataStore.INSTANCE.snapshot().claimIdByChunk().get(standing);
    }

    static @Nullable ClaimData claim(@NotNull DataSnapshot snapshot, @NotNull String claimId) {
        return snapshot.claimsById().get(claimId);
    }

    /**
     * 成员展示名：在线用实时名；离线只取本地用户缓存，避免阻塞式网络查询；取不到用 UUID 前缀。
     */
    static String displayName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(uuid.toString());
        if (cached != null && cached.getName() != null) {
            return cached.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    static String createdDate(ClaimData claim) {
        return DATE_FORMAT.format(Instant.ofEpochMilli(claim.getCreatedAt()));
    }

}
