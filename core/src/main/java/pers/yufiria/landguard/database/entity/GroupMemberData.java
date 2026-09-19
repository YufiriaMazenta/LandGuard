package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Table;

import java.util.UUID;

@Table(name = "lg_group_member")
public class GroupMemberData {

    @Field(id = true, generated = true)
    private long id;

    @Field(name = "group_id", nullable = false)
    private String groupId;

    @Field(name = "member_uuid", nullable = false)
    private UUID memberUuid;

    @Field(name = "role_id", nullable = false)
    private String roleId;

    public GroupMemberData() {
    }

    public GroupMemberData(String groupId, UUID memberUuid, String roleId) {
        this.groupId = groupId;
        this.memberUuid = memberUuid;
        this.roleId = roleId;
    }

    public long getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public UUID getMemberUuid() {
        return memberUuid;
    }

    public void setMemberUuid(UUID memberUuid) {
        this.memberUuid = memberUuid;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

}
