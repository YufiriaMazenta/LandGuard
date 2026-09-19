# LandGuard 所有者 SPI 接入指南

LandGuard 的领地所有权是抽象的：玩家、内置用户组、服务器都是地位对等的「所有者类型」。
第三方插件**无需修改 LandGuard 源码**，只需：

1. 实现 `ClaimOwner`（一个所有者实体）与 `ClaimOwnerProvider`（实体解析器）；
2. 插件启用时一行注册：`ClaimOwnerRegistry.INSTANCE.register(provider)`；
3. 成员变化后调用一个通知方法（或监听 LandGuard 桥接出的 Bukkit 事件）。

接入后，你的组织即可作为 `/land claim` 的归属目标，保护判定、flag、GUI、孤儿回收、维护费、银行全部自动适用。

- 适用版本：LandGuard 1.0.0+，Spigot/Paper 1.20+，Java 21
- 相关包：`pers.yufiria.landguard.owner`、`pers.yufiria.landguard.api.event`

## 1. 依赖配置

```kotlin
// build.gradle.kts —— 只编译依赖，不打包（运行时由服务端提供）
dependencies {
    compileOnly(files("libs/LandGuard-1.0.0.0.jar"))
}
```

```yaml
# plugin.yml：保证 LandGuard 先于你的插件完成启用
name: MyGuild
main: com.example.myguild.MyGuildPlugin
api-version: '1.20'
depend: [ LandGuard ]
```

## 2. 最小完整示例（约 60 行）

```java
package com.example.myguild;

import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.owner.*;

import java.util.*;

public final class MyGuildPlugin extends JavaPlugin implements ClaimOwnerProvider {

    public static final OwnerType TYPE = new OwnerType("myguild:guild");

    // identifier -> 组织实体（真实插件中替换为你的存储/缓存）
    private final Map<String, Guild> guilds = new HashMap<>();

    @Override
    public void onEnable() {
        // ... 加载你的组织数据 ...
        ClaimOwnerRegistry.INSTANCE.register(this);          // 一行注册
    }

    @Override
    public void onDisable() {
        ClaimOwnerRegistry.INSTANCE.unregister(TYPE);        // 重载安全
    }

    @Override public @NotNull OwnerType type() { return TYPE; }

    @Override
    public @Nullable ClaimOwner getOwner(@NotNull String identifier) {
        return guilds.get(identifier);                       // 必须 O(1)：保护判定热路径高频调用
    }

    @Override
    public @NotNull Collection<ClaimOwner> ownersOf(@NotNull UUID player) {
        return guilds.values().stream()
            .filter(g -> g.roleOf(player) != null)
            .map(g -> (ClaimOwner) g)
            .toList();
    }

    /** 成员变更后由你的业务代码调用，权限对在线玩家即时生效 */
    public void onMemberChanged(String guildId) {
        ClaimOwnerRegistry.INSTANCE.notifyMembershipChanged(OwnerRef.of(TYPE, guildId));
    }

    /** 组织解散时调用：其名下领地进入孤儿宽限流程 */
    public void onGuildDisbanded(String guildId) {
        ClaimOwnerRegistry.INSTANCE.notifyOwnerRemoved(OwnerRef.of(TYPE, guildId));
    }

    record Guild(String id, String displayName, Map<UUID, String> roles) implements ClaimOwner {
        @Override public @NotNull OwnerType type() { return TYPE; }
        @Override public @NotNull String identifier() { return id; }
        @Override public @NotNull Component displayName() { return Component.text(displayName); }
        @Override public @NotNull Set<UUID> members() { return Set.copyOf(roles.keySet()); }
        @Override public @Nullable String roleOf(UUID player) { return roles.get(player); }
    }
}
```

## 3. 角色与 flag

- 内置角色标识（`Roles`）：`owner` / `manager` / `member`；非成员自动为 `visitor`。
- 组织插件可以返回**自定义角色字符串**（如 `"recruit"`、`"treasurer"`），
  领地主人随后可在 GUI 的 flag 页为该角色单独配置能力；未配置时回退到默认矩阵。
- 角色是「身份」，flag 是「能力」，二者严格分离——不要在角色名里编码权限。

## 4. 失效通知与事件

| 时机 | 调用 | 效果 |
|---|---|---|
| 成员加入/退出/改角色 | `notifyMembershipChanged(ref)` | 在线玩家权限立即变化；LandGuard 桥接触发 `OwnerMembershipChangedEvent(MEMBERSHIP_CHANGED)` |
| 组织删除 | `notifyOwnerRemoved(ref)` | 名下领地进入孤儿宽限期，到期自动释放 |
| 批量校正/补发 | `fireFullInvalidation()` | LandGuard 全量重算成员关系 |

其他插件也可以只监听 Bukkit 事件 `pers.yufiria.landguard.api.event.OwnerMembershipChangedEvent` 来感知变化（含内置用户组）。

## 5. 注意事项

1. **`getOwner` 是热路径**：万级领地下每次保护事件都会调用，必须保持 O(1)，不要在此查库。
2. `getOwner` 返回 `null` 表示「当前不可解析」——该领地按**孤儿**处理（行为类保护默认拒绝），数据不丢；恢复可解析后自动解除。
3. 类型键务必使用命名空间（`插件名:类型`），避免与内置 `player`/`group`/`server` 冲突。
4. 插件重载（`/land reload`）后注册会被保留；你自己的插件 disable 时应 `unregister`。
5. Folia 下注册与解析均为线程安全的并发结构，无需自行调度。

## 6. 验证

LandGuard 自带的 `ClaimOwnerRegistrySpiTest` 以一个完全外部的 `test:guild` 提供方演示了完整契约：
解析、角色判定、重复注册替换、三类失效回调。第三方接入可直接参照该测试的写法。
