package pers.yufiria.landguard.group;

import crypticlib.database.dao.Dao;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.claim.PlayerQuotaLedger;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotPart;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupMemberData;
import pers.yufiria.landguard.identity.Identity;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.identity.IdentityRegistry;
import pers.yufiria.landguard.identity.PermissionPoint;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.util.ConfigValues;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 内置用户组领域服务（FR-6）。
 * 所有变更都经 {@link DataStore#mutate} 单写线程原子完成；成功后通过 SPI 发出成员失效通知，
 * 在线玩家无需重登即可获得/失去权限。邀请数据为内存态（重启清空，不建表）。
 * 组解散时不删除其名下领地——所有者变为不可解析，领地进入孤儿流程（Task 11 处理）。
 */
public enum GroupService {

    INSTANCE;

    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");
    private static final int MAX_GROUP_NAME_LENGTH = 32;

    /** groupId -> 被邀请玩家集合（内存态） */
    private final Map<String, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();

    /**
     * 组织数量上限解析器：默认不限制（无平台环境），
     * 生产环境由 {@link GroupLimitInitializer} 在启动/重载时注入权限实现。
     */
    private volatile GroupLimitResolver limitResolver = GroupLimitResolver.noLimits();

    // ================= 组生命周期 =================

    /** 启动期注入上限解析器；见 {@link GroupLimitInitializer}。 */
    public void setLimitResolver(@NotNull GroupLimitResolver resolver) {
        this.limitResolver = resolver;
    }

    /**
     * 建组：{@code groupId} 是玩家自选的短标识符（唯一、创建后不可改），
     * {@code name} 是可改可重名的展示名（省略时回退为标识符）。
     */
    public CompletableFuture<GroupOpResult> createGroup(UUID creator, String groupId, @Nullable String name) {
        String wantedId = normalizeGroupId(groupId);
        if (wantedId == null) {
            return failed(GroupFailureReason.INVALID_KEY);
        }
        String wantedName = normalizeName(name == null || name.isBlank() ? wantedId : name);
        if (wantedName == null) {
            return failed(GroupFailureReason.INVALID_NAME);
        }
        // 上限在进入写线程前解析：权限查询属于平台能力，只在调用方线程做一次
        int ownLimit = limitResolver.ownLimit(creator);
        int joinLimit = limitResolver.joinLimit(creator);
        return mutate(resultRef -> current -> {
            if (current.groups().containsKey(wantedId)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.KEY_TAKEN));
                return current;
            }
            // 建组同时占用一个「拥有」名额与一个「加入」名额（拥有视为已加入）
            if (limitReached(ownLimit, countOwned(current, creator))) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.OWN_LIMIT_EXCEEDED));
                return current;
            }
            if (limitReached(joinLimit, countJoined(current, creator))) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.JOIN_LIMIT_EXCEEDED));
                return current;
            }
            long now = System.currentTimeMillis();
            LandDaoManager daos = LandDaoManager.INSTANCE;
            daos.groupDao().create(new GroupData(wantedId, wantedName, creator, now, 0D));
            daos.groupMemberDao().create(new GroupMemberData(wantedId, creator, IdentityRegistry.INSTANCE.leaderIdentityId()));
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP, SnapshotPart.GROUP_MEMBER);
            notifyChanged(wantedId);
            resultRef.set(GroupOpResult.ok(wantedId));
            return next;
        });
    }

    /**
     * 修改用户组的展示名：仅领袖可操作。展示名可与其他组重名，标识符（groupId）不可改。
     */
    public CompletableFuture<GroupOpResult> renameGroup(UUID actor, String groupId, String newName) {
        String wantedName = normalizeName(newName);
        if (wantedName == null) {
            return failed(GroupFailureReason.INVALID_NAME);
        }
        return withGroup(actor, groupId, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_RENAME)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_LEADER));
                return current;
            }
            GroupData stored = LandDaoManager.INSTANCE.groupDao().queryForId(group.getGroupId());
            if (stored == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
                return current;
            }
            stored.setName(wantedName);
            LandDaoManager.INSTANCE.groupDao().update(stored);
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> disband(UUID actor, String groupName) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_DISBAND)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_LEADER));
                return current;
            }
            LandDaoManager daos = LandDaoManager.INSTANCE;
            deleteByGroup(daos.groupMemberDao(), group.getGroupId());
            daos.groupDao().delete(group);
            pendingInvites.remove(group.getGroupId());
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP, SnapshotPart.GROUP_MEMBER);
            // 名下领地不删除：提供者将无法解析该所有者 → 孤儿流程
            ClaimOwnerRegistry.INSTANCE.notifyOwnerRemoved(
                OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId()));
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    // ================= 邀请 / 成员 =================

    public CompletableFuture<GroupOpResult> invite(UUID actor, String groupName, UUID target) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_INVITE)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            if (roleOf(current, group.getGroupId(), target) != null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.ALREADY_MEMBER));
                return current;
            }
            pendingInvites
                .computeIfAbsent(group.getGroupId(), k -> ConcurrentHashMap.newKeySet())
                .add(target);
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return current;
        });
    }

    public CompletableFuture<GroupOpResult> acceptInvite(UUID player, String groupName) {
        return consumeInvite(player, groupName, true);
    }

    public CompletableFuture<GroupOpResult> denyInvite(UUID player, String groupName) {
        return consumeInvite(player, groupName, false);
    }

    private CompletableFuture<GroupOpResult> consumeInvite(UUID player, String groupId, boolean accept) {
        String wanted = normalizeGroupId(groupId);
        if (wanted == null) {
            return failed(GroupFailureReason.INVALID_KEY);
        }
        int joinLimit = accept ? limitResolver.joinLimit(player) : GroupLimitResolver.NO_LIMIT;
        return mutate(resultRef -> current -> {
            GroupData group = findById(current, wanted);
            if (group == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
                return current;
            }
            Set<UUID> invites = pendingInvites.get(group.getGroupId());
            if (invites == null || !invites.contains(player)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NO_INVITE));
                return current;
            }
            if (!accept) {
                invites.remove(player);
                resultRef.set(GroupOpResult.ok(group.getGroupId()));
                return current;
            }
            if (roleOf(current, group.getGroupId(), player) != null) {
                invites.remove(player);
                resultRef.set(GroupOpResult.failed(GroupFailureReason.ALREADY_MEMBER));
                return current;
            }
            // 上限判定失败时不消耗邀请：腾出名额后仍可再次 accept
            if (limitReached(joinLimit, countJoined(current, player))) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.JOIN_LIMIT_EXCEEDED));
                return current;
            }
            invites.remove(player);
            LandDaoManager.INSTANCE.groupMemberDao()
                .create(new GroupMemberData(group.getGroupId(), player, Roles.MEMBER));
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP_MEMBER);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> leave(UUID player, String groupName) {
        return withGroup(player, groupName, resultRef -> (current, group) -> {
            String role = roleOf(current, group.getGroupId(), player);
            if (role == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MEMBER));
                return current;
            }
            if (IdentityPermissions.isLeader(current, group.getGroupId(), player)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.LEADER_CANNOT_LEAVE));
                return current;
            }
            deleteMember(group.getGroupId(), player);
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP_MEMBER);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> kick(UUID actor, String groupName, UUID target) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_KICK)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            if (roleOf(current, group.getGroupId(), target) == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.TARGET_NOT_MEMBER));
                return current;
            }
            // 层级：只能踢优先级严格低于自己的成员；领袖与自己一律不可踢
            if (target.equals(actor) || !IdentityPermissions.outranks(current, group.getGroupId(), actor, target)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.CANNOT_KICK));
                return current;
            }
            deleteMember(group.getGroupId(), target);
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP_MEMBER);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> transferLeadership(UUID actor, String groupName, UUID target) {
        // 接收者将因此多拥有一个组织，需先按其权限解析上限（写线程内不再做平台查询）
        int targetOwnLimit = limitResolver.ownLimit(target);
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_TRANSFER)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_LEADER));
                return current;
            }
            if (roleOf(current, group.getGroupId(), target) == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.TARGET_NOT_MEMBER));
                return current;
            }
            if (target.equals(actor)) {
                resultRef.set(GroupOpResult.ok(group.getGroupId()));
                return current;
            }
            if (limitReached(targetOwnLimit, countOwned(current, target))) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.OWN_LIMIT_EXCEEDED));
                return current;
            }
            LandDaoManager daos = LandDaoManager.INSTANCE;
            // 快照中的 GroupData 不可改写：先取一份数据库副本再改，避免就地篡改已发布的旧快照
            GroupData storedLeader = daos.groupDao().queryForId(group.getGroupId());
            if (storedLeader == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
                return current;
            }
            storedLeader.setLeaderUuid(target);
            daos.groupDao().update(storedLeader);
            upsertMemberRole(daos, group.getGroupId(), target, IdentityRegistry.INSTANCE.leaderIdentityId());
            upsertMemberRole(daos, group.getGroupId(), actor, formerLeaderIdentityId());
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP, SnapshotPart.GROUP_MEMBER);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    // ================= 身份指派 =================

    public CompletableFuture<GroupOpResult> assignRole(UUID actor, String groupName,
                                                       UUID target, String roleId) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_ASSIGN)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            if (roleOf(current, group.getGroupId(), target) == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.TARGET_NOT_MEMBER));
                return current;
            }
            boolean roleKnown = IdentityRegistry.INSTANCE.isRegistered(roleId);
            if (!roleKnown) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.IDENTITY_NOT_FOUND));
                return current;
            }
            // 不能指派领袖身份（只能经转让领袖获得）；不能改自己；只能指派低于自己优先级的身份，
            // 且只能改动优先级不高于自己的成员
            if (IdentityPermissions.isLeaderIdentity(roleId) || target.equals(actor)
                || !(IdentityPermissions.identityOf(current, group.getGroupId(), actor).priority()
                    > IdentityRegistry.INSTANCE.resolve(roleId).priority())
                || !(IdentityPermissions.identityOf(current, group.getGroupId(), actor).priority()
                    >= IdentityPermissions.identityOf(current, group.getGroupId(), target).priority())) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.CANNOT_ASSIGN));
                return current;
            }
            upsertMemberRole(LandDaoManager.INSTANCE, group.getGroupId(), target, roleId);
            DataSnapshot next = DataStore.reload(SnapshotPart.GROUP_MEMBER);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    // ================= 领地转让给组 =================

    /**
     * 把玩家脚下（指定坐标所属）的个人领地整领转让给用户组；区块、设置、flag 全部保留。
     * 操作者必须是该领地的个人所有者，且在目标组中担任领袖/管理者。
     */
    public CompletableFuture<GroupOpResult> giveClaim(UUID actor, String groupName,
                                                      UUID worldUuid, int chunkX, int chunkZ) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!IdentityPermissions.has(current, group.getGroupId(), actor, PermissionPoint.GROUP_GIVE_CLAIM)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            String claimId = current.claimIdByChunk()
                .get(ChunkLoc.of(worldUuid, chunkX, chunkZ));
            ClaimData claim = claimId == null ? null : current.claimsById().get(claimId);
            if (claim == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.CLAIM_NOT_FOUND));
                return current;
            }
            if (!BuiltinOwnerTypes.PLAYER.equals(claim.getOwnerType())
                || !claim.getOwnerId().equals(actor.toString())) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_CLAIM_OWNER));
                return current;
            }
            // 目标组在该世界不能已有领地：否则会出现「同一所有者同世界多块地」，
            // 而 ClaimService.findClaimInWorld 只返回第一块，导致后续扩容目标不确定
            Set<String> groupClaims = current.claimsByOwner()
                .getOrDefault(OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId()), Set.of());
            for (String otherId : groupClaims) {
                ClaimData other = current.claimsById().get(otherId);
                if (other != null && worldUuid.equals(other.getWorldUuid())) {
                    resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_HAS_CLAIM));
                    return current;
                }
            }
            // 组额度：组已用区块（快照派生）+ 本领地区块数不得超过组容量
            int chunks = current.chunksByClaim().getOrDefault(claimId, Set.of()).size();
            long used = ClaimEngine.currentClaimedChunks(current,
                OwnerRef.of(BuiltinOwnerTypes.GROUP, group.getGroupId()));
            if (used + chunks > groupCapacity(current, group.getGroupId())) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_QUOTA_EXCEEDED));
                return current;
            }
            // 只换所有者：区块、设置、flag、银行与生命周期状态全部保留
            ClaimData transferred = ClaimData.builder(
                    claim.getClaimId(), claim.getWorldUuid(), BuiltinOwnerTypes.GROUP, group.getGroupId(),
                    claim.getName())
                .admin(claim.isAdmin())
                .createdAt(claim.getCreatedAt())
                .lastActiveAt(claim.getLastActiveAt())
                .bankBalance(claim.getBankBalance())
                .upkeepExempt(claim.isUpkeepExempt())
                .upkeepChargedAt(claim.getUpkeepChargedAt())
                .upkeepUnpaidSince(claim.getUpkeepUnpaidSince())
                .inactiveWarnedAt(claim.getInactiveWarnedAt())
                .build();
            LandDaoManager.INSTANCE.claimDao().update(transferred);
            // 领地行改了所有者，claimsByOwner 必须按新归属重算
            DataSnapshot next = DataStore.reloadScoped(SnapshotPart.CLAIM, claim.getClaimId());
            // 领地已转给组，捐赠者个人账本按剩余实际持有量校正，避免额度被永久占用
            PlayerQuotaLedger.trimToActual(actor, next);
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    // ================= 额度 =================

    /**
     * 用户组区块额度 = 组基础额度 + 成员加成 × 当前成员数（FR-4.2）。
     */
    public long groupCapacity(DataSnapshot snapshot, String groupId) {
        int members = snapshot.groupMembers().getOrDefault(groupId, Map.of()).size();
        return (long) ConfigValues.get(ClaimConfigs.GROUP_BASE_CHUNKS)
            + (long) ConfigValues.get(ClaimConfigs.GROUP_BONUS_PER_MEMBER) * Math.max(1, members);
    }

    // ================= 内部工具 =================

    private void notifyChanged(String groupId) {
        ClaimOwnerRegistry.INSTANCE.notifyMembershipChanged(
            OwnerRef.of(BuiltinOwnerTypes.GROUP, groupId));
    }

    /** 是否已达上限：{@link GroupLimitResolver#NO_LIMIT} 永不判满（再取得一个即超额）。 */
    private static boolean limitReached(int limit, int current) {
        return limit != GroupLimitResolver.NO_LIMIT && current >= limit;
    }

    /** 已加入的用户组数：拥有视为已加入，故领袖（含无成员行的历史数据）一并计入。 */
    private static int countJoined(DataSnapshot snapshot, UUID player) {
        int count = 0;
        for (String groupId : snapshot.groups().keySet()) {
            if (IdentityPermissions.isMember(snapshot, groupId, player)) {
                count++;
            }
        }
        return count;
    }

    /** 已拥有的用户组数：以快照中的领袖字段为准（即组内身份为领袖身份）。 */
    private static int countOwned(DataSnapshot snapshot, UUID player) {
        int count = 0;
        for (String groupId : snapshot.groups().keySet()) {
            if (IdentityPermissions.isLeader(snapshot, groupId, player)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 转让领袖后，原领袖降为哪一级身份：取「非领袖身份中优先级最高者」
     * （{@link IdentityRegistry#all()} 已按优先级降序，第一个非领袖即所求；默认配置即 {@code manager}）。
     * 配置里只有领袖身份时，回落到默认（非成员）身份，保证降级后仍是一个已注册身份。
     */
    private static String formerLeaderIdentityId() {
        for (Identity identity : IdentityRegistry.INSTANCE.all()) {
            if (!identity.leader()) {
                return identity.id();
            }
        }
        return IdentityRegistry.INSTANCE.defaultIdentityId();
    }

    private static @Nullable String roleOf(DataSnapshot snapshot, String groupId, UUID player) {
        GroupData group = snapshot.groups().get(groupId);
        if (group == null) {
            return null;
        }
        String role = snapshot.groupMembers().getOrDefault(groupId, Map.of()).get(player);
        if (role == null && player.equals(group.getLeaderUuid())) {
            return Roles.OWNER;
        }
        return role;
    }

    /**
     * 按标识符（= groupId，忽略大小写）查找用户组。
     * {@link DataSnapshot#groups()} 本就是以 groupId 为 key，故为 O(1) 取用；供命令参数解析与补全复用。
     */
    public static @Nullable GroupData findById(DataSnapshot snapshot, String groupId) {
        if (groupId == null) {
            return null;
        }
        return snapshot.groups().get(groupId.toLowerCase(Locale.ROOT));
    }

    /** 该玩家当前有待处理邀请的用户组标识符；供补全与提示使用。 */
    public Set<String> pendingInviteGroupIds(UUID player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        Set<String> ids = new LinkedHashSet<>();
        pendingInvites.forEach((groupId, invited) -> {
            if (invited.contains(player) && snapshot.groups().containsKey(groupId)) {
                ids.add(groupId);
            }
        });
        return ids;
    }

    /** 展示名：trim 后非空且不超过长度上限；允许与其他用户组重名。 */
    private static @Nullable String normalizeName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_GROUP_NAME_LENGTH) {
            return null;
        }
        return trimmed;
    }

    /** 标识符：1-32 位小写字母、数字、下划线；统一转小写后返回，非法返回 null。 */
    private static @Nullable String normalizeGroupId(@Nullable String groupId) {
        if (groupId == null) {
            return null;
        }
        String normalized = groupId.trim().toLowerCase(Locale.ROOT);
        return GROUP_ID_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private void deleteMember(String groupId, UUID player) throws SQLException {
        var delete = LandDaoManager.INSTANCE.groupMemberDao().deleteBuilder();
        delete.where(w -> w.equals("group_id", groupId).and().equals("member_uuid", player));
        delete.delete();
    }

    private void deleteByGroup(Dao<?> dao, String groupId) throws SQLException {
        var delete = dao.deleteBuilder();
        delete.where(w -> w.equals("group_id", groupId));
        delete.delete();
    }

    @SuppressWarnings("unchecked")
    private void upsertMemberRole(LandDaoManager daos, String groupId, UUID player, String roleId)
        throws SQLException {
        var dao = daos.groupMemberDao();
        var query = dao.queryBuilder();
        query.where(w -> w.equals("group_id", groupId).and().equals("member_uuid", player));
        GroupMemberData row = query.query().stream().findFirst().orElse(null);
        if (row == null) {
            dao.create(new GroupMemberData(groupId, player, roleId));
        } else {
            row.setRoleId(roleId);
            dao.update(row);
        }
    }

    /**
     * 解析组名 → 组，再执行业务片段；组不存在直接失败（不触达 DAO）。
     */
    private interface GroupMutation {

        DataSnapshot mutate(DataSnapshot current, GroupData group) throws Exception;

    }

    private CompletableFuture<GroupOpResult> withGroup(
        UUID actor, String groupId,
        Function<AtomicReference<GroupOpResult>, GroupMutation> factory
    ) {
        String wanted = normalizeGroupId(groupId);
        if (wanted == null) {
            return failed(GroupFailureReason.INVALID_KEY);
        }
        return mutate(resultRef -> current -> {
            GroupData group = findById(current, wanted);
            if (group == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
                return current;
            }
            return factory.apply(resultRef).mutate(current, group);
        });
    }

    private interface MutationBody {

        DataSnapshot mutate(DataSnapshot current) throws Exception;

    }

    private CompletableFuture<GroupOpResult> mutate(
        Function<AtomicReference<GroupOpResult>, MutationBody> factory
    ) {
        AtomicReference<GroupOpResult> resultRef =
            new AtomicReference<>(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
        return DataStore.INSTANCE.mutate(current -> factory.apply(resultRef).mutate(current))
            .thenApply(snapshot -> resultRef.get());
    }

    private CompletableFuture<GroupOpResult> failed(GroupFailureReason reason) {
        return CompletableFuture.completedFuture(GroupOpResult.failed(reason));
    }

}
