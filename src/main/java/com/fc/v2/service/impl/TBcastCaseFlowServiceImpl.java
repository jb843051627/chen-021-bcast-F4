package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.service.ITBcastCaseFlowService;

/**
 * 播出事故处置单 Service业务层处理（state-machine 形状：单据流转）
 *
 * <p>前年那次转播停播，纸面已经写到恢复确认、系统里还挂在处置抢险：恢复回报自己
 * 往前推、值班主任按老规矩伸手一点，两条路谁手快便听谁的。今年把老路关掉，只留
 * 本类这一个挪步出口——归在第几步、后一步收不收、收了口还改不改得动、同一步
 * 第二遍落不落第二笔，四个说法全出在 {@link #moveGate} 这一处；纸面写的步次与
 * 接口递上来的步次都只算意图，对不上时以系统数出来的为准。旧的 advance（一推
 * 两档）与 rollback（一退到底）两条老路就此关掉，不再承接。</p>
 *
 * <p>一段一段的记载落在 content 里，一行一段：{@code 第k步|落在哪一刻|记了什么话}。
 * 退一步时那一段冠上"沉"字压到卷尾（沉到底下，不抹掉）；落在"不必处置"或
 * "就地终止"那两说的，冠上"收"字当场收了口。第几步由系统数在册段（第k步那几行）
 * 得出来，不凭卷面回读的步次。</p>
 *
 * <p>不继承 ServiceImpl：IService 那一套 save/updateById/removeById 是绕得过去的
 * 第二道口子，收口规矩不许它开张。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBcastCaseFlowServiceImpl implements ITBcastCaseFlowService {

    private static final Logger log = LoggerFactory.getLogger(TBcastCaseFlowServiceImpl.class);

    /** 四步之一：发现核实 */
    private static final int STAGE_VERIFY = 0;
    /** 四步之二：处置抢险 */
    private static final int STAGE_RESCUE = 1;
    /** 四步之三：恢复确认 */
    private static final int STAGE_RECOVER = 2;
    /** 四步之四：上报结案 */
    private static final int STAGE_CLOSE = 3;
    private static final int MAX_STAGE = STAGE_CLOSE;

    /** 处置会落：在办（旧单空着的也按在办容下） */
    private static final int STATUS_ACTIVE = 1;
    /** 处置会落：已收了口（当场锁住：字修改不了、整张删不掉、事后也挪不动） */
    private static final int STATUS_CLOSED = 2;

    /**
     * 恢复确认守着的钟点：恢复之后要连着正常播够，当地定下的那几个钟点。
     * 钟点没走满，材料递得再齐也不算。调限改这里。
     */
    private static final double RECOVERY_WATCH_HOURS = 2;

    /** 省局那两说：落在哪一说，这张单都算收了口 */
    private static final String[] TERMINAL_WORDS = {"不必处置", "就地终止"};

    /** 段记号：沉底段（原先记载的话沉到底下，不抹掉） */
    private static final String SEG_SUNK = "沉|";
    /** 段记号：收口段（凭什么结的、又凭什么终止的，年末就凭这一段追） */
    private static final String SEG_SEAL = "收|";
    /** 在册段的落法：第k步|时刻|话 */
    private static final Pattern SEG_ACTIVE_LINE = Pattern.compile("^第[0-3]步\\|");

    /** 连着正常播够那一笔的认法："正常播出3小时"这样的落法 */
    private static final Pattern NORMAL_HOURS =
            Pattern.compile("正常[^0-9]{0,8}(\\d+(?:\\.\\d+)?)\\s*(?:个?小时|钟头|钟点)");

    /** 一段话落在段里的篇幅上限：全话另有值班簿与操作日志在，段里留要 */
    private static final int SEG_WORDS_MAX = 40;

    @javax.annotation.Resource
    private TBcastCaseFlowMapper bcastCaseFlowMapper;

    /**
     * 读步次：签名照旧。归在第几步以出口数出来的为准——卷面回读（stage 那一栏）
     * 只算纸面，对不上时按出口这一份走。
     */
    @Override
    public TBcastCaseFlow selectTBcastCaseFlowById(Long id) {
        if (id == null) {
            return null;
        }
        return healStage(this.bcastCaseFlowMapper.selectById(id));
    }

    @Override
    public List<TBcastCaseFlow> selectTBcastCaseFlowList(QueryWrapper<TBcastCaseFlow> queryWrapper) {
        List<TBcastCaseFlow> rows = this.bcastCaseFlowMapper.selectList(queryWrapper);
        for (TBcastCaseFlow r : rows) {
            healStage(r);
        }
        return rows;
    }

    /**
     * 立案。同一起事故底下一张活单为止：头一张没办结、也没中途叫停（没收了口）之前，
     * 后一张立不起来。代号之外的栏一律由系统落笔：从发现核实起、在办、段记载空着，
     * 人递进来的步次与记载只算意图，不入库。
     */
    @Override
    public int insertTBcastCaseFlow(TBcastCaseFlow record) {
        if (record == null) {
            return 0;
        }
        String bizNo = record.getBizNo() == null ? "" : record.getBizNo().trim();
        if (bizNo.isEmpty()) {
            return 0;
        }
        Integer inflight = this.bcastCaseFlowMapper.selectCount(new QueryWrapper<TBcastCaseFlow>()
                .eq("biz_no", bizNo)
                .eq("del_flag", 0)
                .and(w -> w.isNull("status").or().ne("status", STATUS_CLOSED)));
        if (inflight != null && inflight.intValue() > 0) {
            log.info("事故{}头一张单还没收了口，后一张立不起来", bizNo);
            return 0;
        }
        record.setBizNo(bizNo);
        // 眼下归在第几步由系统自己数：立案一律从发现核实起
        record.setStage(Integer.valueOf(STAGE_VERIFY));
        record.setStatus(Integer.valueOf(STATUS_ACTIVE));
        // 一段一段的记载只由挪步出口落笔，立案时不给手填留位置
        record.setContent(null);
        record.setLastAction("立案");
        record.setDelFlag(Integer.valueOf(0));
        return this.bcastCaseFlowMapper.insert(record);
    }

    /** 挪步出口·往前：只进紧挨着的后一步 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBcastCaseFlow moveForward(Long id, Integer intentStage, String report) {
        return moveGate(id, intentStage, report, true);
    }

    /** 挪步出口·往后：一次退一步 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBcastCaseFlow moveBackward(Long id, String report) {
        return moveGate(id, null, report, false);
    }

    /**
     * 在册张数：随立案加一、随结案减一。与当场逐步点出来的那一个合得上——
     * 因为它就是当场那一遍点出来的：从库里一次把单取齐、逐张点过去，不分两回数。
     */
    @Override
    public int countOnFile() {
        List<TBcastCaseFlow> rows = this.bcastCaseFlowMapper.selectList(
                new QueryWrapper<TBcastCaseFlow>().eq("del_flag", 0));
        int n = 0;
        for (TBcastCaseFlow r : rows) {
            if (!isClosed(r)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 改卷面批注。收了口的当场锁住，卷面上的字修改不了；在办的也只动批注这一栏——
     * 一段一段的记载只由挪步出口落笔，别处碰不着段，也就开不出第二处挪步次的口子。
     */
    @Override
    public boolean updateContent(Long id, String remark) {
        if (id == null) {
            return false;
        }
        TBcastCaseFlow cur = this.bcastCaseFlowMapper.selectById(id);
        if (cur == null) {
            return false;
        }
        if (isClosed(cur)) {
            log.info("处置单{}已收了口，卷面上的字修改不了", id);
            return false;
        }
        cur.setRemark(remark);
        return this.bcastCaseFlowMapper.updateById(cur) > 0;
    }

    /**
     * 撤单。收了口的当场锁住，整张也删不掉——年末要追那一段当年凭什么结的、
     * 又凭什么终止的，还得凭它。在办的（含旧单）可撤，名额随即空出来。
     */
    @Override
    public boolean remove(Long id) {
        if (id == null) {
            return false;
        }
        TBcastCaseFlow cur = this.bcastCaseFlowMapper.selectById(id);
        if (cur == null) {
            return false;
        }
        if (isClosed(cur)) {
            log.info("处置单{}已收了口，整张删不掉", id);
            return false;
        }
        return this.bcastCaseFlowMapper.deleteById(id) > 0;
    }

    // ================= 挪步出口：往前往后都只有这一个口子 =================

    /**
     * 挪步出口。四个说法全出在这一处：归在第几步（系统数已经过的步数）、
     * 后一步收不收（这一步的门槛）、收了口还改不改得动（当场锁住）、
     * 同一步第二遍落不落第二笔（首次那句留着，第二回不添行）。
     */
    private TBcastCaseFlow moveGate(Long id, Integer intentStage, String report, boolean forward) {
        if (id == null) {
            return null;
        }
        TBcastCaseFlow cur = this.bcastCaseFlowMapper.selectById(id);
        if (cur == null) {
            return null;
        }
        // 收了口的当场锁住：事后也挪不动
        if (isClosed(cur)) {
            log.info("处置单{}已收了口，挪不动", id);
            return null;
        }
        // 第几步由系统数已经过的步数得出来：纸面写的与数出来的对不上时，以数出来的为准
        int stage = deriveStage(cur.getContent());
        String words = report == null ? "" : report.trim();

        if (forward) {
            // 落在"不必处置"或"就地终止"那两说的，这张单也算收了口：当场锁住
            String terminal = terminalWord(words);
            if (terminal != null) {
                appendSegment(cur, SEG_SEAL, stage, words);
                cur.setStage(Integer.valueOf(stage));
                cur.setStatus(Integer.valueOf(STATUS_CLOSED));
                cur.setLastAction("落在" + terminal + "，当场收了口");
                this.bcastCaseFlowMapper.updateById(cur);
                return cur;
            }
            // 同一步骤二次上报：卷上留下的还是首次那句，第二回不再添一行。
            // 接口递上来的步次只算意图，就用来认出这是第几遍；挪步不凭它
            if (intentStage != null && intentStage.intValue() >= 0 && intentStage.intValue() < stage) {
                log.info("处置单{}第{}步早有记载，第二回上报不添行", id, intentStage);
                return healStage(cur);
            }
            // 每一步有自己的门槛：门槛不过，材料递得再齐也不算，后一步开着也不能进
            if (!gatePass(stage, words)) {
                log.info("处置单{}第{}步门槛没过，进不了后一步", id, Integer.valueOf(stage));
                return null;
            }
            // 只进紧挨着的后一步
            appendSegment(cur, "", stage, words);
            int next = Math.min(stage + 1, MAX_STAGE);
            cur.setStage(Integer.valueOf(next));
            if (stage == MAX_STAGE) {
                // 上报结案那一步省局的结论落了：这张单收了口，当场锁住
                cur.setStatus(Integer.valueOf(STATUS_CLOSED));
                cur.setLastAction("上报结案，收了口");
            } else {
                cur.setLastAction("往前挪到第" + next + "步");
            }
            this.bcastCaseFlowMapper.updateById(cur);
            return cur;
        }

        // 往后：一次退一步；已经停在第一步的不再往后收
        if (stage <= STAGE_VERIFY) {
            log.info("处置单{}已经停在第一步，不再往后收", id);
            return null;
        }
        // 退回来那一步原先记载的话沉到底下：不抹掉；重走时从这一步重新攒
        sinkLastSegment(cur);
        cur.setStage(Integer.valueOf(stage - 1));
        cur.setLastAction("往后退回第" + (stage - 1) + "步" + (words.isEmpty() ? "" : "：" + abbrev(words, 20)));
        this.bcastCaseFlowMapper.updateById(cur);
        return cur;
    }

    // ================= 每一步的门槛 =================

    /**
     * 每一步有自己的门槛：
     * 发现核实要四样齐（台站代号、中断起于哪一刻、恢复到哪一刻、被影响到的户数）；
     * 处置抢险看抢险那几笔填没填全；恢复确认守着当地定的那几个钟点——恢复之后
     * 要连着正常播够，钟点没走满，材料递得再齐也不算；上报结案看省局的结论落没落。
     */
    private boolean gatePass(int stage, String words) {
        switch (stage) {
            case STAGE_VERIFY:
                return containsAll(words, "台站", "中断", "恢复", "户");
            case STAGE_RESCUE:
                return words.contains("抢险");
            case STAGE_RECOVER:
                return normalHours(words) >= RECOVERY_WATCH_HOURS;
            case STAGE_CLOSE:
                return words.contains("结论");
            default:
                return false;
        }
    }

    /** 连着正常播够那一笔：报了几处的，以报出来的最大那笔为准；没报按没走满算 */
    private double normalHours(String words) {
        double hours = 0;
        Matcher m = NORMAL_HOURS.matcher(words);
        while (m.find()) {
            try {
                hours = Math.max(hours, Double.parseDouble(m.group(1)));
            } catch (NumberFormatException e) {
                // 认不出的那一笔不算数
            }
        }
        return hours;
    }

    private String terminalWord(String words) {
        for (String w : TERMINAL_WORDS) {
            if (words.contains(w)) {
                return w;
            }
        }
        return null;
    }

    private boolean containsAll(String words, String... keys) {
        for (String k : keys) {
            if (!words.contains(k)) {
                return false;
            }
        }
        return true;
    }

    // ================= 一段一段的记载 =================

    /**
     * 归在第几步：数在册段（第k步那几行）得出来，已经过的步数就是眼下这一步；
     * 沉底的、收口的都不算在册段。旧单卷面空着的，按第一步容下。
     */
    private int deriveStage(String content) {
        if (content == null || content.trim().isEmpty()) {
            return STAGE_VERIFY;
        }
        int passed = 0;
        for (String line : content.split("\n")) {
            if (SEG_ACTIVE_LINE.matcher(line).find()) {
                passed++;
            }
        }
        return Math.min(passed, MAX_STAGE);
    }

    /**
     * 落一段：哪一步、落在哪一刻、记了什么话。同一刹那里两条一起进来的，
     * 分开各记各的，既不归并也不许漏记。新段落压在沉底段前头，沉底的照旧压在卷尾。
     */
    private void appendSegment(TBcastCaseFlow cur, String mark, int stage, String words) {
        String line = mark + "第" + stage + "步|" + stamp() + "|" + abbrev(words, SEG_WORDS_MAX);
        List<String> lines = splitLines(cur.getContent());
        int at = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(SEG_SUNK)) {
                at = i;
                break;
            }
        }
        lines.add(at, line);
        cur.setContent(join(lines));
    }

    /** 退回来那一步原先记载的话沉到底下：冠上沉字压到卷尾，不抹掉 */
    private void sinkLastSegment(TBcastCaseFlow cur) {
        List<String> lines = splitLines(cur.getContent());
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (SEG_ACTIVE_LINE.matcher(lines.get(i)).find()) {
                String sunk = lines.remove(i);
                lines.add(SEG_SUNK + sunk);
                break;
            }
        }
        cur.setContent(join(lines));
    }

    /** 出口给出的步次与卷面回读不一致时，按出口这一份走 */
    private TBcastCaseFlow healStage(TBcastCaseFlow r) {
        if (r != null) {
            r.setStage(Integer.valueOf(deriveStage(r.getContent())));
        }
        return r;
    }

    private boolean isClosed(TBcastCaseFlow r) {
        return r.getStatus() != null && r.getStatus().intValue() == STATUS_CLOSED;
    }

    private String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    /** 一段话落在段里的篇幅：超出的一律截住，全话另有值班簿与操作日志在 */
    private String abbrev(String words, int max) {
        if (words == null) {
            return "";
        }
        return words.length() <= max ? words : words.substring(0, max);
    }

    private List<String> splitLines(String content) {
        List<String> lines = new ArrayList<String>();
        if (content != null) {
            for (String line : content.split("\n")) {
                if (!line.trim().isEmpty()) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private String join(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString();
    }
}
