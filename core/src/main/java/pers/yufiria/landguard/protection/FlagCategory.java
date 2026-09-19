package pers.yufiria.landguard.protection;

/**
 * flag 两大类：
 * BEHAVIOR —— 行为类，主体是玩家/实体，按其在所有者实体中的角色判定；
 * NATURAL  —— 自然类，领地环境设置（爆炸、火焰、流体等），与角色无关，按领地判定。
 */
public enum FlagCategory {

    BEHAVIOR,
    NATURAL

}
