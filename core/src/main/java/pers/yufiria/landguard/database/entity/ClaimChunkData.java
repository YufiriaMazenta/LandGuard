package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

import java.util.UUID;

@Table(name = "lg_claim_chunk")
public class ClaimChunkData {

    @Field(id = true, generated = true)
    private long id;

    @Field(name = "claim_id", nullable = false)
    private String claimId;

    @Field(name = "world_uuid", nullable = false)
    private UUID worldUuid;

    @Field(name = "chunk_x", type = ColumnType.INT)
    private int chunkX;

    @Field(name = "chunk_z", type = ColumnType.INT)
    private int chunkZ;

    public ClaimChunkData() {
    }

    public ClaimChunkData(String claimId, UUID worldUuid, int chunkX, int chunkZ) {
        this.claimId = claimId;
        this.worldUuid = worldUuid;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public long getId() {
        return id;
    }

    public String getClaimId() {
        return claimId;
    }

    public void setClaimId(String claimId) {
        this.claimId = claimId;
    }

    public UUID getWorldUuid() {
        return worldUuid;
    }

    public void setWorldUuid(UUID worldUuid) {
        this.worldUuid = worldUuid;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

}
