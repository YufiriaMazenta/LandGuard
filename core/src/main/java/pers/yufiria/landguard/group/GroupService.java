package pers.yufiria.landguard.group;

import crypticlib.database.dao.Dao;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.GroupMemberData;
import pers.yufiria.landguard.database.entity.GroupRoleData;
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

    private static final Pattern ROLE_ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");
    /** 组标识符（= groupId）格式：与角色标识保持一致。 */
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z0-9_]{1,32}");
    private static final int MAX_GROUP_NAME_LENGTH = 32;

    /** groupId -> 被邀请玩家集合（内存态） */
    private final Map<String, Set<UUID>> pendingInvites = new ConcurrentHashMap<>();

    // ================= 组生命周期 =================

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
        return mutate(resultRef -> current -> {
            if (current.groups().containsKey(wantedId)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.KEY_TAKEN));
                return current;
            }
            long now = System.currentTimeMillis();
            LandDaoManager daos = LandDaoManager.INSTANCE;
            daos.groupDao().create(new GroupData(wantedId, wantedName, creator, now, 0D));
            daos.groupMemberDao().create(new GroupMemberData(wantedId, creator, Roles.OWNER));
            DataSnapshot next = DataStore.rebuildSnapshot();
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
            if (!isLeader(current, group, actor)) {
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
            DataSnapshot next = DataStore.rebuildSnapshot();
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> disband(UUID actor, String groupName) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!isLeader(current, group, actor)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_LEADER));
                return current;
            }
            LandDaoManager daos = LandDaoManager.INSTANCE;
            deleteByGroup(daos.groupRoleDao(), group.getGroupId());
            deleteByGroup(daos.groupMemberDao(), group.getGroupId());
            daos.groupDao().delete(group);
            pendingInvites.remove(group.getGroupId());
            DataSnapshot next = DataStore.rebuildSnapshot();
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
            String actorRole = roleOf(current, group.getGroupId(), actor);
            if (!Roles.OWNER.equals(actorRole) && !Roles.MANAGER.equals(actorRole)) {
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
        return mutate(resultRef -> current -> {
            GroupData group = findById(current, wanted);
            if (group == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.GROUP_NOT_FOUND));
                return current;
            }
            Set<UUID> invites = pendingInvites.get(group.getGroupId());
            if (invites == null || !invites.remove(player)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NO_INVITE));
                return current;
            }
            if (!accept) {
                resultRef.set(GroupOpResult.ok(group.getGroupId()));
                return current;
            }
            if (roleOf(current, group.getGroupId(), player) != null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.ALREADY_MEMBER));
                return current;
            }
            LandDaoManager.INSTANCE.groupMemberDao()
                .create(new GroupMemberData(group.getGroupId(), player, Roles.MEMBER));
            DataSnapshot next = DataStore.rebuildSnapshot();
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
            if (Roles.OWNER.equals(role)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.LEADER_CANNOT_LEAVE));
                return current;
            }
            deleteMember(group.getGroupId(), player);
            DataSnapshot next = DataStore.rebuildSnapshot();
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> kick(UUID actor, String groupName, UUID target) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            String actorRole = roleOf(current, group.getGroupId(), actor);
            if (!Roles.OWNER.equals(actorRole) && !Roles.MANAGER.equals(actorRole)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            String targetRole = roleOf(current, group.getGroupId(), target);
            if (targetRole == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.TARGET_NOT_MEMBER));
                return current;
            }
            // 领袖不可踢；管理者只能踢普通成员/自定义角色，不能踢管理者
            boolean allowed = Roles.OWNER.equals(actorRole)
                ? !Roles.OWNER.equals(targetRole)
                : !Roles.OWNER.equals(targetRole) && !Roles.MANAGER.equals(targetRole);
            if (!allowed) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.CANNOT_KICK));
                return current;
            }
            deleteMember(group.getGroupId(), target);
            DataSnapshot next = DataStore.rebuildSnapshot();
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> transferLeadership(UUID actor, String groupName, UUID target) {
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            if (!isLeader(current, group, actor)) {
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
            LandDaoManager daos = LandDaoManager.INSTANCE;
            group.setLeaderUuid(target);
            daos.groupDao().update(group);
            upsertMemberRole(daos, group.getGroupId(), target, Roles.OWNER);
            upsertMemberRole(daos, group.getGroupId(), actor, Roles.MANAGER);
            DataSnapshot next = DataStore.rebuildSnapshot();
            notifyChanged(group.getGroupId());
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    // ================= 自定义角色 =================

    public CompletableFuture<GroupOpResult> createRole(UUID actor, String groupName,
                                                       String roleId, int priority, String displayName) {
        if (roleId == null || !ROLE_ID_PATTERN.matcher(roleId).matches()) {
            return failed(GroupFailureReason.ROLE_ID_INVALID);
        }
        if (Roles.OWNER.equals(roleId) || Roles.MANAGER.equals(roleId)
            || Roles.MEMBER.equals(roleId) || Roles.VISITOR.equals(roleId)) {
            return failed(GroupFailureReason.ROLE_BUILTIN);
        }
        if (displayName == null || displayName.isBlank()) {
            return failed(GroupFailureReason.ROLE_ID_INVALID);
        }
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            String actorRole = roleOf(current, group.getGroupId(), actor);
            if (!Roles.OWNER.equals(actorRole) && !Roles.MANAGER.equals(actorRole)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            if (current.groupRoles().getOrDefault(group.getGroupId(), Map.of()).containsKey(roleId)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.ROLE_EXISTS));
                return current;
            }
            LandDaoManager.INSTANCE.groupRoleDao()
                .create(new GroupRoleData(group.getGroupId(), roleId, priority, displayName.trim()));
            DataSnapshot next = DataStore.rebuildSnapshot();
            resultRef.set(GroupOpResult.ok(group.getGroupId()));
            return next;
        });
    }

    public CompletableFuture<GroupOpResult> assignRole(UUID actor, String groupName,
                                                       UUID target, String roleId) {
        if (Roles.OWNER.equals(roleId) || Roles.VISITOR.equals(roleId)) {
            return failed(GroupFailureReason.ROLE_BUILTIN);
        }
        return withGroup(actor, groupName, resultRef -> (current, group) -> {
            String actorRole = roleOf(current, group.getGroupId(), actor);
            if (!Roles.OWNER.equals(actorRole) && !Roles.MANAGER.equals(actorRole)) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.NOT_MANAGER));
                return current;
            }
            if (roleOf(current, group.getGroupId(), target) == null) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.TARGET_NOT_MEMBER));
                return current;
            }
            boolean roleKnown = Roles.MANAGER.equals(roleId) || Roles.MEMBER.equals(roleId)
                || current.groupRoles().getOrDefault(group.getGroupId(), Map.of()).containsKey(roleId);
            if (!roleKnown) {
                resultRef.set(GroupOpResult.failed(GroupFailureReason.ROLE_NOT_FOUND));
                return current;
            }
            upsertMemberRole(LandDaoManager.INSTANCE, group.getGroupId(), target, roleId);
            DataSnapshot next = DataStore.rebuildSnapshot();
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
            String actorRole = roleOf(current, group.getGroupId(), actor);
            if (!Roles.OWNER.equals(actorRole) && !Roles.MANAGER.equals(actorRole)) {
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
            ClaimData transferred = new ClaimData(
                claim.getClaimId(), claim.getWorldUuid(), BuiltinOwnerTypes.GROUP, group.getGroupId(),
                claim.getName(), claim.isAdmin(), claim.getCreatedAt(), claim.getLastActiveAt(),
                claim.getBankBalance(), claim.isUpkeepExempt(),
                claim.getUpkeepChargedAt(), claim.getUpkeepUnpaidSince(), claim.getInactiveWarnedAt());
            LandDaoManager.INSTANCE.claimDao().update(transferred);
            DataSnapshot next = DataStore.rebuildSnapshot();
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

    private static boolean isLeader(DataSnapshot snapshot, GroupData group, UUID player) {
        return group.getLeaderUuid().equals(player)
            || Roles.OWNER.equals(roleOf(snapshot, group.getGroupId(), player));
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
