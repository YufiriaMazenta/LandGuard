package pers.yufiria.landguard.economy;

import crypticlib.CrypticLibBukkit;
import org.jetbrains.annotations.Nullable;
import pers.yufiria.landguard.claim.ClaimEngine;
import pers.yufiria.landguard.config.ClaimConfigs;
import pers.yufiria.landguard.config.EconomyConfigs;
import pers.yufiria.landguard.data.ChunkLoc;
import pers.yufiria.landguard.data.DataSnapshot;
import pers.yufiria.landguard.data.DataStore;
import pers.yufiria.landguard.database.dao.LandDaoManager;
import pers.yufiria.landguard.database.entity.ClaimData;
import pers.yufiria.landguard.database.entity.GroupData;
import pers.yufiria.landguard.database.entity.PlayerData;
import pers.yufiria.landguard.database.entity.PlayerQuotaData;
import pers.yufiria.landguard.owner.BuiltinOwnerTypes;
import pers.yufiria.landguard.owner.OwnerRef;
import pers.yufiria.landguard.protection.BuiltinFlags;
import pers.yufiria.landguard.protection.CheckResult;
import pers.yufiria.landguard.protection.ProtectionChecker;
import pers.yufiria.landguard.util.ConfigValues;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 经济领域服务（FR-7）：额度买卖、领地/组银行存取。
 * 所有数据库改动走 {@link DataStore#mutate} 单写线程；具体经济实现（Vault 等）的余额操作
 * 在调用方线程（命令/GUI 为 Bukkit 主线程）执行，快照校验与落库分两阶段，
 * 落库阶段异常时自动补偿退款，保证账户与银行不凭空增减。
 */
public enum EconomyService {

    INSTANCE;

    private volatile EconomyProvider provider;

    // ================= 提供方接入 =================

    public void hook(EconomyProvider provider) {
        this.provider = provider;
    }

    public void unhook() {
        this.provider = null;
    }

    public @Nullable EconomyProvider provider() {
        EconomyProvider current = provider;
        return current != null && current.available() ? current : null;
    }

    /** 配置开关与运行期提供方同时满足时经济入口才可用 */
    public boolean available() {
        return ConfigValues.get(EconomyConfigs.ENABLED) && provider() != null;
    }

    // ================= 额度买卖 =================

    public CompletableFuture<EconomyOpResult> buyChunks(UUID player, int chunks) {
        EconomyProvider economy = provider();
        if (!ConfigValues.get(EconomyConfigs.ENABLED) || economy == null) {
            return fail(EconomyFailureReason.UNAVAILABLE);
        }
        if (chunks <= 0) {
            return fail(EconomyFailureReason.INVALID_AMOUNT);
        }
        double cost = (double) chunks * ConfigValues.get(EconomyConfigs.BUY_PRICE_PER_CHUNK);
        // 阶段一：确保玩家数据行存在（纯落库准备）
        return DataStore.INSTANCE.mutate(current -> {
            ensurePlayer(player);
            return current;
        }).thenCompose(snapshot -> onMain(() -> economy.balance(player)).thenCompose(balance -> {
            if (balance + 1e-9 < cost) {
                return CompletableFuture.completedFuture(EconomyOpResult.failed(EconomyFailureReason.INSUFFICIENT_FUNDS));
            }
            return onMain(() -> economy.withdraw(player, cost)).thenCompose(paid -> {
                if (!paid) {
                    return CompletableFuture.completedFuture(EconomyOpResult.failed(EconomyFailureReason.INSUFFICIENT_FUNDS));
                }
                // 阶段二：扣款成功后落账；失败自动退款
                return DataStore.INSTANCE.mutate(current -> {
                    PlayerData data = LandDaoManager.INSTANCE.playerDao().queryForId(player);
                    data.setBoughtChunks(data.getBoughtChunks() + chunks);
                    LandDaoManager.INSTANCE.playerDao().update(data);
                    return DataStore.rebuildSnapshot();
                }).handle((next, throwable) -> {
                    if (throwable != null) {
                        economy.deposit(player, cost);
                        return EconomyOpResult.failed(EconomyFailureReason.INVALID_AMOUNT);
                    }
                    return EconomyOpResult.ok(cost, economy.balance(player), -1D);
                });
            });
        }));
    }

    public CompletableFuture<EconomyOpResult> sellChunks(UUID player, int chunks) {
        EconomyProvider economy = provider();
        if (!ConfigValues.get(EconomyConfigs.ENABLED) || economy == null) {
            return fail(EconomyFailureReason.UNAVAILABLE);
        }
        if (chunks <= 0) {
            return fail(EconomyFailureReason.INVALID_AMOUNT);
        }
        double refund = (double) chunks * ConfigValues.get(EconomyConfigs.SELL_PRICE_PER_CHUNK);
        OwnerRef owner = OwnerRef.of(BuiltinOwnerTypes.PLAYER, player.toString());
        AtomicReference<EconomyFailureReason> reject = new AtomicReference<>();
        // 阶段一：快照校验可售额度（不能卖空、不能卖到占用量以下）
        return DataStore.INSTANCE.mutate(current -> {
            ensurePlayer(player);
            PlayerData data = LandDaoManager.INSTANCE.playerDao().queryForId(player);
            if (data.getBoughtChunks() < chunks) {
                reject.set(EconomyFailureReason.NOTHING_TO_SELL);
                return current;
            }
            PlayerQuotaData quota = LandDaoManager.INSTANCE.playerQuotaDao().queryForId(player);
            long used = Math.max(quota == null ? 0 : quota.getUsedChunks(),
                ClaimEngine.currentClaimedChunks(current, owner));
            long capacity = (long) data.getAccruedChunks() + data.getBoughtChunks();
            if (capacity - used < chunks) {
                reject.set(EconomyFailureReason.QUOTA_IN_USE);
                return current;
            }
            return current;
        }).thenCompose(snapshot -> {
            if (reject.get() != null) {
                return CompletableFuture.completedFuture(EconomyOpResult.failed(reject.get()));
            }
            return onMain(() -> economy.deposit(player, refund)).thenCompose(credited -> {
                if (!credited) {
                    return CompletableFuture.completedFuture(EconomyOpResult.failed(EconomyFailureReason.INVALID_AMOUNT));
                }
                return DataStore.INSTANCE.mutate(current -> {
                    PlayerData data = LandDaoManager.INSTANCE.playerDao().queryForId(player);
                    data.setBoughtChunks(data.getBoughtChunks() - chunks);
                    LandDaoManager.INSTANCE.playerDao().update(data);
                    return DataStore.rebuildSnapshot();
                }).handle((next, throwable) -> {
                    if (throwable != null) {
                        economy.withdraw(player, refund);
                        return EconomyOpResult.failed(EconomyFailureReason.INVALID_AMOUNT);
                    }
                    return EconomyOpResult.ok(refund, economy.balance(player), -1D);
                });
            });
        });
    }

    // ================= 银行存取 =================

    /**
     * 查询玩家所在区块对应银行余额：组领地→组银行，其余→领地银行；无领地返回 -1。
     */
    public double bankBalanceAt(DataSnapshot snapshot, UUID worldUuid, int chunkX, int chunkZ) {
        ClaimData claim = claimAt(snapshot, worldUuid, chunkX, chunkZ);
        if (claim == null) {
            return -1D;
        }
        return bankBalance(snapshot, claim);
    }

    public CompletableFuture<EconomyOpResult> deposit(UUID player, UUID worldUuid, int chunkX, int chunkZ,
                                                       double amount) {
        EconomyProvider economy = provider();
        if (!ConfigValues.get(EconomyConfigs.ENABLED) || economy == null) {
            return fail(EconomyFailureReason.UNAVAILABLE);
        }
        if (!(amount > 0) || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return fail(EconomyFailureReason.INVALID_AMOUNT);
        }
        AtomicReference<EconomyFailureReason> reject = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            if (claimAt(current, worldUuid, chunkX, chunkZ) == null) {
                reject.set(EconomyFailureReason.CLAIM_NOT_FOUND);
            }
            return current;
        }).thenCompose(snapshot -> {
            if (reject.get() != null) {
                return CompletableFuture.completedFuture(EconomyOpResult.failed(reject.get()));
            }
            return onMain(() -> economy.withdraw(player, amount)).thenCompose(paid -> {
                if (!paid) {
                    return CompletableFuture.completedFuture(EconomyOpResult.failed(EconomyFailureReason.INSUFFICIENT_FUNDS));
                }
                return DataStore.INSTANCE.mutate(current -> {
                    ClaimData claim = claimAt(current, worldUuid, chunkX, chunkZ);
                    addBank(current, claim, amount);
                    return DataStore.rebuildSnapshot();
                }).handle((next, throwable) -> {
                    if (throwable != null) {
                        economy.deposit(player, amount);
                        return EconomyOpResult.failed(EconomyFailureReason.INVALID_AMOUNT);
                    }
                    double bank = bankBalanceAt(DataStore.INSTANCE.snapshot(), worldUuid, chunkX, chunkZ);
                    return EconomyOpResult.ok(amount, economy.balance(player), bank);
                });
            });
        });
    }

    public CompletableFuture<EconomyOpResult> withdraw(UUID player, UUID worldUuid, int chunkX, int chunkZ,
                                                        double amount) {
        EconomyProvider economy = provider();
        if (!ConfigValues.get(EconomyConfigs.ENABLED) || economy == null) {
            return fail(EconomyFailureReason.UNAVAILABLE);
        }
        if (!(amount > 0) || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return fail(EconomyFailureReason.INVALID_AMOUNT);
        }
        AtomicReference<EconomyFailureReason> reject = new AtomicReference<>();
        return DataStore.INSTANCE.mutate(current -> {
            ClaimData claim = claimAt(current, worldUuid, chunkX, chunkZ);
            if (claim == null) {
                reject.set(EconomyFailureReason.CLAIM_NOT_FOUND);
                return current;
            }
            // 取款受 BANK flag 控制（owner/manager 默认允许）
            CheckResult check = ProtectionChecker.checkBehavior(current, player, worldUuid, chunkX, chunkZ,
                BuiltinFlags.BANK);
            if (!check.allowed()) {
                reject.set(EconomyFailureReason.BANK_FORBIDDEN);
                return current;
            }
            if (bankBalance(current, claim) + 1e-9 < amount) {
                reject.set(EconomyFailureReason.BANK_EMPTY);
                return current;
            }
            return current;
        }).thenCompose(snapshot -> {
            if (reject.get() != null) {
                return CompletableFuture.completedFuture(EconomyOpResult.failed(reject.get()));
            }
            return DataStore.INSTANCE.mutate(current -> {
                ClaimData claim = claimAt(current, worldUuid, chunkX, chunkZ);
                addBank(current, claim, -amount);
                return DataStore.rebuildSnapshot();
            }).thenCompose(next -> onMain(() -> economy.deposit(player, amount)).thenCompose(credited -> {
                if (!credited) {
                    // 银行已扣而入账失败：回滚银行
                    return DataStore.INSTANCE.mutate(current -> {
                        ClaimData claim = claimAt(current, worldUuid, chunkX, chunkZ);
                        addBank(current, claim, amount);
                        return DataStore.rebuildSnapshot();
                    }).thenApply(s -> EconomyOpResult.failed(EconomyFailureReason.INVALID_AMOUNT));
                }
                double bank = bankBalanceAt(DataStore.INSTANCE.snapshot(), worldUuid, chunkX, chunkZ);
                return CompletableFuture.completedFuture(
                    EconomyOpResult.ok(amount, economy.balance(player), bank));
            }));
        });
    }

    // ================= 内部工具 =================

    private static @Nullable ClaimData claimAt(DataSnapshot snapshot, UUID worldUuid, int chunkX, int chunkZ) {
        String claimId = snapshot.claimIdByChunk().get(ChunkLoc.of(worldUuid, chunkX, chunkZ));
        return claimId == null ? null : snapshot.claimsById().get(claimId);
    }

    private static double bankBalance(DataSnapshot snapshot, ClaimData claim) {
        if (BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType())) {
            GroupData group = snapshot.groups().get(claim.getOwnerId());
            return group == null ? 0D : group.getBankBalance();
        }
        return claim.getBankBalance();
    }

    /**
     * 组领地钱进组银行，其余进领地银行。调用方位于写线程。
     */
    private static void addBank(DataSnapshot snapshot, ClaimData claim, double delta) throws java.sql.SQLException {
        LandDaoManager daos = LandDaoManager.INSTANCE;
        if (BuiltinOwnerTypes.GROUP.equals(claim.getOwnerType())) {
            GroupData group = daos.groupDao().queryForId(claim.getOwnerId());
            if (group == null) {
                throw new IllegalStateException("group owner row missing: " + claim.getOwnerId());
            }
            group.setBankBalance(round(group.getBankBalance() + delta));
            daos.groupDao().update(group);
        } else {
            ClaimData fresh = daos.claimDao().queryForId(claim.getClaimId());
            fresh.setBankBalance(round(fresh.getBankBalance() + delta));
            daos.claimDao().update(fresh);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static void ensurePlayer(UUID player) throws java.sql.SQLException {
        var playerDao = LandDaoManager.INSTANCE.playerDao();
        if (playerDao.queryForId(player) == null) {
            playerDao.create(new PlayerData(player, ConfigValues.get(ClaimConfigs.START_CHUNKS), 0,
                System.currentTimeMillis()));
        }
    }

    /**
     * 经济实现的余额操作放到 Bukkit 主线程执行（Vault 契约假定主线程）；
     * 无 Bukkit 平台（单元测试）时就地执行。
     */
    private static <T> CompletableFuture<T> onMain(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            CrypticLibBukkit.scheduler().sync(() -> {
                try {
                    future.complete(task.get());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (Throwable platformUnavailable) {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }
        return future;
    }

    private static CompletableFuture<EconomyOpResult> fail(EconomyFailureReason reason) {
        return CompletableFuture.completedFuture(EconomyOpResult.failed(reason));
    }

}
