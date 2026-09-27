package pers.yufiria.landguard.data;

import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.owner.OwnerRef;

import java.util.*;

/**
 * 全量内存数据的不可变快照。
 * 所有集合保持插入顺序并禁止修改；DataStore 通过整体替换该对象实现无锁读。
 */
public record DataSnapshot(
    Map<String, ClaimData> claimsById,
    Map<ChunkLoc, String> claimIdByChunk,
    Map<String, Set<ChunkLoc>> chunksByClaim,
    Map<OwnerRef, Set<String>> claimsByOwner,
    Map<String, Map<String, Map<String, Boolean>>> roleFlagsByClaim,
    Map<String, Map<String, String>> settingsByClaim,
    Map<UUID, PlayerData> players,
    Map<String, GroupData> groups,
    Map<String, Map<UUID, String>> groupMembers
) {

    public DataSnapshot {
        claimsById = Collections.unmodifiableMap(new LinkedHashMap<>(claimsById));
        claimIdByChunk = Collections.unmodifiableMap(new LinkedHashMap<>(claimIdByChunk));
        Map<String, Set<ChunkLoc>> immutableChunks = new LinkedHashMap<>();
        chunksByClaim.forEach((id, set) ->
            immutableChunks.put(id, Collections.unmodifiableSet(new LinkedHashSet<>(set))));
        chunksByClaim = Collections.unmodifiableMap(immutableChunks);
        Map<OwnerRef, Set<String>> immutableOwnerClaims = new LinkedHashMap<>();
        claimsByOwner.forEach((owner, ids) ->
            immutableOwnerClaims.put(owner, Collections.unmodifiableSet(new LinkedHashSet<>(ids))));
        claimsByOwner = Collections.unmodifiableMap(immutableOwnerClaims);
        roleFlagsByClaim = deepImmutable(roleFlagsByClaim);
        Map<String, Map<String, String>> immutableSettings = new LinkedHashMap<>();
        settingsByClaim.forEach((id, map) -> immutableSettings.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(map))));
        settingsByClaim = Collections.unmodifiableMap(immutableSettings);
        players = Collections.unmodifiableMap(new LinkedHashMap<>(players));
        groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        Map<String, Map<UUID, String>> immutableMembers = new LinkedHashMap<>();
        groupMembers.forEach((id, map) -> immutableMembers.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(map))));
        groupMembers = Collections.unmodifiableMap(immutableMembers);
    }

    public static DataSnapshot empty() {
        return new DataSnapshot(
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>(),
            new LinkedHashMap<>()
        );
    }

    private static Map<String, Map<String, Map<String, Boolean>>> deepImmutable(
        Map<String, Map<String, Map<String, Boolean>>> source
    ) {
        Map<String, Map<String, Map<String, Boolean>>> result = new LinkedHashMap<>();
        source.forEach((claimId, byRole) -> {
            Map<String, Map<String, Boolean>> roleMap = new LinkedHashMap<>();
            byRole.forEach((roleId, flags) ->
                roleMap.put(roleId, Collections.unmodifiableMap(new LinkedHashMap<>(flags))));
            result.put(claimId, Collections.unmodifiableMap(roleMap));
        });
        return Collections.unmodifiableMap(result);
    }

    public @Nullable ClaimData claimAt(UUID worldUuid, int chunkX, int chunkZ) {
        String claimId = claimIdByChunk.get(ChunkLoc.of(worldUuid, chunkX, chunkZ));
        return claimId == null ? null : claimsById.get(claimId);
    }

    /** 替换领地索引与它的派生索引，其余组件沿用本快照。 */
    public DataSnapshot withClaims(Map<String, ClaimData> claimsById, Map<OwnerRef, Set<String>> claimsByOwner) {
        return new DataSnapshot(claimsById, claimIdByChunk, chunksByClaim, claimsByOwner,
            roleFlagsByClaim, settingsByClaim, players, groups, groupMembers);
    }

    /** 替换区块归属的两个索引，其余组件沿用本快照。 */
    public DataSnapshot withChunkIndex(Map<ChunkLoc, String> claimIdByChunk, Map<String, Set<ChunkLoc>> chunksByClaim) {
        return new DataSnapshot(claimsById, claimIdByChunk, chunksByClaim, claimsByOwner,
            roleFlagsByClaim, settingsByClaim, players, groups, groupMembers);
    }

    /** 替换按领地的 flag 覆盖表，其余组件沿用本快照。 */
    public DataSnapshot withRoleFlags(Map<String, Map<String, Map<String, Boolean>>> roleFlagsByClaim) {
        return new DataSnapshot(claimsById, claimIdByChunk, chunksByClaim, claimsByOwner,
            roleFlagsByClaim, settingsByClaim, players, groups, groupMembers);
    }

    /** 替换按领地的设置表，其余组件沿用本快照。 */
    public DataSnapshot withSettings(Map<String, Map<String, String>> settingsByClaim) {
        return new DataSnapshot(claimsById, claimIdByChunk, chunksByClaim, claimsByOwner,
            roleFlagsByClaim, settingsByClaim, players, groups, groupMembers);
    }

}
