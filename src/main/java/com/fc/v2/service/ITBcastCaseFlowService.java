package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TBcastCaseFlow;

/**
 * 播出事故处置单 Service接口（state-machine 形状：单据流转，不提供增删改查全套）
 *
 * <p>处置单四步：发现核实、处置抢险、恢复确认、上报结案。这张单归在第几步、
 * 后一步收不收它、收了口还改不改得动、同一步第二遍落不落第二笔——这四个说法
 * 一律出自本接口的挪步出口（{@link #moveForward} / {@link #moveBackward}），
 * 除此之外别的地方都不给步次结论：纸面写的步次与接口递上来的步次都只算意图，
 * 屏上也不留第二处能挪步次的把手。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBcastCaseFlowService {

    /**
     * 按主键回查单据（读步次的方法签名照旧，不加参数）。
     * 归在第几步以挪步出口数出来的为准：卷面回读与出口给出的对不上时，按出口那一份走。
     */
    TBcastCaseFlow selectTBcastCaseFlowById(Long id);

    /** 列表查询（流转台账）：每张单归在第几步同样以出口数出来的为准 */
    List<TBcastCaseFlow> selectTBcastCaseFlowList(QueryWrapper<TBcastCaseFlow> queryWrapper);

    /**
     * 立案。同一起事故底下一张活单为止：头一张没办结、也没中途叫停之前，
     * 后一张立不起来。在册张数随立案加一。代号、步次、会落、段记载一律由系统落笔，
     * 人递进来的那几样只算意图。
     */
    int insertTBcastCaseFlow(TBcastCaseFlow record);

    /**
     * 挪步出口·往前：只进紧挨着的后一步，第几步由系统数已经过的步数得出来。
     * intentStage 是接口递上来的步次，只算意图——用来认出"同一步骤二次上报"
     * （卷上留下的还是首次那句，第二回不再添一行），不作挪步依据。
     * 每一步有自己的门槛，门槛不过返回 null；收了口的挪不动，返回 null。
     */
    TBcastCaseFlow moveForward(Long id, Integer intentStage, String report);

    /**
     * 挪步出口·往后：一次退一步，退回来那一步原先记载的话沉到底下（不抹掉），
     * 重走时从这一步重新攒；已经停在第一步的不再往后收。被拒返回 null。
     */
    TBcastCaseFlow moveBackward(Long id, String report);

    /**
     * 在册的处置单张数：随立案加一、随结案减一。这个数与当场逐步点出来的那一个
     * 是同一回点出来的，不分两回数。
     */
    int countOnFile();

    /** 改卷面批注：一段一段的记载只由挪步出口落笔；收了口的改不动，被拒返回 false */
    boolean updateContent(Long id, String remark);

    /** 撤单：收了口的整张删不掉（年末还要凭它追那一段），被拒返回 false */
    boolean remove(Long id);
}
