package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BcastCaseMoveException;
import com.fc.v2.mapper.auto.TBcastCaseFlowLogMapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;
import com.fc.v2.model.auto.TBcastCaseFlowLog;

/**
 * 播出事故处置单：唯一挪步出口的单测。库用 mock 顶替，主单与逐段记载都落在内存篮子里，
 * 步次按段数真数、SET 真改，跟从库里回读一个意思。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TBcastCaseFlowMoveServiceImplTest {

    @Mock
    private TBcastCaseFlowMapper caseMapper;

    @Mock
    private TBcastCaseFlowLogMapper logMapper;

    @org.mockito.InjectMocks
    private TBcastCaseFlowMoveServiceImpl service;

    private final Map<Long, TBcastCaseFlow> cases = new LinkedHashMap<Long, TBcastCaseFlow>();
    private final List<TBcastCaseFlowLog> logs = new ArrayList<TBcastCaseFlowLog>();
    private final AtomicLong ids = new AtomicLong(1000);

    /** 列名 -> 实体字段名，SET 了哪一栏就真改到内存主单上 */
    private static final Map<String, String> COL_FIELD = new LinkedHashMap<String, String>();
    static {
        COL_FIELD.put("stage", "stage");
        COL_FIELD.put("status", "status");
        COL_FIELD.put("content", "content");
        COL_FIELD.put("last_action", "lastAction");
        COL_FIELD.put("del_flag", "delFlag");
        COL_FIELD.put("province_result", "provinceResult");
        COL_FIELD.put("site_code", "siteCode");
        COL_FIELD.put("outage_at", "outageAt");
        COL_FIELD.put("recover_at", "recoverAt");
        COL_FIELD.put("affected_count", "affectedCount");
        COL_FIELD.put("rescue_note", "rescueNote");
        COL_FIELD.put("normal_hours", "normalHours");
        COL_FIELD.put("update_time", "updateTime");
    }

    private static final Pattern SET_PAIR =
            Pattern.compile("(\\w+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)}");

    @BeforeEach
    void wire() {
        // ---- 主单 insert：补主键、进篮子 ----
        when(caseMapper.insert(any(TBcastCaseFlow.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                TBcastCaseFlow c = inv.getArgument(0);
                if (c.getId() == null) {
                    c.setId(Long.valueOf(ids.incrementAndGet()));
                }
                cases.put(c.getId(), c);
                return Integer.valueOf(1);
            }
        });
        when(caseMapper.selectById(any())).thenAnswer(new Answer<TBcastCaseFlow>() {
            @Override
            public TBcastCaseFlow answer(InvocationOnMock inv) {
                return cases.get(inv.getArgument(0));
            }
        });
        // ---- 主单 selectOne：认 wrapper 里压的 id，只回没叫停的 ----
        when(caseMapper.selectOne(any(Wrapper.class))).thenAnswer(new Answer<TBcastCaseFlow>() {
            @Override
            public TBcastCaseFlow answer(InvocationOnMock inv) {
                AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
                w.getSqlSegment(); // MP 3.4.2 懒填参数：先渲染才进 map
                for (Object v : w.getParamNameValuePairs().values()) {
                    for (TBcastCaseFlow c : cases.values()) {
                        if (String.valueOf(c.getId()).equals(String.valueOf(v))
                                && (c.getDelFlag() == null || c.getDelFlag().intValue() == 0)) {
                            return c;
                        }
                    }
                }
                return null;
            }
        });
        when(caseMapper.selectList(any(Wrapper.class))).thenAnswer(new Answer<List<TBcastCaseFlow>>() {
            @Override
            public List<TBcastCaseFlow> answer(InvocationOnMock inv) {
                List<TBcastCaseFlow> live = new ArrayList<TBcastCaseFlow>();
                for (TBcastCaseFlow c : cases.values()) {
                    if ((c.getDelFlag() == null || c.getDelFlag().intValue() == 0)
                            && c.getStatus() != null && c.getStatus().intValue() == 1) {
                        live.add(c);
                    }
                }
                return live;
            }
        });
        when(caseMapper.selectCount(any(Wrapper.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                int n = 0;
                for (TBcastCaseFlow c : cases.values()) {
                    if ((c.getDelFlag() == null || c.getDelFlag().intValue() == 0)
                            && c.getStatus() != null && c.getStatus().intValue() == 1) {
                        n++;
                    }
                }
                return Integer.valueOf(n);
            }
        });
        // ---- 主单 update：把 SET 真落到内存实体 ----
        when(caseMapper.update(any(), any(Wrapper.class))).thenAnswer(new Answer<Integer>() {
            @Override
            @SuppressWarnings("unchecked")
            public Integer answer(InvocationOnMock inv) {
                UpdateWrapper<TBcastCaseFlow> uw = inv.getArgument(1);
                uw.getSqlSet();
                uw.getSqlSegment(); // 触发 SET 与 WHERE 参数懒填
                Long id = null;
                for (Object v : uw.getParamNameValuePairs().values()) {
                    if (cases.containsKey(toLong(v))) {
                        id = toLong(v);
                    }
                }
                TBcastCaseFlow c = id == null ? null : cases.get(id);
                if (c == null) {
                    return Integer.valueOf(0);
                }
                Matcher m = SET_PAIR.matcher(uw.getSqlSet());
                while (m.find()) {
                    String field = COL_FIELD.get(m.group(1));
                    Object val = uw.getParamNameValuePairs().get(m.group(2));
                    if (field != null) {
                        setField(c, field, val);
                    }
                }
                return Integer.valueOf(1);
            }
        });

        // ---- 逐段记载 ----
        when(logMapper.insert(any(TBcastCaseFlowLog.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                TBcastCaseFlowLog l = inv.getArgument(0);
                if (l.getId() == null) {
                    l.setId(Long.valueOf(ids.incrementAndGet()));
                }
                logs.add(l);
                return Integer.valueOf(1);
            }
        });
        when(logMapper.selectList(any(Wrapper.class))).thenAnswer(new Answer<List<TBcastCaseFlowLog>>() {
            @Override
            public List<TBcastCaseFlowLog> answer(InvocationOnMock inv) {
                AbstractWrapper<?, ?, ?> w = inv.getArgument(0);
                w.getSqlSegment();
                Long caseId = null;
                for (Object v : w.getParamNameValuePairs().values()) {
                    if (v instanceof Number) {
                        caseId = ((Number) v).longValue();
                    }
                }
                List<TBcastCaseFlowLog> got = new ArrayList<TBcastCaseFlowLog>();
                for (TBcastCaseFlowLog l : logs) {
                    if (caseId != null && caseId.equals(l.getCaseId())) {
                        got.add(l);
                    }
                }
                return got;
            }
        });
        when(logMapper.updateById(any(TBcastCaseFlowLog.class))).thenAnswer(new Answer<Integer>() {
            @Override
            public Integer answer(InvocationOnMock inv) {
                return Integer.valueOf(1);
            }
        });
    }

    // ================= 家什 =================

    private TBcastCaseFlow baseCase() {
        TBcastCaseFlow c = new TBcastCaseFlow();
        return c;
    }

    /** 发现核实四样齐，恢复时刻落在 hoursAgo 前（兼顾恢复确认钟点） */
    private TBcastCaseFlow discoverPatch(long hoursAgo) {
        TBcastCaseFlow p = new TBcastCaseFlow();
        p.setSiteCode("TZ00");
        p.setOutageAt(new Date(System.currentTimeMillis() - (hoursAgo + 2) * 3600000L));
        p.setRecoverAt(new Date(System.currentTimeMillis() - hoursAgo * 3600000L));
        p.setAffectedCount(Long.valueOf(1200L));
        return p;
    }

    private Long openOne() {
        return service.openCase(baseCase(), "播出一中断，先立处置单").getId();
    }

    private long logsOf(Long caseId, String action) {
        long n = 0;
        for (TBcastCaseFlowLog l : logs) {
            if (caseId.equals(l.getCaseId()) && action.equals(l.getAction())) {
                n++;
            }
        }
        return n;
    }

    private Long toLong(Object v) {
        return v instanceof Number ? Long.valueOf(((Number) v).longValue()) : null;
    }

    private void setField(Object target, String field, Object val) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ================= 立案与活单名额 =================

    @Test
    void 立案落在发现核实_落头一段OPEN_在册活单加一() {
        Long id = openOne();

        assertEquals(Integer.valueOf(0), service.currentStage(id));
        assertEquals(1, logs.size());
        TBcastCaseFlowLog first = logs.get(0);
        assertEquals("OPEN", first.getAction());
        assertEquals(Integer.valueOf(0), first.getStage());
        assertEquals("播出一中断，先立处置单", first.getContent());
        assertEquals(1, service.activeCount());
        assertEquals(1, service.activeCases().size());
    }

    @Test
    void 头张活单没办结也没叫停_第二张立不起来() {
        openOne();
        assertNull(service.openCase(baseCase(), "又一起"));
        // 篮子里只有头一张，第二张没落库
        assertEquals(1, cases.size());
    }

    @Test
    void 中途叫停腾出名额_之后新单才立得起() {
        Long id = openOne();
        assertEquals(1, service.cancel(id));
        assertEquals(0, service.activeCount());
        // 叫停是软删，旧单还在篮子里，但新单立得起来
        assertTrue(service.openCase(baseCase(), "新的一起") != null);
        assertEquals(2, cases.size());
    }

    // ================= 读步次只认真数 =================

    @Test
    void 步次由系统数过的步数得出_纸面被改花了也以数出来的为准() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "四样齐，进处置抢险");

        // 卷面 stage 被人改花成 9，出口仍按段数数：只过了一步 -> 1
        cases.get(id).setStage(Integer.valueOf(9));
        assertEquals(Integer.valueOf(1), service.currentStage(id));
    }

    @Test
    void 查无此单读步次返回空() {
        assertNull(service.currentStage(Long.valueOf(9999)));
    }

    // ================= 四步门槛 =================

    @Test
    void 发现核实四样缺着_后一步开着也进不去_补齐才收() {
        Long id = openOne();
        assertFalse(service.canMoveForward(id));
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "想抢跑"));

        TBcastCaseFlow p = discoverPatch(10);
        p.setAffectedCount(null); // 户数缺一样
        service.supplement(id, p);
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "还差户数"));

        p.setAffectedCount(Long.valueOf(300L));
        service.supplement(id, p);
        assertTrue(service.canMoveForward(id));
        service.moveForward(id, "四样齐");
        assertEquals(Integer.valueOf(1), service.currentStage(id));
        assertEquals(1, logsOf(id, "FORWARD"));
    }

    @Test
    void 处置抢险那几笔没填全_恢复确认收不下() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "进处置抢险");
        assertFalse(service.canMoveForward(id));
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "抢险几笔空着"));

        TBcastCaseFlow p = new TBcastCaseFlow();
        p.setRescueNote("换板件、倒备用、复测三路信号");
        service.supplement(id, p);
        service.moveForward(id, "抢险几笔填全，进恢复确认");
        assertEquals(Integer.valueOf(2), service.currentStage(id));
    }

    @Test
    void 恢复确认守钟点_没走满材料再齐也不算_走满才进上报结案() {
        Long id = openOne();
        service.supplement(id, discoverParseRecoverOneHourAgo());
        service.moveForward(id, "进处置抢险");
        service.supplement(id, rescuePatch());
        service.moveForward(id, "进恢复确认");

        // 当地定恢复后须连播 24 个钟点，才过了 1 个钟点：走不满
        TBcastCaseFlow hours = new TBcastCaseFlow();
        hours.setNormalHours(new BigDecimal("24"));
        service.supplement(id, hours);
        assertFalse(service.canMoveForward(id));
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "钟点没走满"));

        // 把应播够的钟点改成 1：已经走满，收
        hours.setNormalHours(new BigDecimal("1"));
        service.supplement(id, hours);
        service.moveForward(id, "钟点走满，进上报结案");
        assertEquals(Integer.valueOf(3), service.currentStage(id));
    }

    private TBcastCaseFlow discoverParseRecoverOneHourAgo() {
        return discoverPatch(1);
    }

    private TBcastCaseFlow rescuePatch() {
        TBcastCaseFlow p = new TBcastCaseFlow();
        p.setRescueNote("换板件、倒备用");
        return p;
    }

    @Test
    void 往前只进紧挨着的后一步_一步踏不过两档() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "进处置抢险");
        // 已在处置抢险，抢险几笔空着：不可能直接蹦到恢复确认
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "想蹦一档"));
        assertEquals(Integer.valueOf(1), service.currentStage(id));
    }

    // ================= 上报结案与收口锁定 =================

    @Test
    void 省局落不必处置_在哪一步都当场收口锁住() {
        Long id = openOne();
        TBcastCaseFlow done = service.conclude(id, "不必处置");
        assertEquals(Integer.valueOf(2), done.getStatus());
        assertEquals(Integer.valueOf(0), service.currentStage(id));
        assertEquals(1, logsOf(id, "TERMINAL"));
        assertLocked(id);
    }

    @Test
    void 省局落就地终止_也算收口() {
        Long id = openOne();
        assertEquals(Integer.valueOf(2), service.conclude(id, "就地终止").getStatus());
        assertLocked(id);
    }

    @Test
    void 寻常结论须走到上报结案才收_结论没落不收() {
        Long id = openOne();
        assertThrows(BcastCaseMoveException.class, () -> service.conclude(id, "责令整改"));
        assertThrows(BcastCaseMoveException.class, () -> service.conclude(id, "  "));
        // 一路走到上报结案
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "1");
        service.supplement(id, rescuePatch());
        service.moveForward(id, "2");
        TBcastCaseFlow h = new TBcastCaseFlow();
        h.setNormalHours(new BigDecimal("1"));
        service.supplement(id, h);
        service.moveForward(id, "3");
        TBcastCaseFlow done = service.conclude(id, "结案归档");
        assertEquals(Integer.valueOf(2), done.getStatus());
        // 收了口，在册活单随即减一
        assertEquals(0, service.activeCount());
    }

    /** 收了口：字改不了、材料补不进、整张删不掉、前后都挪不动 */
    private void assertLocked(Long id) {
        assertFalse(service.reviseWords(id, "想改个字"));
        assertEquals(0, service.cancel(id));
        assertThrows(BcastCaseMoveException.class, () -> service.moveForward(id, "还想往前"));
        assertThrows(BcastCaseMoveException.class, () -> service.moveBack(id, "还想往后"));
        assertThrows(BcastCaseMoveException.class,
                () -> service.supplement(id, rescuePatch()));
        assertFalse(service.reportWords(id, "还想添一句"));
    }

    // ================= 同一步只留首次那句 =================

    @Test
    void 同一步第二遍上报_卷上只留首次那句_不添第二行() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "头一句：四样齐");
        long rowsBefore = logs.size();

        // 第二回原样不收
        assertFalse(service.reportWords(id, "第二句：别记我"));
        assertEquals(rowsBefore, logs.size());
        // 卷上仍是头一句
        assertEquals("头一句：四样齐", service.replay(id).get(0).getContent());
    }

    @Test
    void 这一步进门时没带话_头一回补在原段上_之后第二回不收() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "  ");
        assertTrue(service.reportWords(id, "后补的头一句"));
        assertFalse(service.reportWords(id, "再补不收"));
    }

    // ================= 往后：退一步、话沉底、重走重攒 =================

    @Test
    void 往后一次退一步_退回那步原话沉底_重走从这步重新攒() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "头趟进抢险");
        service.supplement(id, rescuePatch());
        service.moveForward(id, "头趟进恢复");
        assertEquals(Integer.valueOf(2), service.currentStage(id));

        service.moveBack(id, "恢复钟点存疑，退回抢险重来");
        assertEquals(Integer.valueOf(1), service.currentStage(id));
        // 原先那段话没抹，仍沉在台账里
        assertTrue(logsOf(id, "FORWARD") >= 2);
        assertEquals(1, logsOf(id, "BACK"));

        // 重走：抢险几笔门槛还在，过了重新迈进恢复确认，另落一段新的
        service.moveForward(id, "二趟进恢复");
        assertEquals(Integer.valueOf(2), service.currentStage(id));
        assertEquals(3, logsOf(id, "FORWARD"));
    }

    @Test
    void 已停在第一步_不再往后收() {
        Long id = openOne();
        TBcastCaseFlow same = service.moveBack(id, "没处可退");
        assertEquals(Integer.valueOf(0), service.currentStage(id));
        assertEquals(0, logsOf(id, "BACK"));
        assertTrue(same != null);
    }

    // ================= 计数同源与倒序清点 =================

    @Test
    void 在册张数与当场逐张点的是同一份_收口随即减一() {
        Long id = openOne();
        assertEquals(service.activeCases().size(), service.activeCount());
        service.conclude(id, "不必处置");
        assertEquals(service.activeCases().size(), service.activeCount());
        assertEquals(0, service.activeCount());
    }

    @Test
    void 倒着从上报结案数回发现核实_段段点得清_抽一段单看也齐整() {
        Long id = openOne();
        service.supplement(id, discoverPatch(10));
        service.moveForward(id, "进抢险");
        service.supplement(id, rescuePatch());
        service.moveForward(id, "进恢复");
        TBcastCaseFlow h = new TBcastCaseFlow();
        h.setNormalHours(new BigDecimal("1"));
        service.supplement(id, h);
        service.moveForward(id, "进上报结案");
        service.conclude(id, "结案归档");

        List<TBcastCaseFlowLog> replayed = service.replay(id);
        // 倒序：收口在最前，立案在最末
        assertEquals("TERMINAL", replayed.get(0).getAction());
        assertEquals("OPEN", replayed.get(replayed.size() - 1).getAction());
        // OPEN + 3 次往前 + 收口 = 5 段，与值班簿那几笔合得拢
        assertEquals(5, replayed.size());
        for (TBcastCaseFlowLog l : replayed) {
            assertTrue(l.getStage() != null && l.getAction() != null
                    && l.getContent() != null && l.getCreateTime() != null);
        }
    }
}
