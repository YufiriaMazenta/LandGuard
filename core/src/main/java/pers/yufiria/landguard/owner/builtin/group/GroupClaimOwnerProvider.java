package pers.yufiria.landguard.owner.builtin.group;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerProvider;
import pers.yufiria.landguard.owner.OwnerType;

import java.util.*;

/**
 * 内置 {@code group} 类型提供方。与 player 提供方、第三方提供方地位对等，
 * 保护核心不特化任何组逻辑——成员关系全部由快照回答。
 */
public enum GroupClaimOwnerProvider implements ClaimOwnerProvider {

    INSTANCE;

    private static final OwnerType TYPE = new OwnerType(BuiltinOwnerTypes.GROUP);

    @Override
    public @NotNull OwnerType type() {
        return TYPE;
    }

    @Override
    public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
        return DataStore.INSTANCE.snapshot().groups().containsKey(identifier)
            ? new GroupClaimOwner(identifier)
            : null;
    }

    @Override
    public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
        DataSnapshot snapshot = DataStore.INSTANCE.snapshot();
        List<ClaimOwner> result = new ArrayList<>();
        for (Map.Entry<String, Map<UUID, String>> entry : snapshot.groupMembers().entrySet()) {
            if (entry.getValue().containsKey(player) && snapshot.groups().containsKey(entry.getKey())) {
                result.add(new GroupClaimOwner(entry.getKey()));
            }
        }
        return result;
    }

}
