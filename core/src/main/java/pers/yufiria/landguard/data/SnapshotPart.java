package pers.yufiria.landguard.data;

import java.util.EnumSet;

/**
 * 快照组件，与 {@link pers.yufiria.landguard.database.dao.LandDaoManager} 的表一一对应。
 * 写线程内的 mutation 落库后通过 {@link DataStore#reload} 声明本次真正改动的组件，
 * 未声明的组件按引用复用，避免每次写都全量重读。
 * 注意 {@code claimsByOwner} 不是组件：它是 {@code claimsById} 的派生索引，加载 {@link #CLAIM} 时一并重算。
 */
public enum SnapshotPart {

    /** lg_claim */
    CLAIM,
    /** lg_claim_chunk，同时驱动 claimIdByChunk / chunksByClaim 两个索引 */
    CLAIM_CHUNK,
    /** lg_claim_role_flag */
    ROLE_FLAG,
    /** lg_claim_setting */
    CLAIM_SETTING,
    /** lg_player_data */
    PLAYER,
    /** lg_group */
    GROUP,
    /** lg_group_member */
    GROUP_MEMBER;

    public static EnumSet<SnapshotPart> all() {
        return EnumSet.allOf(SnapshotPart.class);
    }

    public static EnumSet<SnapshotPart> of(SnapshotPart first, SnapshotPart... more) {
        return EnumSet.of(first, more);
    }

}