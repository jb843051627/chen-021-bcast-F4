package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.model.auto.TBcastCaseFlowLog;

/**
 * 播出事故处置单——服务层那一个挪步出口。
 *
 * <p>四样步次结论一律只从这一个出口出：这张单归在第几步（{@link #currentStage}）、
 * 后一步收不收它（{@link #canMoveForward}/{@link #moveForward}）、收了口还改不改得动
 * （{@link #supplement}/{@link #reviseWords}/{@link #cancel}）、同一步第二遍落不落第二笔
 * （{@link #reportWords}）。除了这一处，别处不给步次结论；主单纸面临的 stage、接口递
 * 上来的步次都只算意图，挪步出口数出来的与卷面回读对不上时，按出口这一份走。</p>
 *
 * <p>读步次的方法只认主键一个参数，签名不动；推进不沿用老的 advance 写法，由
 * {@link #moveForward} 另起名字承接。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public interface IBcastCaseFlowMoveService {

    /** 立案：先立一张在第一步（发现核实）的活单，落头一段记载。同一起事故一张活单为止。 */
    TBcastCaseFlow openCase(TBcastCaseFlow record, String firstWords);

    /**
     * 读步次：这张单归在第几步（0..3）。第几步由系统数已经过的步数得出
     * （往前段数减往后段数），纸面写的与数出来的对不上时以数出来的为准。
     * 签名照旧，只认主键，不加参数。查无此单返回 null。
     */
    Integer currentStage(Long id);

    /** 后一步收不收它：按当前步次那一道门槛当面评，不动单子。收口的、步次到头的不收。 */
    boolean canMoveForward(Long id);

    /** 往前：只进紧挨着的后一步；门槛没过抛 BcastCaseMoveException。另起的推进写法。 */
    TBcastCaseFlow moveForward(Long id, String words);

    /** 往后：一次退一步，退回那一步原先的话沉底，重走从这一步重新攒；已在第一步不退。 */
    TBcastCaseFlow moveBack(Long id, String words);

    /**
     * 同一步二次上报：卷上留的还是首次那句。当前步头一句还空着就补在原段上，
     * 已经有话了第二回不添一行，返回 false。
     */
    boolean reportWords(Long id, String words);

    /**
     * 上报结案：落省局结论。落在“不必处置/就地终止”两说的，在哪一步都当场收口锁住；
     * 其余结论须已走到上报结案（第4步）才收口。结论没落、对收口单再落，一律拒。
     */
    TBcastCaseFlow conclude(Long id, String provinceResult);

    /** 在办单补门槛材料（台站代号、起止时刻、户数、抢险几笔、应播够钟点）；收口的一律不进。 */
    TBcastCaseFlow supplement(Long id, TBcastCaseFlow patch);

    /** 改卷面上的话：收了口的改不动，返回 false。 */
    boolean reviseWords(Long id, String words);

    /** 中途叫停：只软删在办活单，腾出活单名额；收了口的整张删不掉，返回 0。 */
    int cancel(Long id);

    /** 当场逐张点出来的在册活单（随立案加一、随收口/叫停减一）；张数就照这一份点。 */
    List<TBcastCaseFlow> activeCases();

    /** 在册处置单张数：与 {@link #activeCases()} 同一次清点、同一个数，不分开数两回。 */
    int activeCount();

    /** 倒着清点：从上报结案倒回发现核实逐段返回，抽任何一段单看都有步次、动作、原话、时刻。 */
    List<TBcastCaseFlowLog> replay(Long id);
}
