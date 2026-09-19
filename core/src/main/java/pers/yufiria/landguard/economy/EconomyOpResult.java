package pers.yufiria.landguard.economy;

import org.jetbrains.annotations.Nullable;

/**
 * 经济写操作结果。amount=本次涉及金额，accountBalance=操作后个人账户余额，
 * bankBalance=操作后领地/组银行余额（非银行操作时为 -1）。
 */
public record EconomyOpResult(boolean success, double amount, double accountBalance, double bankBalance,
                              @Nullable EconomyFailureReason failureReason) {

    public static EconomyOpResult ok(double amount, double accountBalance, double bankBalance) {
        return new EconomyOpResult(true, amount, accountBalance, bankBalance, null);
    }

    public static EconomyOpResult failed(EconomyFailureReason reason) {
        return new EconomyOpResult(false, 0D, 0D, -1D, reason);
    }

}
