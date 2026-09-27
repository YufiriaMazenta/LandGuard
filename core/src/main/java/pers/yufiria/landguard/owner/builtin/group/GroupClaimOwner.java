package pers.yufiria.landguard.owner.builtin.group;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.identity.IdentityPermissions;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.OwnerType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 内置用户组作为 {@link ClaimOwner} 的视图。
 * 不缓存任何成员数据：每次解析都读取当前 {@link DataStore#snapshot()}，
 * 因此成员加入/退出/角色调整在写线程发布新快照后对判定热路径即时可见。
 */
public final class GroupClaimOwner implements ClaimOwner {

    private static final OwnerType TYPE = new OwnerType(BuiltinOwnerTypes.GROUP);

    private final String groupId;

    public GroupClaimOwner(@NotNull String groupId) {
        this.groupId = groupId;
    }

    public @NotNull String groupId() {
        return groupId;
    }

    @Override
    public @NotNull OwnerType type() {
        return TYPE;
    }

    @Override
    public @NotNull String identifier() {
        return groupId;
    }

    @Override
    public @NotNull Component displayName() {
        GroupData data = DataStore.INSTANCE.snapshot().groups().get(groupId);
        return Component.text(data == null ? groupId : data.getName());
    }

    @Override
    public @NotNull Set<UUID> members() {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        GroupData group = snapshot.groups().get(groupId);
        if (group == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(snapshot.groupMembers().getOrDefault(groupId, Map.of()).keySet());
    }

    @Override
    public @Nullable String roleOf(UUID player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        if (!snapshot.groups().containsKey(groupId)) {
            return null;
        }
        // 领袖身份以 lg_group.leader_uuid 为准（优先于成员行），其余照实返回成员行里的身份 id
        return IdentityPermissions.memberIdentityIdOf(snapshot, groupId, player);
    }

}
