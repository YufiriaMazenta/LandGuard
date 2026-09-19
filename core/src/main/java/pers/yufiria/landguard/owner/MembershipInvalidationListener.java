package pers.yufiria.landguard.owner;

/**
 * 成员资格失效监听。领地/会话层据此实现“成员变更即时生效”。
 * 所有回调均可能在异步线程触发，实现方需要自行保证线程安全。
 */
public interface MembershipInvalidationListener {

    /**
     * 某个所有者实体的成员/角色发生变化。
     */
    default void onMembershipChanged(OwnerRef owner) {
    }

    /**
     * 某个所有者实体被删除（组织解散、提供方注销数据等）。
     */
    default void onOwnerRemoved(OwnerRef owner) {
    }

    /**
     * 周期性全量校正：实现方应丢弃派生缓存并重新计算。
     */
    default void onFullInvalidation() {
    }

}
