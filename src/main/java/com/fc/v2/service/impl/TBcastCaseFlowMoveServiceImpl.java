package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BcastCaseMoveException;
import com.fc.v2.mapper.auto.TBcastCaseFlowLogMapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.model.auto.TBcastCaseFlowLog;
import com.fc.v2.service.IBcastCaseFlowMoveService;
import com.fc.v2.util.StringUtils;

/**
 * 播出事故处置单挪步出口（唯一一处）。
 *
 * <p>一张处置单合不合规、眼下算第几步、后一步收不收、收了口还动不动得了、同一步第二遍
 * 落不落第二笔，一律由本类这几条方法独家定调。控制器与旧台账服务只查不改，屏上不留第二
 * 处能挪步次的把手。</p>
 *
 * <p><b>步次怎么数。</b>纸面主单上的 stage 只算意图，不作准。作准的步次由逐段记载
 * （t_bcast_case_flow_log）里已经过的步数数出来：往前（FORWARD）加一、往后（BACK）减一，
 * 立案从发现核实（0）起。卷面回读与这里数出来的对不上，按这里这一份走。</p>
 *
 * <p><b>两道往前的来路。</b>早年一条照着恢复回报自己往前推、一条值班主任按老规矩伸手
 * 一点，两头话对不上、谁手快听谁的。如今两道都并到 {@link #moveForward} 这一个口子，
 * 别处不开推进；老的 advance 写法已撤。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
@Service
public class TBcastCaseFlowMoveServiceImpl implements IBcastCaseFlowMoveService {

    // ---- 步次：0 发现核实 / 1 处置抢险 / 2 恢复确认 / 3 上报结案 ----
    private static final int STAGE_DISCOVER = 0;
    private static final int STAGE_RESCUE = 1;
    private static final int STAGE_RECOVER = 2;
    private static final int STAGE_REPORT = 3;

    // ---- 会落：1 在办 / 2 已收了口（立案即落在办；0 未起为纸面旧档，本流程不产生） ----
    private static final int STATUS_ACTIVE = 1;
    private static final int STATUS_TERMINAL = 2;

    // ---- 段段记载的种类 ----
    private static final String ACT_OPEN = "OPEN";
    private static final String ACT_FORWARD = "FORWARD";
    private static final String ACT_BACK = "BACK";
    private static final String ACT_TERMINAL = "TERMINAL";

    /** 省局两说了，落在哪一步都当场收口：不必处置 */
    private static final String RESULT_NO_NEED = "不必处置";
    /** 省局两说了，落在哪一步都当场收口：就地终止 */
    private static final String RESULT_TERMINATE = "就地终止";

    @javax.annotation.Resource
    private TBcastCaseFlowMapper bcastCaseFlowMapper;

    @javax.annotation.Resource
    private TBcastCaseFlowLogMapper bcastCaseFlowLogMapper;

    // ================= 立案 =================

    /**
     * 立案。同一起事故底下一张活单为止：头一张没办结、也没中途叫停之前，后一张立不起来。
     * 新单落在发现核实（第一步），四样门槛、抢险几笔先空着，随立案落头一段记载。
     */
    @Override
    public TBcastCaseFlow openCase(TBcastCaseFlow record, String firstWords) {
        if (record == null) {
            return null;
        }
        // 活单名额：在册在办的还有一张，后一张就立不起来（办结/收口的不占名额）
        if (activeCount() > 0) {
            return null;
        }
        record.setBizNo(genBizNo());
        // 步次是数出来的，不是人填的：纸面一律先落第一步，之后随挪步出口走
        record.setStage(Integer.valueOf(STAGE_DISCOVER));
        record.setStatus(Integer.valueOf(STATUS_ACTIVE));
        record.setLastAction(ACT_OPEN);
        record.setDelFlag(Integer.valueOf(0));
        this.bcastCaseFlowMapper.insert(record);

        // 立案即落头一段：发现核实、OPEN、头一句话、这一刻。与挪步段同一张台账点数
        appendLog(record.getId(), STAGE_DISCOVER, ACT_OPEN, firstWords);
        syncPaper(record.getId(), STAGE_DISCOVER, ACT_OPEN);
        return record;
    }

    // ================= 读步次：签名照旧，不加参数 =================

    /**
     * 这张单归在第几步。第几步由系统数已经过的步数得出：FORWARD 段数减 BACK 段数，
     * 立案从 0 起。纸面 stage 与接口递来的步次都不作数；查无此单返回 null。
     */
    @Override
    public Integer currentStage(Long id) {
        if (id == null) {
            return null;
        }
        TBcastCaseFlow r = loadLive(id);
        if (r == null) {
            return null;
        }
        return Integer.valueOf(countStage(listLogs(id)));
    }

    // ================= 往前 =================

    /** 后一步收不收它：门槛当面评，不动单子。 */
    @Override
    public boolean canMoveForward(Long id) {
        TBcastCaseFlow r = loadLive(id);
        if (r == null) {
            return false;
        }
        int stage = countStage(listLogs(id));
        if (isTerminal(r) || stage >= STAGE_REPORT) {
            return false;
        }
        return gatePassed(r, stage);
    }

    /**
     * 往前：只进紧挨着的后一步。第几步由系统数，不看纸面；每一步有自己的门槛，
     * 钟点没走满，材料递得再齐也不算。进档记一段 FORWARD（踏进的是后一步）。
     */
    @Override
    public TBcastCaseFlow moveForward(Long id, String words) {
        TBcastCaseFlow r = requireLive(id);
        int stage = countStage(listLogs(id));
        if (isTerminal(r)) {
            throw new BcastCaseMoveException("这张单已经收了口，挪不动了");
        }
        if (stage >= STAGE_REPORT) {
            throw new BcastCaseMoveException("已经到上报结案这一步，前头没有再挨着的一步了");
        }
        // 门槛没过，后一步开着也不能进
        checkGate(r, stage);
        int next = stage + 1;
        appendLog(id, next, ACT_FORWARD, words);
        syncPaper(id, next, ACT_FORWARD);
        return loadLive(id);
    }

    // ================= 往后 =================

    /**
     * 往后：一次退一步。退回来那一步原先记载的话沉到底下（不抹，留在台账里），
     * 重走时从这一步重新攒。已经停在第一步的不再往后收。
     */
    @Override
    public TBcastCaseFlow moveBack(Long id, String words) {
        TBcastCaseFlow r = requireLive(id);
        int stage = countStage(listLogs(id));
        if (isTerminal(r)) {
            throw new BcastCaseMoveException("这张单已经收了口，退不动了");
        }
        if (stage <= STAGE_DISCOVER) {
            // 已在第一步，不再往后收：原样退回，不记段、不改单
            return r;
        }
        int prev = stage - 1;
        appendLog(id, prev, ACT_BACK, words);
        syncPaper(id, prev, ACT_BACK);
        return loadLive(id);
    }

    // ================= 同一步上报 =================

    /**
     * 同一步骤二次上报：卷上留下的还是首次那句，第二回不再添一行。
     * 当前步头一句（该步最近一段 FORWARD/OPEN 的话）还空着，就补在原段上；
     * 已经有话了，第二回原样退回 false。
     */
    @Override
    public boolean reportWords(Long id, String words) {
        if (StringUtils.isEmpty(words)) {
            return false;
        }
        TBcastCaseFlow r = requireLive(id);
        if (isTerminal(r)) {
            return false;
        }
        int stage = countStage(listLogs(id));
        List<TBcastCaseFlowLog> logs = listLogs(id);
        TBcastCaseFlowLog holder = latestEntryOfStage(logs, stage);
        if (holder == null) {
            return false;
        }
        // 卷上已有首次那句：第二回不添一行、不改一字
        if (!StringUtils.isEmpty(holder.getContent())) {
            return false;
        }
        holder.setContent(words);
        this.bcastCaseFlowLogMapper.updateById(holder);
        return true;
    }

    // ================= 上报结案 / 收口 =================

    /**
     * 落省局结论。落在“不必处置/就地终止”两说的，这张单也算收了口，在哪一步都当场锁；
     * 其余结论要已走到上报结案（第4步）、并把结论落下才收口。结论没落不收口。
     */
    @Override
    public TBcastCaseFlow conclude(Long id, String provinceResult) {
        if (StringUtils.isEmpty(provinceResult)) {
            throw new BcastCaseMoveException("省局的结论没落，上报结案收不了口");
        }
        TBcastCaseFlow r = requireLive(id);
        if (isTerminal(r)) {
            throw new BcastCaseMoveException("这张单已经收了口，结论落不进去了");
        }
        int stage = countStage(listLogs(id));
        boolean earlyClose = RESULT_NO_NEED.equals(provinceResult.trim())
                || RESULT_TERMINATE.equals(provinceResult.trim());
        if (!earlyClose && stage < STAGE_REPORT) {
            throw new BcastCaseMoveException("还没走到上报结案这一步，结论先收不下");
        }

        UpdateWrapper<TBcastCaseFlow> uw = new UpdateWrapper<TBcastCaseFlow>()
                .eq("id", id).eq("del_flag", 0);
        uw.set("province_result", provinceResult.trim());
        uw.set("status", STATUS_TERMINAL);
        uw.set("last_action", ACT_TERMINAL);
        uw.set("update_time", new Date());
        this.bcastCaseFlowMapper.update(null, uw);
        // 收口也落一段：年末要追当年凭什么结、凭什么终止，凭的就是这一段
        appendLog(id, stage, ACT_TERMINAL, provinceResult.trim());
        return loadLive(id);
    }

    // ================= 补材料 / 改字 / 叫停 =================

    /** 在办单补门槛材料；收了口的材料递得再齐也不进。 */
    @Override
    public TBcastCaseFlow supplement(Long id, TBcastCaseFlow patch) {
        if (patch == null) {
            return null;
        }
        TBcastCaseFlow r = requireLive(id);
        if (isTerminal(r)) {
            throw new BcastCaseMoveException("这张单已经收了口，材料补不进去了");
        }
        UpdateWrapper<TBcastCaseFlow> uw = new UpdateWrapper<TBcastCaseFlow>()
                .eq("id", id).eq("del_flag", 0);
        boolean touched = false;
        if (patch.getSiteCode() != null) {
            uw.set("site_code", patch.getSiteCode());
            touched = true;
        }
        if (patch.getOutageAt() != null) {
            uw.set("outage_at", patch.getOutageAt());
            touched = true;
        }
        if (patch.getRecoverAt() != null) {
            uw.set("recover_at", patch.getRecoverAt());
            touched = true;
        }
        if (patch.getAffectedCount() != null) {
            uw.set("affected_count", patch.getAffectedCount());
            touched = true;
        }
        if (patch.getRescueNote() != null) {
            uw.set("rescue_note", patch.getRescueNote());
            touched = true;
        }
        if (patch.getNormalHours() != null) {
            uw.set("normal_hours", patch.getNormalHours());
            touched = true;
        }
        if (!touched) {
            return r;
        }
        uw.set("update_time", new Date());
        this.bcastCaseFlowMapper.update(null, uw);
        return loadLive(id);
    }

    /** 卷面上的话：收了口当场锁住，字修改不了，返回 false。 */
    @Override
    public boolean reviseWords(Long id, String words) {
        if (StringUtils.isEmpty(words)) {
            return false;
        }
        TBcastCaseFlow r = requireLive(id);
        if (isTerminal(r)) {
            return false;
        }
        UpdateWrapper<TBcastCaseFlow> uw = new UpdateWrapper<TBcastCaseFlow>()
                .eq("id", id).eq("del_flag", 0)
                .set("content", words)
                .set("update_time", new Date());
        return this.bcastCaseFlowMapper.update(null, uw) > 0;
    }

    /** 中途叫停：只软删在办活单，名额随即空出；收了口的整张删不掉。 */
    @Override
    public int cancel(Long id) {
        TBcastCaseFlow r = this.bcastCaseFlowMapper.selectById(id);
        if (r == null || (r.getDelFlag() != null && r.getDelFlag().intValue() == 1)) {
            return 0;
        }
        if (isTerminal(r)) {
            return 0;
        }
        UpdateWrapper<TBcastCaseFlow> uw = new UpdateWrapper<TBcastCaseFlow>()
                .eq("id", id)
                .set("del_flag", 1)
                .set("update_time", new Date());
        return this.bcastCaseFlowMapper.update(null, uw);
    }

    // ================= 计数与清点 =================

    /**
     * 在册活单当场逐张点出来。张数（{@link #activeCount()}）就照这一份点，
     * 与逐步点出来的是同一次清点，不分两回数。
     */
    @Override
    public List<TBcastCaseFlow> activeCases() {
        return this.bcastCaseFlowMapper.selectList(new QueryWrapper<TBcastCaseFlow>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE)
                .orderByAsc("id"));
    }

    /**
     * 在册张数：随立案加一、随收口/叫停减一。不另开一条 selectCount 分两回数——
     * 就从 {@link #activeCases()} 当场逐张点上来的那一份里数，二者天然合得上。
     */
    @Override
    public int activeCount() {
        return activeCases().size();
    }

    /** 倒着数回发现核实：逐段返回（最新在上），段数与值班簿那几笔合得拢。 */
    @Override
    public List<TBcastCaseFlowLog> replay(Long id) {
        if (loadLive(id) == null) {
            return Collections.emptyList();
        }
        List<TBcastCaseFlowLog> logs = listLogs(id);
        Collections.reverse(logs);
        return logs;
    }

    // ================= 四步门槛 =================

    /** 当前步要往后一步迈，先看这一步的门槛凑没凑齐。 */
    private void checkGate(TBcastCaseFlow r, int stage) {
        if (stage == STAGE_DISCOVER) {
            // 发现核实四样齐：台站代号、中断起于哪一刻、恢复到哪一刻、被影响户数
            List<String> missing = new ArrayList<String>();
            if (StringUtils.isEmpty(r.getSiteCode())) {
                missing.add("台站代号");
            }
            if (r.getOutageAt() == null) {
                missing.add("中断起于哪一刻");
            }
            if (r.getRecoverAt() == null) {
                missing.add("恢复到哪一刻");
            }
            if (r.getAffectedCount() == null) {
                missing.add("被影响到的户数");
            }
            if (!missing.isEmpty()) {
                throw new BcastCaseMoveException("发现核实四样没齐，还差：" + String.join("、", missing));
            }
        } else if (stage == STAGE_RESCUE) {
            // 处置抢险：看抢险那几笔填没填全
            if (StringUtils.isEmpty(r.getRescueNote())) {
                throw new BcastCaseMoveException("处置抢险那几笔还没填全，后一步收不下");
            }
        } else if (stage == STAGE_RECOVER) {
            // 恢复确认：守着当地定的钟点——恢复之后连着正常播够，钟点没走满不算
            if (r.getNormalHours() == null || r.getRecoverAt() == null) {
                throw new BcastCaseMoveException("恢复确认的钟点没守着：应播够的钟点或恢复时刻空着");
            }
            if (r.getNormalHours().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BcastCaseMoveException("恢复确认的钟点没守着：应播够的钟点得是正数");
            }
            long elapsedMs = System.currentTimeMillis() - r.getRecoverAt().getTime();
            long requiredMs = r.getNormalHours().multiply(BigDecimal.valueOf(3600000L)).longValue();
            if (elapsedMs < requiredMs) {
                throw new BcastCaseMoveException("恢复后连着正常播的钟点还没走满，材料再齐也不算");
            }
        }
        // 上报结案是最后一步，没有再往后的门槛；收口由 conclude 凭省局结论落
    }

    private boolean gatePassed(TBcastCaseFlow r, int stage) {
        try {
            checkGate(r, stage);
            return true;
        } catch (BcastCaseMoveException e) {
            return false;
        }
    }

    // ================= 台账与步次的家底 =================

    /** 数已经过的步数：FORWARD 加一、BACK 减一，OPEN 不挪。立案从 0 起。 */
    private int countStage(List<TBcastCaseFlowLog> logs) {
        int stage = STAGE_DISCOVER;
        for (TBcastCaseFlowLog log : logs) {
            String act = log.getAction();
            if (ACT_FORWARD.equals(act)) {
                stage++;
            } else if (ACT_BACK.equals(act)) {
                stage--;
            }
        }
        return stage;
    }

    /** 这一步最近一次“进门”的段（OPEN/FORWARD），同一步二次上报就认它那句首次话。 */
    private TBcastCaseFlowLog latestEntryOfStage(List<TBcastCaseFlowLog> logs, int stage) {
        for (int i = logs.size() - 1; i >= 0; i--) {
            TBcastCaseFlowLog log = logs.get(i);
            if (log.getStage() != null && log.getStage().intValue() == stage
                    && (ACT_OPEN.equals(log.getAction()) || ACT_FORWARD.equals(log.getAction()))) {
                return log;
            }
        }
        return null;
    }

    /** 同一刹那两条一起进来，各记各的：每条单独落一行，不归并。 */
    private void appendLog(Long caseId, int stage, String action, String content) {
        TBcastCaseFlowLog log = new TBcastCaseFlowLog();
        log.setCaseId(caseId);
        log.setStage(Integer.valueOf(stage));
        log.setAction(action);
        log.setContent(content);
        log.setCreateTime(new Date());
        this.bcastCaseFlowLogMapper.insert(log);
    }

    /**
     * 挪步出口给的步次落到纸面上：卷面 stage 只跟着出口走，出口与卷面不一致时以出口为准。
     */
    private void syncPaper(Long id, int stage, String action) {
        UpdateWrapper<TBcastCaseFlow> uw = new UpdateWrapper<TBcastCaseFlow>()
                .eq("id", id).eq("del_flag", 0)
                .set("stage", stage)
                .set("last_action", action)
                .set("update_time", new Date());
        this.bcastCaseFlowMapper.update(null, uw);
    }

    private List<TBcastCaseFlowLog> listLogs(Long id) {
        return this.bcastCaseFlowLogMapper.selectList(new QueryWrapper<TBcastCaseFlowLog>()
                .eq("case_id", id)
                .orderByAsc("id"));
    }

    private TBcastCaseFlow loadLive(Long id) {
        if (id == null) {
            return null;
        }
        return this.bcastCaseFlowMapper.selectOne(new QueryWrapper<TBcastCaseFlow>()
                .eq("id", id).eq("del_flag", 0));
    }

    private TBcastCaseFlow requireLive(Long id) {
        TBcastCaseFlow r = loadLive(id);
        if (r == null) {
            throw new BcastCaseMoveException("查无这张处置单（或已中途叫停）");
        }
        return r;
    }

    private boolean isTerminal(TBcastCaseFlow r) {
        return r.getStatus() != null && r.getStatus().intValue() == STATUS_TERMINAL;
    }

    /** 处置单代号由系统发出：CC+立案时刻+一段随机码。 */
    private String genBizNo() {
        return "CC" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
