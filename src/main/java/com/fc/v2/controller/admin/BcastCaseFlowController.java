package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.ITBcastCaseFlowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

/**
 * 播出事故处置单 Controller（state-machine 形状：流转入口）
 *
 * <p>屏上只有这一处把手，往前、往后都递到服务层那一个挪步出口；归在第几步、
 * 收不收、改不改得动，屏上不自作主张，也不留第二处能挪步次的把手。
 * 旧的 /advance（一推两档）、/rollback（一退到底）两条老路已经关掉。</p>
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

    @Log(title = "播出事故处置单往前挪", action = "moveForward")
    @ApiOperation(value = "往前挪一步", notes = "只进紧挨着的后一步；递上来的步次只算意图")
    @PostMapping("/moveForward")
    @RequiresPermissions("bcastCaseFlow:moveForward")
    @ResponseBody
    public AjaxResult moveForward(Long id, Integer intentStage, String report) {
        return toAjax(bcastCaseFlowService.moveForward(id, intentStage, report) != null ? 1 : 0);
    }

    @Log(title = "播出事故处置单往后退", action = "moveBackward")
    @ApiOperation(value = "往后退一步", notes = "一次退一步；已经停在第一步的不再往后收")
    @PostMapping("/moveBackward")
    @RequiresPermissions("bcastCaseFlow:moveBackward")
    @ResponseBody
    public AjaxResult moveBackward(Long id, String report) {
        return toAjax(bcastCaseFlowService.moveBackward(id, report) != null ? 1 : 0);
    }
}
