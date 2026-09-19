package pers.yufiria.landguard.claim;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.OwnerRef;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 纯函数认领规则引擎：只读快照、零 Bukkit/DAO 依赖，可直接单元测试。
 * 规则：
 * 1. 同区块至多属于一个领地（重叠拒绝）；
 * 2. 要求相邻时，新区块组必须（经区块组内部连通后）接上操作者在同世界已有领地；
 *    首个领地只要求区块组自身连通；
 * 3. 额度不足拒绝。
 */
public final class ClaimEngine {

    private ClaimEngine() {
    }

    /**
     * 归一化目标区块：去重、校验非空与同世界。非法返回 null。
     */
    public static @Nullable List<ChunkLoc> normalizeTargets(UUID world, Collection<ChunkLoc> rawTargets) {
        if (rawTargets == null || rawTargets.isEmpty()) {
            return null;
        }
        Set<ChunkLoc> dedup = new LinkedHashSet<>(rawTargets);
        for (ChunkLoc loc : dedup) {
            if (!loc.worldUuid().equals(world)) {
                return null;
            }
        }
        return new ArrayList<>(dedup);
    }

    /**
     * 校验认领。通过返回 null，否则返回失败原因。
     *
     * @param ownedChunks     操作者在目标世界已拥有的全部区块
     * @param capacityChunks  可用区块额度（Long.MAX_VALUE 表示不限）
     * @param usedChunks      已消耗额度（含历史未返还部分）
     */
    public static @Nullable ClaimFailureReason validateClaim(
        DataSnapshot snapshot,
        Collection<ChunkLoc> targets,
        Set<ChunkLoc> ownedChunks,
        boolean requireAdjacent,
        boolean allowDiagonal,
        long capacityChunks,
        long usedChunks
    ) {
        if (targets.isEmpty()) {
            return ClaimFailureReason.INVALID_TARGETS;
        }
        for (ChunkLoc target : targets) {
            if (snapshot.claimIdByChunk().containsKey(target)) {
                return ClaimFailureReason.OVERLAP;
            }
        }
        if (ownedChunks.isEmpty()) {
            // 首个领地：批次自身必须连通（单个区块天然连通）
            if (!internallyConnected(targets, allowDiagonal)) {
                return ClaimFailureReason.INVALID_TARGETS;
            }
        } else if (requireAdjacent && !connectedToTerritory(targets, ownedChunks, allowDiagonal)) {
            return ClaimFailureReason.NOT_ADJACENT;
        }
        if (capacityChunks != Long.MAX_VALUE && usedChunks + targets.size() > capacityChunks) {
            return ClaimFailureReason.QUOTA_EXCEEDED;
        }
        return null;
    }

    /**
     * 目标区块组是否（经组内连通）接到已有领地。
     */
    static boolean connectedToTerritory(Collection<ChunkLoc> targets, Set<ChunkLoc> owned, boolean allowDiagonal) {
        Set<ChunkLoc> targetSet = targets instanceof Set ? (Set<ChunkLoc>) targets : new HashSet<>(targets);
        Set<ChunkLoc> seeds = new HashSet<>();
        for (ChunkLoc target : targetSet) {
            for (ChunkLoc neighbour : neighbours(target, allowDiagonal)) {
                if (owned.contains(neighbour)) {
                    seeds.add(target);
                    break;
                }
            }
        }
        if (seeds.isEmpty()) {
            return false;
        }
        Set<ChunkLoc> reached = flood(seeds, targetSet, allowDiagonal);
        return reached.size() == targetSet.size();
    }

    /**
     * 首个领地：区块组自身必须连通。
     */
    static boolean internallyConnected(Collection<ChunkLoc> targets, boolean allowDiagonal) {
        Set<ChunkLoc> targetSet = targets instanceof Set ? (Set<ChunkLoc>) targets : new HashSet<>(targets);
        Set<ChunkLoc> reached = flood(Set.of(targetSet.iterator().next()), targetSet, allowDiagonal);
        return reached.size() == targetSet.size();
    }

    private static Set<ChunkLoc> flood(Set<ChunkLoc> seeds, Set<ChunkLoc> passable, boolean allowDiagonal) {
        Set<ChunkLoc> reached = new HashSet<>(seeds);
        Deque<ChunkLoc> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            ChunkLoc current = queue.poll();
            for (ChunkLoc neighbour : neighbours(current, allowDiagonal)) {
                if (passable.contains(neighbour) && reached.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return reached;
    }

    public static boolean adjacent(@NotNull ChunkLoc a, @NotNull ChunkLoc b, boolean allowDiagonal) {
        if (!a.worldUuid().equals(b.worldUuid())) {
            return false;
        }
        int dx = Math.abs(a.x() - b.x());
        int dz = Math.abs(a.z() - b.z());
        if (allowDiagonal) {
            return dx <= 1 && dz <= 1 && (dx + dz) > 0;
        }
        return dx + dz == 1;
    }

    private static List<ChunkLoc> neighbours(ChunkLoc loc, boolean allowDiagonal) {
        List<ChunkLoc> result = new ArrayList<>(8);
        result.add(ChunkLoc.of(loc.worldUuid(), loc.x() + 1, loc.z()));
        result.add(ChunkLoc.of(loc.worldUuid(), loc.x() - 1, loc.z()));
        result.add(ChunkLoc.of(loc.worldUuid(), loc.x(), loc.z() + 1));
        result.add(ChunkLoc.of(loc.worldUuid(), loc.x(), loc.z() - 1));
        if (allowDiagonal) {
            result.add(ChunkLoc.of(loc.worldUuid(), loc.x() + 1, loc.z() + 1));
            result.add(ChunkLoc.of(loc.worldUuid(), loc.x() + 1, loc.z() - 1));
            result.add(ChunkLoc.of(loc.worldUuid(), loc.x() - 1, loc.z() + 1));
            result.add(ChunkLoc.of(loc.worldUuid(), loc.x() - 1, loc.z() - 1));
        }
        return result;
    }

    /**
     * 操作者在指定世界已拥有的全部区块。
     */
    public static Set<ChunkLoc> ownedChunksInWorld(DataSnapshot snapshot, OwnerRef owner, UUID world) {
        Set<ChunkLoc> result = new LinkedHashSet<>();
        for (String claimId : snapshot.claimsByOwner().getOrDefault(owner, Set.of())) {
            ClaimData claim = snapshot.claimsById().get(claimId);
            if (claim != null && claim.getWorldUuid().equals(world)) {
                result.addAll(snapshot.chunksByClaim().getOrDefault(claimId, Set.of()));
            }
        }
        return result;
    }

    /**
     * 操作者在所有世界已消耗的区块数（快照派生，不含历史未返还额度；历史部分在额度表中）。
     */
    public static int currentClaimedChunks(DataSnapshot snapshot, OwnerRef owner) {
        int total = 0;
        for (String claimId : snapshot.claimsByOwner().getOrDefault(owner, Set.of())) {
            total += snapshot.chunksByClaim().getOrDefault(claimId, Set.of()).size();
        }
        return total;
    }

    /**
     * 半径方形区块集合（radius=1 仅中心，2 为 3x3）。
     */
    public static List<ChunkLoc> radiusTargets(UUID world, int centerX, int centerZ, int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be >= 1");
        }
        int offset = radius - 1;
        List<ChunkLoc> targets = new ArrayList<>((offset * 2 + 1) * (offset * 2 + 1));
        for (int dx = -offset; dx <= offset; dx++) {
            for (int dz = -offset; dz <= offset; dz++) {
                targets.add(ChunkLoc.of(world, centerX + dx, centerZ + dz));
            }
        }
        return targets;
    }

}
