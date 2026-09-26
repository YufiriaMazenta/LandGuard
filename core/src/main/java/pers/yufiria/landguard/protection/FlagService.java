package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.data.SnapshotPart;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimRoleFlagData;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 领地级 flag 覆盖写服务。全部变更走 DataStore 单写线程：DAO upsert/delete 后按领地范围重读该领地的 flag。
 * 角色与 flag 在此始终作为两个独立维度落库（claim_id + role_id + flag_key）。
 */
public enum FlagService {

    INSTANCE;

    /**
     * 设置行为类 flag 覆盖（某领地某角色）。
     */
    public CompletableFuture<Boolean> setBehaviorOverride(@NotNull String claimId,
                                                         @NotNull String roleId,
                                                         @NotNull ProtectionFlag flag,
                                                         boolean value) {
        return setOverride(claimId, roleId, flag, value);
    }

    /**
     * 设置自然类 flag 覆盖（领地环境设置，角色维度为环境伪角色）。
     */
    public CompletableFuture<Boolean> setNaturalOverride(@NotNull String claimId,
                                                        @NotNull ProtectionFlag flag,
                                                        boolean value) {
        return setOverride(claimId, ProtectionChecker.ENVIRONMENT_ROLE, flag, value);
    }

    /**
     * 删除行为类覆盖，回退全局默认。
     */
    public CompletableFuture<Boolean> resetBehaviorOverride(@NotNull String claimId,
                                                            @NotNull String roleId,
                                                            @NotNull ProtectionFlag flag) {
        return deleteOverride(claimId, roleId, flag);
    }

    /**
     * 删除自然类覆盖。
     */
    public CompletableFuture<Boolean> resetNaturalOverride(@NotNull String claimId,
                                                          @NotNull ProtectionFlag flag) {
        return deleteOverride(claimId, ProtectionChecker.ENVIRONMENT_ROLE, flag);
    }

    private CompletableFuture<Boolean> setOverride(String claimId, String roleId, ProtectionFlag flag, boolean value) {
        if (roleId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        if (!FlagRegistry.INSTANCE.isRegistered(flag.id())) {
            return CompletableFuture.completedFuture(false);
        }
        AtomicBoolean outcome = new AtomicBoolean(false);
        return DataStore.INSTANCE.mutate(current -> {
            if (!current.claimsById().containsKey(claimId)) {
                return current;
            }
            LandDaoManager daos = LandDaoManager.INSTANCE;
            List<ClaimRoleFlagData> existing = daos.roleFlagDao().queryBuilder().where(where -> where
                .equals("claim_id", claimId).and()
                .equals("role_id", roleId).and()
                .equals("flag_key", flag.id())
            ).query();
            if (existing.isEmpty()) {
                daos.roleFlagDao().create(new ClaimRoleFlagData(claimId, roleId, flag.id(), value));
            } else {
                ClaimRoleFlagData row = existing.get(0);
                row.setValue(value);
                daos.roleFlagDao().update(row);
            }
            outcome.set(true);
            return DataStore.reloadScoped(SnapshotPart.ROLE_FLAG, claimId);
        }).thenApply(ignored -> outcome.get());
    }

    private CompletableFuture<Boolean> deleteOverride(String claimId, String roleId, ProtectionFlag flag) {
        if (!FlagRegistry.INSTANCE.isRegistered(flag.id())) {
            return CompletableFuture.completedFuture(false);
        }
        AtomicBoolean outcome = new AtomicBoolean(false);
        return DataStore.INSTANCE.mutate(current -> {
            if (!current.claimsById().containsKey(claimId)) {
                return current;
            }
            LandDaoManager daos = LandDaoManager.INSTANCE;
            daos.roleFlagDao().deleteBuilder().where(where -> where
                .equals("claim_id", claimId).and()
                .equals("role_id", roleId).and()
                .equals("flag_key", flag.id())
            ).delete();
            outcome.set(true);
            return DataStore.reloadScoped(SnapshotPart.ROLE_FLAG, claimId);
        }).thenApply((DataSnapshot ignored) -> outcome.get());
    }

}
