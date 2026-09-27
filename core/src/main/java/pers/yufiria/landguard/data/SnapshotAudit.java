package pers.yufiria.landguard.data;

import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.PlayerData;

import java.sql.SQLException;
import java.util.*;

/**
 * 快照新鲜度审计：把已发布的快照与「全量重读的结果」逐组件按值比对。
 * 增量加载（{@link DataStore#reload}）漏声明组件时会静默陈旧，本类用于在测试与排查中兜住这种疏漏。
 * 实体类没有实现 equals，因此这里比对的是各字段的投影。
 */
public final class SnapshotAudit {

    private SnapshotAudit() {
    }

    /** 与当前数据库的全量读取结果比对，不一致抛 {@link AssertionError}。 */
    public static void assertFresh(DataSnapshot published) throws SQLException {
        assertFresh(published, DataStore.SnapshotLoader.load(null));
    }

    private static void assertFresh(DataSnapshot published, DataSnapshot expected) {
        compare("claimsById", claimFields(published.claimsById()), claimFields(expected.claimsById()));
        compare("claimIdByChunk", published.claimIdByChunk(), expected.claimIdByChunk());
        compare("chunksByClaim", published.chunksByClaim(), expected.chunksByClaim());
        compare("claimsByOwner", published.claimsByOwner(), expected.claimsByOwner());
        compare("roleFlagsByClaim", published.roleFlagsByClaim(), expected.roleFlagsByClaim());
        compare("settingsByClaim", published.settingsByClaim(), expected.settingsByClaim());
        compare("players", playerFields(published.players()), playerFields(expected.players()));
        compare("groups", groupFields(published.groups()), groupFields(expected.groups()));
        compare("groupMembers", published.groupMembers(), expected.groupMembers());
    }

    private static <K, V> void compare(String component, Map<K, V> actual, Map<K, V> expected) {
        if (actual.equals(expected)) {
            return;
        }
        Set<K> missing = new LinkedHashSet<>(expected.keySet());
        missing.removeAll(actual.keySet());
        Set<K> extra = new LinkedHashSet<>(actual.keySet());
        extra.removeAll(expected.keySet());
        K firstDiff = null;
        for (K key : expected.keySet()) {
            if (actual.containsKey(key) && !Objects.equals(expected.get(key), actual.get(key))) {
                firstDiff = key;
                break;
            }
        }
        throw new AssertionError("快照组件 " + component + " 与全量重读不一致：缺少 " + missing + "，多出 " + extra
            + "，首个值不同的键 " + firstDiff
            + "（期望 " + (firstDiff == null ? "-" : expected.get(firstDiff))
            + "，实际 " + (firstDiff == null ? "-" : actual.get(firstDiff)) + "）");
    }

    private static Map<String, List<Object>> claimFields(Map<String, ClaimData> source) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        source.forEach((claimId, claim) -> result.put(claimId, Arrays.asList(
            claim.getWorldUuid(), claim.getOwnerType(), claim.getOwnerId(), claim.getName(), claim.isAdmin(),
            claim.getCreatedAt(), claim.getLastActiveAt(), claim.getBankBalance(), claim.isUpkeepExempt(),
            claim.getUpkeepChargedAt(), claim.getUpkeepUnpaidSince(), claim.getInactiveWarnedAt(),
            claim.getOrphanSince())));
        return result;
    }

    private static Map<UUID, List<Object>> playerFields(Map<UUID, PlayerData> source) {
        Map<UUID, List<Object>> result = new LinkedHashMap<>();
        source.forEach((uuid, player) -> result.put(uuid, Arrays.asList(
            player.getAccruedChunks(), player.getBoughtChunks(), player.getLastLogin())));
        return result;
    }

    private static Map<String, List<Object>> groupFields(Map<String, GroupData> source) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        source.forEach((groupId, group) -> result.put(groupId, Arrays.asList(
            group.getName(), group.getLeaderUuid(), group.getCreatedAt(), group.getBankBalance())));
        return result;
    }

}