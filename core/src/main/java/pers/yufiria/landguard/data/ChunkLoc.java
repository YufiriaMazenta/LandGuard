package pers.yufiria.landguard.data;

import java.util.UUID;

/**
 * 区块坐标：世界 + 区块 X/Z。
 * 不可变，可直接作为 Map 键使用。
 */
public record ChunkLoc(UUID worldUuid, int x, int z) {

    public static ChunkLoc of(UUID worldUuid, int x, int z) {
        return new ChunkLoc(worldUuid, x, z);
    }

    /**
     * 与 Chunk#getChunkKey 相同的打包方式，便于调试与互转。
     */
    public long chunkKey() {
        return chunkKey(x, z);
    }

    public static long chunkKey(int x, int z) {
        return ((long) x & 0xffffffffL) | ((long) z << 32);
    }

}
