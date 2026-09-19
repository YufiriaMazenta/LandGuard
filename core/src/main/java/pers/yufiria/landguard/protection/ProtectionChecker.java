package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.owner.ClaimOwner;
import pers.yufiria.landguard.owner.ClaimOwnerRegistry;
import pers.yufiria.landguard.owner.OwnerRef;

import java.util.Map;
import java.util.UUID;

/**
 * 纯 Java 判定链（FR-3.5），不依赖 Bukkit，可在测试中直接断言：
 * 成员资格 → roleOf → 领地级覆盖 → 全局默认；角色永不从 flag 反推。
 */
public final class ProtectionChecker {

    private ProtectionChecker() {
    }

    /**
     * 解析区块/玩家上下文。
     */
    public static @NotNull CheckContext context(@NotNull DataSnapshot snapshot,
                                              @NotNull UUID player,
                                              @NotNull UUID worldUuid,
                                              int chunkX,
                                              int chunkZ) {
        String claimId = snapshot.claimIdByChunk().get(ChunkLoc.of(worldUuid, chunkX, chunkZ));
        if (claimId == null) {
            return CheckContext.freeWilderness();
        }
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim == null) {
            // 索引异常时按无领地处理，保护链上层不做越权放行
            return CheckContext.freeWilderness();
        }
        OwnerRef ownerRef = OwnerRef.of(claim.getOwnerType(), claim.getOwnerId());
        ClaimOwner owner = ClaimOwnerRegistry.INSTANCE.resolve(ownerRef);
        String role = owner == null ? null : owner.roleOf(player);
        return CheckContext.ofClaimed(claimId, ownerRef, owner, role);
    }

    /**
     * 行为类判定。野外一律允许；孤儿领地（所有者不可解析）一律拒绝；其余走角色覆盖链。
     */
    public static @NotNull CheckResult checkBehavior(@NotNull DataSnapshot snapshot,
                                                    @NotNull UUID player,
                                                    @NotNull UUID worldUuid,
                                                    int chunkX,
                                                    int chunkZ,
                                                    @NotNull ProtectionFlag flag) {
        CheckContext context = context(snapshot, player, worldUuid, chunkX, chunkZ);
        return decideBehavior(context, snapshot, flag);
    }

    public static @NotNull CheckResult decideBehavior(@NotNull CheckContext context,
                                                     @NotNull DataSnapshot snapshot,
                                                     @NotNull ProtectionFlag flag) {
        if (context.wilderness()) {
            return new CheckResult(context, flag, true, true);
        }
        if (context.orphaned()) {
            return new CheckResult(context, flag, false, true);
        }
        String role = context.effectiveBehaviorRole();
        Map<String, Boolean> overrides = snapshot.roleFlagsByClaim()
            .getOrDefault(context.claimId(), Map.of())
            .get(role);
        if (overrides != null && overrides.containsKey(flag.id())) {
            return new CheckResult(context, flag, overrides.get(flag.id()), false);
        }
        return new CheckResult(context, flag, BuiltinFlagDefaults.behaviorDefault(role, flag), true);
    }

    /**
     * 自然类判定：野外允许；在领地内取环境伪角色（{@link #ENVIRONMENT_ROLE}）覆盖，未覆盖取环境默认。
     */
    public static @NotNull CheckResult checkNatural(@NotNull DataSnapshot snapshot,
                                                   @NotNull UUID worldUuid,
                                                   int chunkX,
                                                   int chunkZ,
                                                   @NotNull ProtectionFlag flag) {
        String claimId = snapshot.claimIdByChunk().get(ChunkLoc.of(worldUuid, chunkX, chunkZ));
        if (claimId == null) {
            return new CheckResult(CheckContext.freeWilderness(), flag, true, true);
        }
        OwnerRef ownerRef = null;
        ClaimData claim = snapshot.claimsById().get(claimId);
        if (claim != null) {
            ownerRef = OwnerRef.of(claim.getOwnerType(), claim.getOwnerId());
        }
        ClaimOwner owner = ownerRef == null ? null : ClaimOwnerRegistry.INSTANCE.resolve(ownerRef);
        String memberRole = null;
        CheckContext context = new CheckContext(claimId, ownerRef, owner, memberRole);
        Map<String, Boolean> overrides = snapshot.roleFlagsByClaim()
            .getOrDefault(claimId, Map.of())
            .get(ENVIRONMENT_ROLE);
        if (overrides != null && overrides.containsKey(flag.id())) {
            return new CheckResult(context, flag, overrides.get(flag.id()), false);
        }
        return new CheckResult(context, flag, BuiltinFlagDefaults.naturalDefault(flag), true);
    }

    /**
     * 自然类覆盖在 lg_role_flag 中的伪角色键。以 # 前缀与真实角色标识隔离。
     */
    public static final String ENVIRONMENT_ROLE = "#natural";

}
