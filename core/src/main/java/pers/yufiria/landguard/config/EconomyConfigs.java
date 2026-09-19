package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.BooleanConfig;
import crypticlib.config.node.impl.bukkit.DoubleConfig;

import java.util.List;

/**
 * economy.yml：经济功能总开关与额度买卖单价。
 * 经济实际可用性 = ENABLED 且运行期存在 {@link pers.yufiria.landguard.economy.EconomyProvider}
 * （即安装了 Vault 及任意经济插件）。
 */
@ConfigHandler(path = "economy.yml")
public class EconomyConfigs {

    public static final BooleanConfig ENABLED = new BooleanConfig(
        "economy.enabled",
        true,
        List.of("经济功能总开关；即使为 true，未安装 Vault 经济时买卖与银行入口仍会自动禁用")
    );

    public static final DoubleConfig BUY_PRICE_PER_CHUNK = new DoubleConfig(
        "economy.buy_price_per_chunk",
        100D,
        List.of("向服务器购买 1 个区块额度的价格")
    );

    public static final DoubleConfig SELL_PRICE_PER_CHUNK = new DoubleConfig(
        "economy.sell_price_per_chunk",
        80D,
        List.of("向服务器出售 1 个区块额度的返还价格；不能出售已被占用的额度")
    );

}
