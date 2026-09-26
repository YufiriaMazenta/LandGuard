package pers.yufiria.landguard.protection.listener;

import crypticlib.listener.EventListener;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.protection.BlockPoint;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.CrossBoundaryRules;
import pers.yufiria.landguard.protection.ProtectionQueries;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然类：爆炸、火焰蔓延、流体跨界、活塞跨界、作物踩踏。
 * 监听内只读快照与纯规则，取消即预防，不写日志/回滚（FR-5.5）。
 */
@EventListener
public enum NaturalProtectionListener implements Listener {

    INSTANCE;

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        List<BlockPoint> points = new ArrayList<>();
        for (Block block : event.blockList()) {
            points.add(ProtectionEvents.point(block));
        }
        List<BlockPoint> protectedBlocks = CrossBoundaryRules.protectedExplosionBlocks(
            DataStore.INSTANCE.snapshot(), points);
        if (protectedBlocks.isEmpty()) {
            return;
        }
        // 直接从破坏列表移除受保护方块（其余爆炸效果照常）
        event.blockList().removeIf(block -> {
            BlockPoint point = ProtectionEvents.point(block);
            return protectedBlocks.contains(point);
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFireSpread(BlockSpreadEvent event) {
        if (!CrossBoundaryRules.fireAllowed(DataStore.INSTANCE.snapshot(), ProtectionEvents.point(event.getNewState().getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (!CrossBoundaryRules.fireAllowed(DataStore.INSTANCE.snapshot(), ProtectionEvents.point(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        // 仅拦自然成因（熔岩/火焰蔓延），玩家用火属行为类范畴且不受自然 flag 控制
        if (event.getCause() != BlockIgniteEvent.IgniteCause.LAVA
            && event.getCause() != BlockIgniteEvent.IgniteCause.SPREAD) {
            return;
        }
        if (!CrossBoundaryRules.fireAllowed(DataStore.INSTANCE.snapshot(), ProtectionEvents.point(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (!CrossBoundaryRules.fluidAllowed(
            DataStore.INSTANCE.snapshot(),
            ProtectionEvents.point(event.getBlock()),
            ProtectionEvents.point(to))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        BlockFace face = event.getDirection();
        List<BlockPoint> moves = toPoints(event.getBlocks());
        if (!CrossBoundaryRules.deniedPistonMoves(
            DataStore.INSTANCE.snapshot(), moves, face.getModX(), face.getModY(), face.getModZ()).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().isEmpty()) {
            return;
        }
        // 收回：方块沿推出方向的反方向回移一格
        BlockFace face = event.getDirection().getOppositeFace();
        List<BlockPoint> moves = toPoints(event.getBlocks());
        if (!CrossBoundaryRules.deniedPistonMoves(
            DataStore.INSTANCE.snapshot(), moves, face.getModX(), face.getModY(), face.getModZ()).isEmpty()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTrample(EntityInteractEvent event) {
        // 仅实体对农田的踩踏（作物破坏）；玩家踩踏同样经过此事件
        Block block = event.getBlock();
        if (!ProtectionQueries
            .queryNatural(block.getWorld().getUID(), block.getX() >> 4, block.getZ() >> 4, BuiltinFlags.TRAMPLE)
            .allowed()) {
            event.setCancelled(true);
        }
    }

    private List<BlockPoint> toPoints(List<Block> blocks) {
        List<BlockPoint> points = new ArrayList<>(blocks.size());
        for (Block block : blocks) {
            points.add(ProtectionEvents.point(block));
        }
        return points;
    }

}
