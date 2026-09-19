package pers.yufiria.landguard.admin;

/**
 * 孤儿领地展示信息（不可变）。
 *
 * @param orphanSince 进入孤儿状态的时间戳；0 表示已被识别但尚未被后台周期落戳（下一次扫描补记）
 */
public record OrphanInfo(
    String claimId,
    String claimName,
    String ownerType,
    String ownerId,
    int chunks,
    long orphanSince
) {
}
