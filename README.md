# LandGuard

[English](README.en-US.md) | 简体中文

> 现代化、面向组织的 Minecraft 纯区块领地保护插件。

LandGuard 以 **16×16 区块**为最小领地单位，所有权通过 SPI 抽象（玩家 / 用户组 / 服务器 / 第三方组织），角色身份与 flag（能力）严格分离；所有数据库写入走单写线程，主线程零 JDBC，完整兼容 Folia 区域线程模型。

## 特性

- **纯区块领地**：认领、放弃、半径批量、行走自动认领、粒子边界可视化
- **组织优先**：内置用户组（邀请 / 角色 / 转让 / 领地赠予），并开放所有者 SPI 供第三方组织系统接入
- **角色 × Flag 模型**：owner / manager / member / visitor 与自定义角色，每个角色可独立配置 20 项行为与自然保护开关
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

## 命令

| 命令 | 说明 |
|---|---|
| `/land` | 打开领地 GUI |
| `/land claim [radius <r> \| auto]` | 认领脚下区块 / 方形批量 / 切换行走自动认领 |
| `/land unclaim` | 放弃脚下区块 |
| `/land list` | 我的领地列表 |
| `/land info` | 脚下领地信息 |
| `/land group create\|disband\|invite\|accept\|deny\|leave\|kick\|transfer\|role\|giveclaim\|list\|info` | 用户组管理 |
| `/land buy <数量>` / `/land sell <数量>` | 买卖区块额度（需经济） |
| `/land bank [deposit\|withdraw <金额>]` | 领地银行（需经济） |
| `/land admin claim\|unclaim\|transfer\|release\|exempt\|info\|orphans\|run` | 管理操作 |
| `/land reload` / `/land version` | 重载 / 版本 |

别名：`landguard`、`lg`（可在 `config.yml` 修改）。

## 权限

| 节点 | 默认 | 说明 |
|---|---|---|
| `landguard.command` | true | 使用主命令 |
| `landguard.command.admin` | OP | 管理命令 |
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
