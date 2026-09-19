package pers.yufiria.landguard.economy;

public enum EconomyFailureReason {

    /** 经济服务未安装或被配置关闭 */
    UNAVAILABLE,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    /** 出售的额度正被占用，不能卖到容量以下 */
    QUOTA_IN_USE,
    NOTHING_TO_SELL,
    CLAIM_NOT_FOUND,
    /** 没有该领地银行取款权限（BANK flag） */
    BANK_FORBIDDEN,
    /** 银行余额不足（取款金额超过余额） */
    BANK_EMPTY

}
