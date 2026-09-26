package pers.yufiria.landguard.protection.listener;

import crypticlib.listener.EventListener;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.ProtectionFlag;
import pers.yufiria.landguard.protection.ProtectionQueries;

/**
 * 行为类实体交互 + 自然类怪物生成/改变方块。
 */
@EventListener
public enum EntityProtectionListener implements Listener {

    INSTANCE;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || victim instanceof Player) {
            if (event.getEntity() instanceof Player player) {
                if (!behaviorAllowed(playerDamager(event), player.getLocation(), BuiltinFlags.PVP)) {
                    event.setCancelled(true);
                }
            }
            return;
        }
        Player damager = playerDamager(event);
        if (damager == null) {
            return;
        }
        ProtectionFlag flag;
        Entity target = event.getEntity();
        if (target instanceof ItemFrame || target instanceof Painting || target instanceof ArmorStand) {
            flag = BuiltinFlags.DISPLAY;
        } else if (target instanceof Vehicle) {
            flag = BuiltinFlags.VEHICLE;
        } else if (target instanceof Animals || target instanceof Villager) {
            flag = BuiltinFlags.ANIMAL;
        } else {
            return;
        }
        if (!ProtectionEvents.allowed(damager, victim.getLocation().getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity target = event.getRightClicked();
        ProtectionFlag flag;
        if (target instanceof ItemFrame || target instanceof Painting || target instanceof ArmorStand) {
            flag = BuiltinFlags.DISPLAY;
        } else if (target instanceof Vehicle) {
            flag = BuiltinFlags.VEHICLE;
        } else if (target instanceof Animals || target instanceof Villager) {
            flag = BuiltinFlags.ANIMAL;
        } else {
            return;
        }
        if (!ProtectionEvents.allowed(event.getPlayer(), target.getLocation().getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = damagerPlayer(event.getRemover());
        if (remover == null) {
            return;
        }
        if (!ProtectionEvents.allowed(remover, event.getEntity().getLocation().getBlock(), BuiltinFlags.DISPLAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Block block = event.getItem().getLocation().getBlock();
        if (!ProtectionEvents.allowed(player, block, BuiltinFlags.ITEM)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        if (!ProtectionEvents.allowed(event.getPlayer(), item.getLocation().getBlock(), BuiltinFlags.ITEM)) {
            event.setCancelled(true);
        }
    }

    // ---------------- 自然类 ----------------

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Monster)) {
            return;
        }
        Location location = event.getLocation();
        if (!natural(location.getBlock(), BuiltinFlags.MOB_SPAWN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // 末影人搬方块、兔子啃胡萝卜、劫掠兽毁叶等
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (!natural(event.getBlock(), BuiltinFlags.MOB_GRIEF)) {
            event.setCancelled(true);
        }
    }

    private boolean natural(Block block, ProtectionFlag flag) {
        return ProtectionQueries
            .queryNatural(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4, flag).allowed();
    }

    private Player playerDamager(EntityDamageByEntityEvent event) {
        return damagerPlayer(event.getDamager());
    }

    private Player damagerPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean behaviorAllowed(Player player, Location location, ProtectionFlag flag) {
        if (player == null || location == null) {
            return true;
        }
        return ProtectionEvents.allowed(player, location.getBlock(), flag);
    }

}
