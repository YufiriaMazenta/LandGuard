# LandGuard

[English](README.en-US.md) | 简体中文

> 现代化、面向组织的 Minecraft 纯区块领地保护插件。

LandGuard 以 **16×16 区块**为最小领地单位，所有权通过 SPI 抽象（玩家 / 用户组 / 服务器 / 第三方组织），角色身份与 flag（能力）严格分离；所有数据库写入走单写线程，主线程零 JDBC，完整兼容 Folia 区域线程模型。

## 特性

- **纯区块领地**：认领、放弃（本人领地，以及本人所在组内拥有放弃权限的身份所持的组领地）、半径批量（自动跳过已被认领的区块）、行走自动认领 / 自动放弃、粒子边界可视化
- **进入提示**：踏入他人/自己的领地时按配置发送动作栏或聊天提示，并可渲染该领地粒子边界（服务器与玩家各有一层开关）
- **组织优先**：内置用户组（邀请 / 身份 / 转让 / 领地赠予），并开放所有者 SPI 供第三方组织系统接入
- **身份 × Flag 模型**：owner / manager / member / visitor 等身份全部由 `identities.yml` 定义（全服统一，组内不可自定义），每个身份可独立配置组管理权限与领地内行为 flag
- **全面防护向量**：放置 / 破坏 / 容器 / 门 / 红石 / 工作台 / 载具 / 动物 / 展示实体 / 种植 / 采收 / 物品 / 银行；PvP / 爆炸 / 火焰 / 流体 / 活塞 / 怪物生成 / 怪物破坏 / 踩踏；活塞、流体等跨界过程按目标区块判定
- **GUI 管理**：`/land` 直接打开领地列表、详情、flag 三态切换、成员与领地银行界面
- **经济能力**：可选 Vault 挂钩，区块额度买卖、领地银行存取、定期维护费与欠费宽限
- **生命周期治理**：欠费 / 长期不活跃 / 所有者实体消失（孤儿）三条自动回收链，均可配置宽限期
- **管理员工具**：强制认领（server 所有）、强制放弃 / 释放 / 转让、豁免维护费、孤儿清单、手动执行回收周期
- **存储**：SQLite 内置零配置；MySQL 可选；旧库自动加列迁移
- **性能**：区块归属 O(1) 内存索引；1 万认领区块下保护事件均值约 **0.05–0.1µs/事件**（详见 `ProtectionHotPathBenchmarkTest`）
- **平台**：Spigot / Paper 1.20 ~ 1.21.x，Folia 支持（`folia-supported: true`），Java 21

## 安装

1. 安装 Java 21 与 1.20+ 的 Spigot/Paper（或 Folia）服务端
2. 将 `LandGuard-<version>.jar` 放入 `plugins/`
3. （可选）安装 Vault + 任意经济插件以启用额度买卖与领地银行
4. 启动服务器。配置文件生成于 `plugins/LandGuard/`：
   - `config.yml`：主命令别名、bStats 等
   - `claim.yml`：默认额度、半径上限、边界粒子时长、不活跃判定等
   - `database.yml`：sqlite / mysql
   - `economy.yml`：经济开关与额度单价
   - `upkeep.yml`：维护费、欠费/不活跃/孤儿宽限周期
   - `identities.yml`：用户组身份与权限（组管理权限点 + 领地内行为 flag）

## 身份与权限

用户组内的**身份**（role）与两类权限全部由 `identities.yml` 定义，**全服统一**：组内只能使用配置里定义的身份，玩家不能自定义身份。

每个身份可配置两类权限：

- `permissions`：组管理功能权限点（如 `group.invite`、`group.assign_identity`、`claim.expand`、`claim.flags`），决定能否邀请 / 踢人 / 指派身份 / 认领扩容组领地等；
- `behaviors`：领地内行为 flag（如 `place`、`container`、`bank`），决定该身份在领地内能做什么；`behaviors: ['*']` 表示全部行为。

两个语义锚点（各至多一个）：

- `leader: true`：领袖身份 —— 组内最高身份，只能经「转让领袖」获得，不可被指派、不可被踢；
- `default: true`：非成员默认身份 —— 不在组内的玩家按它判定。

配置缺失（文件不存在 / 该节为空 / 条目全部非法）时会回落到内置种子身份 `owner` / `manager` / `member` / `visitor`，与旧版本行为逐项一致。

> ⚠️ 身份 `id` 是稳定标识（落库于成员表，也是领地 flag 覆盖行的键）。**改动 id 会让已有的领地 flag 覆盖行失效**，建议只改 `name` / `priority` / 权限，保持 id 不变。

### 删除身份后旧数据如何回落

成员行的 `role_id` 指向一个已不存在于配置的身份时，该成员按「成员」（`member`）身份生效，**不写回数据库**：配置可以随时改回，读时兜底可逆且幂等，成员行本身也是审计痕迹。同理，领地 flag 覆盖表里指向已删除身份的行不再生效，但不会泄漏或误用。

旧版本用于自定义角色的 `lg_group_role` 表已不再使用，**不会自动删除**（便于回滚旧版本），可手工执行 `DROP TABLE lg_group_role` 清理。

## 命令

| 命令 | 说明 |
|---|---|
| `/land` | 打开领地 GUI |
| `/land claim [radius <r> \| auto] [--group <组>]` | 认领脚下区块 / 方形批量 / 切换行走自动认领（批量认领会跳过已被认领的区块）；缺省以本人身份，`--group` 以该组身份认领 / 扩容组领地 |
| `/land unclaim [auto]` | 放弃脚下区块 / 切换行走自动放弃（个人领地，以及本人所在组内拥有放弃权限的身份所持的组领地；该权限默认由领袖与管理者持有） |
| `/land list` | 我的领地列表 |
| `/land info` | 脚下领地信息 |
| `/land boundary` | 切换进入领地时的粒子边界渲染（按玩家，默认开启） |
| `/land rename <新名字>` | 重命名脚下领地（仅该领地所有者，名字可含空格，最长 32 字符） |
| `/land transfer --player <玩家名>` / `--group <组标识符>` | 把脚下领地转让给其他玩家或用户组（仅该领地所有者） |
| `/land group create\|disband\|invite\|accept\|deny\|leave\|kick\|transfer\|rename\|role\|list\|info` | 用户组管理（`transfer` 为转让组领袖，`rename` 改展示名） |
| `/land group role assign <组标识符> <玩家> <身份>` | 指派成员身份（身份来自 `identities.yml`） |
| `/land group role list [组标识符]` | 列出全部身份（id / 展示名 / 优先级 / 权限数 / 行为数）；给出组标识符时附带该组内各身份的成员数 |
| `/land buy <数量>` / `/land sell <数量>` | 买卖区块额度（需经济） |
| `/land bank [deposit\|withdraw <金额>]` | 领地银行（需经济） |
| `/land admin claim\|unclaim\|transfer\|release\|exempt\|rename\|info\|orphans\|run` | 管理操作 |
| `/land reload` / `/land version` | 重载 / 版本 |

别名：`landguard`、`lg`（可在 `config.yml` 修改）。

## 权限

子命令各自独立授权（未授权的子命令不会出现在补全里）；除 `landguard.bypass` 外默认均为 OP。

| 节点 | 默认 | 说明 |
|---|---|---|
| `landguard.command` | true | 使用主命令 `/land` |
| `landguard.command.claim` / `unclaim` / `list` / `info` / `boundary` / `rename` / `transfer` | OP | 认领 / 放弃 / 领地列表 / 领地信息 / 边界渲染开关 / 重命名领地 / 转让领地 |
| `landguard.command.reload` / `version` | OP | 重载配置 / 查看版本 |
| `landguard.command.buy` / `sell` / `bank` | OP | 购买额度 / 出售额度 / 领地银行（需 Vault） |
| `landguard.command.group` | OP | `/land group` 子命令树根（无参时输出用法） |
| `landguard.command.group.<动作>` | OP | `create`、`disband`、`invite`、`accept`、`deny`、`leave`、`kick`、`transfer`、`rename`、`role`、`list`、`info` |
| `landguard.command.group.role.assign` / `.list` | OP | 指派成员身份 / 列出全部身份 |
| `landguard.command.admin` | OP | `/land admin` 子命令树根（无参时输出用法） |
| `landguard.command.admin.<动作>` | OP | `claim`、`unclaim`、`transfer`、`release`、`exempt`、`rename`、`info`、`orphans`、`run` |
| `landguard.group.join_group_limit.<N>` | **false（含 OP）** | 最多加入 N 个用户组，**自己拥有的组织也计入**；未授予视为 0（不能加入），同时授予多个 N 时取最大 |
| `landguard.group.own_group_limit.<N>` | **false（含 OP）** | 最多拥有 N 个用户组（组内身份为领袖身份）；未授予视为 0（不能创建/接管） |
| `landguard.bypass` | **false（含 OP）** | 绕过全部行为保护判定，必须显式分配 |

## 开发者：接入自有组织系统

只需实现 `ClaimOwnerProvider` + `ClaimOwner`，启动时调用一行
`ClaimOwnerRegistry.INSTANCE.register(provider)` 即可让任意组织成为领地所有者。
完整最小示例见 [docs/spi-claim-owner.zh-CN.md](docs/spi-claim-owner.zh-CN.md)。

## 构建

```powershell
$env:JAVA_HOME="JDK21 路径"
.\gradlew.bat build
```

产物：`build/libs/LandGuard-<version>.jar`。

## 许可证

建议 MIT。
