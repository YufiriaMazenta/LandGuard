package pers.yufiria.landguard.config;

import crypticlib.config.ConfigHandler;
import crypticlib.config.node.impl.bukkit.ConfigSectionConfig;

/**
 * 用户组身份定义：全服统一，用户组不能自定义身份（见 {@code identities.yml}）。
 */
@ConfigHandler(path = "identities.yml")
public class IdentityConfigs {

    public static final ConfigSectionConfig IDENTITIES = new ConfigSectionConfig(
        "identities"
    );

}
