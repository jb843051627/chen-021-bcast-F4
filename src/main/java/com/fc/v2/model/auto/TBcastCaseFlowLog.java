package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 处置单逐段记载对象 t_bcast_case_flow_log
 *
 * <p>一张处置单带一段一段的记载：哪一步记了什么话、落在哪一刻。往前、往后、收口各落一段，
 * 同一刹那里两条一起进来也分开各记各的，既不归并也不漏记。当前步次不靠主单纸面，
 * 由这张表 FORWARD/BACK 段数数出来。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_bcast_case_flow_log")
@ApiModel(value = "TBcastCaseFlowLog", description = "处置单逐段记载")
public class TBcastCaseFlowLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属处置单 t_bcast_case_flow.id */
    @TableField("case_id")
    @ApiModelProperty(value = "所属处置单")
    private Long caseId;

    /** 这一段落在哪一步（FORWARD 记踏进的步次，BACK 记退回后所在步次，TERMINAL 记收口时步次） */
    @TableField("stage")
    @ApiModelProperty(value = "这一段落在哪一步")
    private Integer stage;

    /** 记载种类 FORWARD 往前 / BACK 往后 / TERMINAL 收口 */
    @TableField("action")
    @ApiModelProperty(value = "记载种类 FORWARD/BACK/TERMINAL")
    private String action;

    /** 这一步记下的原话（同一步二次上报不再添行，卷上留的还是首次那句） */
    @TableField("content")
    @ApiModelProperty(value = "这一步记下的话")
    private String content;

    /** 记载人 */
    @TableField("create_by")
    @ApiModelProperty(value = "记载人")
    private String createBy;

    /** 落在哪一刻 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "落在哪一刻")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
