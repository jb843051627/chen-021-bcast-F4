package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.IBcastCaseFlowMoveService;
import com.fc.v2.service.ITBcastCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 播出事故处置单 Controller（流转台账 + 挪步出口的唯一门面）。
 *
 * <p>屏上只有一处能挪步次：往前、往后都调 {@link IBcastCaseFlowMoveService}，
 * 本类不自作主张定第几步。纸面临的 stage、表单递来的步次只算意图，
 * 回读步次一律取 moveService.currentStage——挪步出口说第几步就是第几步。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Api(value = "播出事故处置单")
@Controller
@RequestMapping("/bcastCaseFlow")
public class BcastCaseFlowController extends BaseController {

    private final String prefix = "admin/bcastCaseFlow";

    @Autowired
    private ITBcastCaseFlowService bcastCaseFlowService;

    @Autowired
    private IBcastCaseFlowMoveService bcastCaseFlowMoveService;

    @ApiOperation(value = "流转台账跳转", notes = "流转台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("bcastCaseFlow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "播出事故处置单流转台账", action = "list")
    @ApiOperation(value = "流转台账", notes = "流转台账")
    @GetMapping("/list")
    @RequiresPermissions("bcastCaseFlow:list")
    @ResponseBody
    public ResultTable list(TBcastCaseFlow record) {
        QueryWrapper<TBcastCaseFlow> queryWrapper = new QueryWrapper<TBcastCaseFlow>();
        startPage();
        com.github.pagehelper.PageInfo<TBcastCaseFlow> page =
                new com.github.pagehelper.PageInfo<TBcastCaseFlow>(bcastCaseFlowService.selectTBcastCaseFlowList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "播出事故处置单立案", action = "open")
    @ApiOperation(value = "立案", notes = "先立一张在第一步的活单")
    @PostMapping("/open")
    @RequiresPermissions("bcastCaseFlow:open")
    @ResponseBody
    public AjaxResult open(TBcastCaseFlow record, String firstWords) {
        TBcastCaseFlow r = bcastCaseFlowMoveService.openCase(record, firstWords);
        return r == null ? AjaxResult.error("同一起事故还有一张活单没办结，新单立不起来") : AjaxResult.successData(200, r);
    }

    @Log(title = "播出事故处置单补料", action = "supplement")
    @ApiOperation(value = "补门槛材料", notes = "在办单补四样/抢险几笔/应播钟点")
    @PostMapping("/supplement")
    @RequiresPermissions("bcastCaseFlow:supplement")
    @ResponseBody
    public AjaxResult supplement(Long id, TBcastCaseFlow patch) {
        return toAjax(bcastCaseFlowMoveService.supplement(id, patch) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单推进", action = "advance")
    @ApiOperation(value = "往前一步", notes = "只进紧挨着的后一步，门槛没过收不下")
    @PostMapping("/advance")
    @RequiresPermissions("bcastCaseFlow:advance")
    @ResponseBody
    public AjaxResult advance(Long id, String remark) {
        // 两道来路（恢复回报自推、值班主任手点）都并到这一个口子，屏上不留第二处把手
        return toAjax(bcastCaseFlowMoveService.moveForward(id, remark) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单回退", action = "rollback")
    @ApiOperation(value = "往后一步", notes = "一次退一步，原话沉底，重走重新攒")
    @PostMapping("/rollback")
    @RequiresPermissions("bcastCaseFlow:rollback")
    @ResponseBody
    public AjaxResult rollback(Long id, String remark) {
        return toAjax(bcastCaseFlowMoveService.moveBack(id, remark) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单同步上报", action = "report")
    @ApiOperation(value = "同一步上报", notes = "卷上只留首次那句，第二遍不添行")
    @PostMapping("/report")
    @RequiresPermissions("bcastCaseFlow:report")
    @ResponseBody
    public AjaxResult report(Long id, String remark) {
        return toAjax(bcastCaseFlowMoveService.reportWords(id, remark) ? 1 : 0);
    }

    @Log(title = "播出事故处置单上报结案", action = "conclude")
    @ApiOperation(value = "落省局结论收口", notes = "不必处置/就地终止两说当场收口")
    @PostMapping("/conclude")
    @RequiresPermissions("bcastCaseFlow:conclude")
    @ResponseBody
    public AjaxResult conclude(Long id, String provinceResult) {
        return toAjax(bcastCaseFlowMoveService.conclude(id, provinceResult) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单中途叫停", action = "cancel")
    @ApiOperation(value = "中途叫停", notes = "只叫停在办活单，收了口的删不掉")
    @PostMapping("/cancel")
    @RequiresPermissions("bcastCaseFlow:cancel")
    @ResponseBody
    public AjaxResult cancel(Long id) {
        return toAjax(bcastCaseFlowMoveService.cancel(id));
    }

    @ApiOperation(value = "读当前步次", notes = "步次只从挪步出口读，纸面的不算")
    @GetMapping("/stage")
    @RequiresPermissions("bcastCaseFlow:view")
    @ResponseBody
    public AjaxResult stage(Long id) {
        Integer stage = bcastCaseFlowMoveService.currentStage(id);
        return stage == null ? AjaxResult.error("查无这张处置单") : AjaxResult.successData(200, stage);
    }
}
