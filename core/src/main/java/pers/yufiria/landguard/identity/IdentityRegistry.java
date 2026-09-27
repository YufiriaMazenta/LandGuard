package pers.yufiria.landguard.identity;

import crypticlib.CrypticLib;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.owner.Roles;
import pers.yufiria.landguard.protection.BuiltinFlags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 身份注册表：全服统一的身份定义来源。
 * 静态种子等价于历史的内置矩阵与硬编码判定，因此未加载配置的环境（单测、配置被删空）行为与旧版本一致；
 * 配置文件存在时整体替换。发布用「局部构建 + 一次性替换 volatile 引用」，
 * 因此删除身份立即生效（不像逐条 put 的注册表会残留），读者也只会看到完整的新旧两版之一。
 */
public enum IdentityRegistry {

    INSTANCE;

    private volatile Map<String, Identity> identities = builtinIdentities();
    private volatile Identity leaderIdentity = identities.get(Roles.OWNER);
    private volatile Identity defaultIdentity = identities.get(Roles.VISITOR);

    /** 幂等替换单个身份（测试夹具用）。 */
    public synchronized void register(@NotNull Identity identity) {
        Map<String, Identity> next = new LinkedHashMap<>(identities);
        next.put(identity.id(), identity);
        publish(next);
    }

    /** 整体替换全部身份，并按锚点规则重算领袖身份与非成员默认身份。 */
    public synchronized void replaceAll(@NotNull Collection<Identity> next) {
        Map<String, Identity> map = new LinkedHashMap<>();
        for (Identity identity : next) {
            map.put(identity.id(), identity);
        }
        publish(map);
    }

    /** 恢复内置种子身份。 */
    public synchronized void resetToBuiltins() {
        publish(builtinIdentities());
    }

    public @Nullable Identity get(@NotNull String id) {
        return identities.get(id);
    }

    public boolean isRegistered(@NotNull String id) {
        return identities.containsKey(id);
    }

    /** 全部身份，优先级降序（同值按 id 字典序，保证顺序稳定）。 */
    public @NotNull List<Identity> all() {
        List<Identity> result = new ArrayList<>(identities.values());
        result.sort(Comparator.comparingInt(Identity::priority).reversed()
            .thenComparing(Identity::id));
        return Collections.unmodifiableList(result);
    }

    public @NotNull Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(identities.keySet()));
    }

    public @NotNull String leaderIdentityId() {
        return leaderIdentity.id();
    }

    public @NotNull Identity defaultIdentity() {
        return defaultIdentity;
    }

    public @NotNull String defaultIdentityId() {
        return defaultIdentity.id();
    }

    /**
     * 把一个可能未注册的身份 id 解析成实际生效的身份：
     * 未注册（null 或配置里已删除）→ 回落 {@code member}，再回落非成员默认身份。
     */
    public @NotNull Identity resolve(@Nullable String id) {
        Identity identity = id == null ? null : identities.get(id);
        if (identity != null) {
            return identity;
        }
        Identity member = identities.get(Roles.MEMBER);
        return member != null ? member : defaultIdentity;
    }

    /** 该身份是否允许某行为 flag；未注册按 {@link #resolve} 回落。 */
    public boolean behaviorAllows(@Nullable String identityId, @NotNull String flagId) {
        return resolve(identityId).allowsBehavior(flagId);
    }

    /** 该身份是否拥有某管理权限点；未注册按 {@link #resolve} 回落。 */
    public boolean hasPermission(@Nullable String identityId, @NotNull PermissionPoint point) {
        return resolve(identityId).has(point);
    }

    /** 层级：actor 的优先级必须严格高于 target（同值不可操作）。 */
    public boolean outranks(@Nullable String actorId, @Nullable String targetId) {
        return resolve(actorId).priority() > resolve(targetId).priority();
    }

    private void publish(Map<String, Identity> map) {
        if (map.isEmpty()) {
            info("&c身份配置为空，继续使用内置种子身份");
            map = builtinIdentities();
        }
        this.identities = Collections.unmodifiableMap(map);
        this.leaderIdentity = resolveAnchor(map, true);
        this.defaultIdentity = resolveAnchor(map, false);
    }

    /**
     * 解析语义锚点：优先显式标记，其次 id 约定，最后按优先级取（领袖取最高、默认取最低）。
     */
    private static Identity resolveAnchor(Map<String, Identity> map, boolean leader) {
        String conventionalId = leader ? Roles.OWNER : Roles.VISITOR;
        List<Identity> marked = new ArrayList<>();
        for (Identity identity : map.values()) {
            if (leader ? identity.leader() : identity.isDefault()) {
                marked.add(identity);
            }
        }
        if (marked.size() > 1) {
            info("&e身份配置有多个 " + (leader ? "leader" : "default") + " 标记，按优先级取一个");
        }
        if (!marked.isEmpty()) {
            return leader ? highest(marked) : lowest(marked);
        }
        Identity conventional = map.get(conventionalId);
        if (conventional != null) {
            return conventional;
        }
        info("&e身份配置缺少领袖/默认身份标记与 " + conventionalId + " 约定 id，按优先级兜底");
        return leader ? highest(map.values()) : lowest(map.values());
    }

    private static Identity highest(Collection<Identity> candidates) {
        return candidates.stream().max(Comparator.comparingInt(Identity::priority)
            .thenComparing(Identity::id)).orElseThrow();
    }

    private static Identity lowest(Collection<Identity> candidates) {
        return candidates.stream().min(Comparator.comparingInt(Identity::priority)
            .thenComparing(Identity::id)).orElseThrow();
    }

    /**
     * 内置种子身份：权限点与行为集等价于历史实现
     * （ownership 判定为 owner/manager，行为矩阵见原 BuiltinFlagDefaults）。
     */
    private static Map<String, Identity> builtinIdentities() {
        Set<String> allBehaviors = Set.of(Identity.ALL_BEHAVIORS);
        Set<String> memberBehaviors = ids(
            BuiltinFlags.PLACE, BuiltinFlags.BREAK, BuiltinFlags.DOOR, BuiltinFlags.REDSTONE,
            BuiltinFlags.CRAFTING, BuiltinFlags.VEHICLE, BuiltinFlags.ANIMAL, BuiltinFlags.DISPLAY,
            BuiltinFlags.PLANTING, BuiltinFlags.HARVEST, BuiltinFlags.ITEM);
        Set<String> visitorBehaviors = Set.of(BuiltinFlags.CRAFTING.id());

        Identity owner = new Identity(Roles.OWNER, "领袖", 100, true, false,
            keys(PermissionPoint.values()), allBehaviors);
        Identity manager = new Identity(Roles.MANAGER, "管理者", 60, false, false,
            keys(PermissionPoint.GROUP_INVITE, PermissionPoint.GROUP_KICK,
                PermissionPoint.GROUP_ASSIGN, PermissionPoint.GROUP_GIVE_CLAIM,
                PermissionPoint.CLAIM_EXPAND, PermissionPoint.CLAIM_UNCLAIM,
                PermissionPoint.CLAIM_FLAGS),
            allBehaviors);
        Identity member = new Identity(Roles.MEMBER, "成员", 40, false, false,
            Set.of(), memberBehaviors);
        Identity visitor = new Identity(Roles.VISITOR, "访客", 0, false, true,
            Set.of(), visitorBehaviors);

        Map<String, Identity> map = new LinkedHashMap<>();
        map.put(owner.id(), owner);
        map.put(manager.id(), manager);
        map.put(member.id(), member);
        map.put(visitor.id(), visitor);
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> keys(PermissionPoint... points) {
        Set<String> keys = new LinkedHashSet<>();
        for (PermissionPoint point : points) {
            keys.add(point.key());
        }
        return Collections.unmodifiableSet(keys);
    }

    private static Set<String> ids(pers.yufiria.landguard.protection.ProtectionFlag... flags) {
        Set<String> result = new LinkedHashSet<>();
        for (pers.yufiria.landguard.protection.ProtectionFlag flag : flags) {
            result.add(flag.id());
        }
        return Collections.unmodifiableSet(result);
    }

    private static void info(String message) {
        try {
            CrypticLib.info(message);
        } catch (Throwable platformUnavailable) {
            // 单元测试等无平台环境下 CrypticLib 不可用；配置告警不影响运行，忽略
        }
    }

}
