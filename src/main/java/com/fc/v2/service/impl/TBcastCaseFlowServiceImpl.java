package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.ITBcastCaseFlowService;

/**
 * 播出事故处置单台账 Service业务层处理（只查账）。
 *
 * <p>早先的 advance 一脚踏两档（st+2）、rollback 一步踢回首档、updateContent/remove
 * 对收口单也照改照删——那是与挪步出口并存的第二道口子，正是今年要关掉的那一条。
 * 如今本类只摆台账：立案、往前、往后、补料、收口、叫停一律没有，全归
 * {@link TBcastCaseFlowMoveServiceImpl} 独家。</p>
 *
 * <p>不继承 ServiceImpl：IService 的 save/updateById/removeById 是绕得过去的写口，
 * 台账这一层不许它开张。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastCaseFlowServiceImpl implements ITBcastCaseFlowService {

    @javax.annotation.Resource
    private TBcastCaseFlowMapper bcastCaseFlowMapper;

    @Override
    public TBcastCaseFlow selectTBcastCaseFlowById(Long id) {
        return this.bcastCaseFlowMapper.selectOne(new QueryWrapper<TBcastCaseFlow>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    /**
     * 台账只摆库里确有的单：递进来的条件照用，只补一条删除标记。
     * 收口单不另设状态过滤——年末要翻它追当年凭什么结、凭什么终止。
     */
    @Override
    public List<TBcastCaseFlow> selectTBcastCaseFlowList(Wrapper<TBcastCaseFlow> queryWrapper) {
        QueryWrapper<TBcastCaseFlow> wrapper;
        if (queryWrapper instanceof QueryWrapper) {
            wrapper = (QueryWrapper<TBcastCaseFlow>) queryWrapper;
        } else {
            wrapper = new QueryWrapper<TBcastCaseFlow>();
        }
        wrapper.eq("del_flag", 0);
        return this.bcastCaseFlowMapper.selectList(wrapper);
    }
}
