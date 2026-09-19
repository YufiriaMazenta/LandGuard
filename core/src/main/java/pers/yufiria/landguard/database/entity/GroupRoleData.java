package pers.yufiria.landguard.database.entity;

import crypticlib.database.annotation.Field;
import crypticlib.database.annotation.Field.ColumnType;
import crypticlib.database.annotation.Table;

@Table(name = "lg_group_role")
public class GroupRoleData {

    @Field(id = true, generated = true)
    private long id;

    @Field(name = "group_id", nullable = false)
    private String groupId;

    @Field(name = "role_id", nullable = false)
    private String roleId;

    @Field(name = "priority", type = ColumnType.INT)
    private int priority;

    @Field(name = "name", nullable = false)
    private String name;

    public GroupRoleData() {
    }

    public GroupRoleData(String groupId, String roleId, int priority, String name) {
        this.groupId = groupId;
        this.roleId = roleId;
        this.priority = priority;
        this.name = name;
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

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
