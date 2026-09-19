package pers.yufiria.landguard.protection;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 方块坐标（纯数据，监听器把 Bukkit Block 转换后送入纯规则，避免热路径依赖平台 API）。
 */
public record BlockPoint(@NotNull UUID worldUuid, int x, int y, int z) {

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

}
