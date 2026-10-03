package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBcastCaseFlow;

/**
 * 播出事故处置单台账 Service接口（只查账，不挪步）。
 *
 * <p>挪步、收口、改字、叫停一律走 {@link IBcastCaseFlowMoveService} 那一个出口，
 * 本接口只摆台账、不写单据——能挪步次的把手一处都不留，屏上因此只有一处能往前挪。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBcastCaseFlowService {

    /** 按主键查单据（已收口的仍查得到，年末要凭它追当年结/终止的来由） */
    TBcastCaseFlow selectTBcastCaseFlowById(Long id);

    /** 台账列表：递进来的条件照用，只补一条删除标记，已收口的不抹、仍在台账上 */
    List<TBcastCaseFlow> selectTBcastCaseFlowList(Wrapper<TBcastCaseFlow> queryWrapper);
}
