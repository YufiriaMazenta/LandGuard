package pers.yufiria.landguard.data;

import java.sql.SQLException;
import java.util.EnumSet;

/**
 * 单次 mutation 的写上下文：记录本次真正重新加载的快照组件。
 * 组件在 {@link #reload} / {@link #reloadScoped} 时按需查库，未加载的组件沿用基准快照；
 * 一次都没加载（校验失败直接返回）时 {@link #publish()} 返回基准快照本身，不做任何拷贝。
 * 仅由 DataStore 的写线程创建与使用。
 */
public final class WriteContext {

    private final DataSnapshot base;
    private DataSnapshot result;

    WriteContext(DataSnapshot base) {
        this.base = base;
        this.result = base;
    }

    /** 本次 mutation 的基准快照（写前状态，整个 mutation 内保持不变）。 */
    public DataSnapshot current() {
        return base;
    }

    public DataSnapshot reload(SnapshotPart first, SnapshotPart... more) throws SQLException {
        return reload(SnapshotPart.of(first, more));
    }

    /** 按组件重读；未声明的组件沿用上一次结果。 */
    public DataSnapshot reload(EnumSet<SnapshotPart> parts) throws SQLException {
        result = DataStore.SnapshotLoader.load(parts, result);
        return result;
    }

    /** 只重读某个领地范围内的行，用于 lg_claim_chunk 这类大表。 */
    public DataSnapshot reloadScoped(SnapshotPart part, String claimId) throws SQLException {
        result = DataStore.SnapshotLoader.loadScoped(part, claimId, result);
        return result;
    }

    /** 全量重读所有组件。 */
    public DataSnapshot reloadAll() throws SQLException {
        return reload(SnapshotPart.all());
    }

    /** 发布结果：没有加载过任何组件时即基准快照本身。 */
    public DataSnapshot publish() {
        return result;
    }

}