package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 播出事故处置单对象 t_bcast_case_flow
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_bcast_case_flow")
@ApiModel(value = "TBcastCaseFlow", description = "播出事故处置单")
public class TBcastCaseFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 播出事故处置单代号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "播出事故处置单代号")
    private String bizNo;

    /** 当前步次 0..3（发现核实/处置抢险/恢复确认/上报结案） */
    @TableField("stage")
    @ApiModelProperty(value = "当前步次 0..3（发现核实/处置抢险/恢复确认/上报结案）")
    private Integer stage;

    /** 处置会落 0未起 1在办 2已收了口 */
    @TableField("status")
    @ApiModelProperty(value = "处置会落 0未起 1在办 2已收了口")
    private Integer status;

    /** 一段一记 */
    @TableField("content")
    @ApiModelProperty(value = "一段一记")
    private String content;

    /** 最近一次挪步动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次挪步动作")
    private String lastAction;

    /** 发现核实门槛之一：台站代号 */
    @TableField("site_code")
    @ApiModelProperty(value = "发现核实门槛之一：台站代号")
    private String siteCode;

    /** 发现核实门槛之一：中断起于哪一刻 */
    @TableField("outage_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "发现核实门槛之一：中断起于哪一刻")
    private Date outageAt;

    /** 发现核实门槛之一：恢复到哪一刻（也是恢复确认起算钟点的那一刻） */
    @TableField("recover_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "发现核实门槛之一：恢复到哪一刻")
    private Date recoverAt;

    /** 发现核实门槛之一：被影响到的户数 */
    @TableField("affected_count")
    @ApiModelProperty(value = "发现核实门槛之一：被影响到的户数")
    private Long affectedCount;

    /** 处置抢险门槛：抢险那几笔记载（填没填全看它） */
    @TableField("rescue_note")
    @ApiModelProperty(value = "处置抢险门槛：抢险那几笔记载")
    private String rescueNote;

    /** 恢复确认门槛：当地定的恢复后须连着正常播够的钟点数 */
    @TableField("normal_hours")
    @ApiModelProperty(value = "恢复确认门槛：恢复后须连着正常播够的钟点数")
    private BigDecimal normalHours;

    /** 上报结案门槛：省局落下的结论（不必处置/就地终止两说也算收口） */
    @TableField("province_result")
    @ApiModelProperty(value = "上报结案门槛：省局结论")
    private String provinceResult;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public Date getOutageAt() {
        return outageAt;
    }

    public void setOutageAt(Date outageAt) {
        this.outageAt = outageAt;
    }

    public Date getRecoverAt() {
        return recoverAt;
    }

    public void setRecoverAt(Date recoverAt) {
        this.recoverAt = recoverAt;
    }

    public Long getAffectedCount() {
        return affectedCount;
    }

    public void setAffectedCount(Long affectedCount) {
        this.affectedCount = affectedCount;
    }

    public String getRescueNote() {
        return rescueNote;
    }

    public void setRescueNote(String rescueNote) {
        this.rescueNote = rescueNote;
    }

    public BigDecimal getNormalHours() {
        return normalHours;
    }

    public void setNormalHours(BigDecimal normalHours) {
        this.normalHours = normalHours;
    }

    public String getProvinceResult() {
        return provinceResult;
    }

    public void setProvinceResult(String provinceResult) {
        this.provinceResult = provinceResult;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
