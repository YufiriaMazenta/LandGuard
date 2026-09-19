package pers.yufiria.landguard.protection.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import crypticlib.listener.EventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.ProtectionFlag;

/**
 * 行为类：放拆、容器、门、红石、工作台、种植/采收、桶流体，以及压力板触发。
 */
@EventListener
public enum BlockProtectionListener implements Listener {

    INSTANCE;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ProtectionFlag flag = ProtectionMaterials.isCrop(event.getBlock().getType())
            ? BuiltinFlags.HARVEST : BuiltinFlags.BREAK;
        if (!ProtectionEvents.allowed(event.getPlayer(), event.getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ProtectionFlag flag = ProtectionMaterials.isCrop(event.getBlock().getType())
            ? BuiltinFlags.PLANTING : BuiltinFlags.PLACE;
        if (!ProtectionEvents.allowed(event.getPlayer(), event.getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || event.getPlayer() == null) {
            return;
        }
        Material material = block.getType();
        ProtectionFlag flag;
        if (event.getAction() == Action.PHYSICAL) {
            // 压力板踩下/触碰
            flag = BuiltinFlags.REDSTONE;
        } else if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        } else if (ProtectionMaterials.isContainer(material)) {
            flag = BuiltinFlags.CONTAINER;
        } else if (ProtectionMaterials.isDoorLike(material)) {
            flag = BuiltinFlags.DOOR;
        } else if (ProtectionMaterials.isRedstone(material)) {
            flag = BuiltinFlags.REDSTONE;
        } else if (ProtectionMaterials.isCrafting(material)) {
            flag = BuiltinFlags.CRAFTING;
        } else if (isPlantingAttempt(event.getMaterial(), block)) {
            flag = BuiltinFlags.PLANTING;
        } else {
            return;
        }
        if (!ProtectionEvents.allowed(event.getPlayer(), block, flag)) {
            event.setCancelled(true);
        }
    }

    private boolean isPlantingAttempt(Material hand, Block clicked) {
        if (hand == null || !ProtectionMaterials.isPlantingItem(hand)) {
            return false;
        }
        Material clickedType = clicked.getType();
        return clickedType == Material.FARMLAND
            || clickedType == Material.GRASS_BLOCK
            || clickedType == Material.DIRT
            || clickedType == Material.PODZOL
            || clickedType == Material.ROOTED_DIRT
            || clickedType.isAir();
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        // getBlockClicked 为源相邻方块，流体落在 face 方向
        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!ProtectionEvents.allowed(event.getPlayer(), target, BuiltinFlags.PLACE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        // 被舀起的流体在点击面的相邻格
        Block liquid = event.getBlockClicked().getRelative(event.getBlockFace());
        if (!ProtectionEvents.allowed(event.getPlayer(), liquid, BuiltinFlags.BREAK)) {
            event.setCancelled(true);
        }
    }

}
