package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBcastCaseFlowMapper;
import com.fc.v2.model.auto.TBcastCaseFlow;

/**
 * 播出事故处置单：挪步出口的单测。库用一本账顶替，落库、写回、撤单都当着面在账上过。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TBcastCaseFlowServiceImplTest {

    @Mock
    private TBcastCaseFlowMapper caseFlowMapper;

    @org.mockito.InjectMocks
    private TBcastCaseFlowServiceImpl service;

    /** 顶替库的那本账：单都落在里头，点验从账上点 */
    private final Map<Long, TBcastCaseFlow> db = new LinkedHashMap<Long, TBcastCaseFlow>();
    private long idSeq = 100L;

    // 各步递上去的话
    private static final String 发现核实齐 = "台站代号TZ00，中断起于14时00分，恢复至15时30分，被影响到的户数1200户";
    private static final String 发现核实缺户 = "台站代号TZ00，中断起于14时00分，恢复至15时30分";
    private static final String 抢险齐 = "处置抢险那几笔已填全：抢修、代播、复查";
    private static final String 钟点没走满 = "恢复后已连续正常播出1小时，材料齐全";
    private static final String 钟点走满 = "恢复后已连续正常播出3小时，材料齐全";
    private static final String 结论未落 = "材料已备齐，等候上报";
    private static final String 结论落 = "省局结论：同意结案";
    private static final String 就地终止 = "省局定夺：就地终止，器材封存";
    private static final String 不必处置 = "省局结论：不必处置";

    @BeforeEach
    void mockDb() {
        db.clear();
        idSeq = 100L;
        when(caseFlowMapper.selectById(any())).thenAnswer(inv -> db.get((Long) inv.getArgument(0)));
        when(caseFlowMapper.selectList(any()))
                .thenAnswer(inv -> new ArrayList<TBcastCaseFlow>(db.values()));
        when(caseFlowMapper.selectCount(any())).thenAnswer(inv -> {
            // 立案查活单：同一代号、没收了口的张数；代号从条件的实参里拣出来
            QueryWrapper<?> w = inv.getArgument(0);
            // 参数对是懒落的：先把条件串唤出来，实参才上得了账
            w.getSqlSegment();
            String bizNo = null;
            for (Object v : w.getParamNameValuePairs().values()) {
                if (v instanceof String) {
                    bizNo = (String) v;
                }
            }
            int n = 0;
            for (TBcastCaseFlow r : db.values()) {
                boolean closed = r.getStatus() != null && r.getStatus().intValue() == 2;
                if (!closed && (bizNo == null || bizNo.equals(r.getBizNo()))) {
                    n++;
                }
            }
            return Integer.valueOf(n);
        });
        when(caseFlowMapper.insert(any())).thenAnswer(inv -> {
            TBcastCaseFlow r = inv.getArgument(0);
            r.setId(Long.valueOf(++idSeq));
            db.put(r.getId(), r);
            return Integer.valueOf(1);
        });
        when(caseFlowMapper.updateById(any())).thenAnswer(inv -> {
            TBcastCaseFlow r = inv.getArgument(0);
            db.put(r.getId(), r);
            return Integer.valueOf(1);
        });
        when(caseFlowMapper.deleteById(any())).thenAnswer(inv -> {
            db.remove((Long) inv.getArgument(0));
            return Integer.valueOf(1);
        });
    }

    // ================= 家什 =================

    /** 立一张活单 */
    private TBcastCaseFlow file(String bizNo) {
        TBcastCaseFlow r = new TBcastCaseFlow();
        r.setBizNo(bizNo);
        service.insertTBcastCaseFlow(r);
        return r;
    }

    /** 一路走到第几步门口：发现核实→处置抢险→恢复确认，各递各的话 */
    private void walkTo(Long id, int stage) {
        if (stage >= 1) {
            service.moveForward(id, null, 发现核实齐);
        }
        if (stage >= 2) {
            service.moveForward(id, null, 抢险齐);
        }
        if (stage >= 3) {
            service.moveForward(id, null, 钟点走满);
        }
    }

    /** 在册段（第k步那几行）有几段 */
    private static int 在册段数(TBcastCaseFlow r) {
        if (r.getContent() == null) {
            return 0;
        }
        int n = 0;
        for (String line : r.getContent().split("\n")) {
            if (line.matches("^第[0-3]步\\|.*")) {
                n++;
            }
        }
        return n;
    }

    /** 沉到底下的段有几段 */
    private static int 沉底段数(TBcastCaseFlow r) {
        if (r.getContent() == null) {
            return 0;
        }
        int n = 0;
        for (String line : r.getContent().split("\n")) {
            if (line.startsWith("沉|")) {
                n++;
            }
        }
        return n;
    }

    // ================= 立案 =================

    @Test
    void 立案_系统落笔_人递进来的步次记载只算意图() {
        TBcastCaseFlow r = new TBcastCaseFlow();
        r.setBizNo(" SG-01 ");
        r.setStage(Integer.valueOf(3));
        r.setStatus(Integer.valueOf(2));
        r.setContent("自拟的段");
        assertEquals(1, service.insertTBcastCaseFlow(r));
        // 代号之外的栏一律由系统落笔：从发现核实起、在办、段记载空着
        assertEquals("SG-01", r.getBizNo());
        assertEquals(Integer.valueOf(0), r.getStage());
        assertEquals(Integer.valueOf(1), r.getStatus());
        assertNull(r.getContent());
        assertEquals("立案", r.getLastAction());
    }

    @Test
    void 立案_同一起事故底下一张活单为止() {
        TBcastCaseFlow first = file("SG-02");
        // 头一张没办结、也没中途叫停，后一张立不起来
        TBcastCaseFlow dup = new TBcastCaseFlow();
        dup.setBizNo("SG-02");
        assertEquals(0, service.insertTBcastCaseFlow(dup));
        assertEquals(1, db.size());
        // 另一起事故另立一张，互不碍着
        TBcastCaseFlow other = new TBcastCaseFlow();
        other.setBizNo("SG-03");
        assertEquals(1, service.insertTBcastCaseFlow(other));
        // 头一张中途叫停收了口，同名事故才立得起后一张
        service.moveForward(first.getId(), null, 就地终止);
        TBcastCaseFlow again = new TBcastCaseFlow();
        again.setBizNo("SG-02");
        assertEquals(1, service.insertTBcastCaseFlow(again));
    }

    // ================= 往前 =================

    @Test
    void 往前_一次只进紧挨着的后一步() {
        TBcastCaseFlow a = file("SG-10");
        TBcastCaseFlow r = service.moveForward(a.getId(), null, 发现核实齐);
        // 只进紧挨着的后一步：从0到1，不是一推两档
        assertEquals(Integer.valueOf(1), r.getStage());
        assertEquals(1, 在册段数(r));
        // 接口递上来的步次只算意图：递个3也不会一步蹦到3
        TBcastCaseFlow r2 = service.moveForward(a.getId(), Integer.valueOf(3), 抢险齐);
        assertEquals(Integer.valueOf(2), r2.getStage());
    }

    @Test
    void 门槛_发现核实四样不齐不进() {
        TBcastCaseFlow a = file("SG-11");
        // 缺了被影响到的户数：四样不齐，进不了后一步
        assertNull(service.moveForward(a.getId(), null, 发现核实缺户));
        assertEquals(Integer.valueOf(0), db.get(a.getId()).getStage());
        assertNull(db.get(a.getId()).getContent());
        // 四样补齐才进
        assertEquals(Integer.valueOf(1), service.moveForward(a.getId(), null, 发现核实齐).getStage());
    }

    @Test
    void 门槛_恢复确认钟点没走满不进() {
        TBcastCaseFlow a = file("SG-12");
        walkTo(a.getId(), 2);
        // 钟点没走满，材料递得再齐也不算
        assertNull(service.moveForward(a.getId(), null, 钟点没走满));
        assertEquals(Integer.valueOf(2), db.get(a.getId()).getStage());
        // 连着正常播够当地定的那几个钟点，才进得去
        assertEquals(Integer.valueOf(3), service.moveForward(a.getId(), null, 钟点走满).getStage());
    }

    @Test
    void 门槛_上报结案结论落了才收口() {
        TBcastCaseFlow a = file("SG-13");
        walkTo(a.getId(), 3);
        // 省局的结论没落：后一步开着也不能进
        assertNull(service.moveForward(a.getId(), null, 结论未落));
        assertEquals(Integer.valueOf(1), db.get(a.getId()).getStatus());
        // 结论一落，这张单收了口
        TBcastCaseFlow r = service.moveForward(a.getId(), null, 结论落);
        assertEquals(Integer.valueOf(2), r.getStatus());
        assertEquals(Integer.valueOf(3), r.getStage());
        assertEquals(4, 在册段数(r));
    }

    @Test
    void 落在两说_当场收了口() {
        TBcastCaseFlow a = file("SG-14");
        walkTo(a.getId(), 1);
        // 落在"就地终止"：哪一步落的话，单就停在哪一步收了口
        TBcastCaseFlow r = service.moveForward(a.getId(), null, 就地终止);
        assertEquals(Integer.valueOf(2), r.getStatus());
        assertEquals(Integer.valueOf(1), r.getStage());
        assertTrue(r.getContent().contains("收|第1步|"));
        // 落在"不必处置"那一说，这张单也算收了口
        TBcastCaseFlow b = file("SG-15");
        TBcastCaseFlow r2 = service.moveForward(b.getId(), null, 不必处置);
        assertEquals(Integer.valueOf(2), r2.getStatus());
        assertEquals(Integer.valueOf(0), r2.getStage());
    }

    @Test
    void 收了口_改不动删不掉挪不动() {
        TBcastCaseFlow a = file("SG-16");
        walkTo(a.getId(), 3);
        service.moveForward(a.getId(), null, 结论落);
        // 收了口的当场锁住
        assertNull(service.moveForward(a.getId(), null, 结论落));
        assertNull(service.moveBackward(a.getId(), "想退"));
        assertFalse(service.updateContent(a.getId(), "想改"));
        assertFalse(service.remove(a.getId()));
        // 年末还要凭它追那一段：单还在，段还在
        assertTrue(db.containsKey(a.getId()));
        assertEquals(4, 在册段数(db.get(a.getId())));
    }

    // ================= 往后 =================

    @Test
    void 往后_一次退一步_沉底的话还在() {
        TBcastCaseFlow a = file("SG-17");
        walkTo(a.getId(), 2);
        TBcastCaseFlow r = service.moveBackward(a.getId(), "抢险那笔要重填");
        // 一次退一步：从2退到1，不是一退到底
        assertEquals(Integer.valueOf(1), r.getStage());
        assertEquals(1, 在册段数(r));
        // 退回来那一步原先记载的话沉到底下，不抹掉
        assertEquals(1, 沉底段数(r));
        assertTrue(r.getContent().contains("沉|第1步|"));
    }

    @Test
    void 往后_停在第一步的不再往后收() {
        TBcastCaseFlow a = file("SG-18");
        assertNull(service.moveBackward(a.getId(), null));
        assertEquals(Integer.valueOf(0), db.get(a.getId()).getStage());
    }

    @Test
    void 退后重走_从这一步重新攒() {
        TBcastCaseFlow a = file("SG-19");
        walkTo(a.getId(), 2);
        service.moveBackward(a.getId(), null);
        TBcastCaseFlow r = service.moveForward(a.getId(), null, 抢险齐);
        assertEquals(Integer.valueOf(2), r.getStage());
        // 重走时从这一步重新攒：新的一段照记，沉底的旧话也还在
        assertEquals(2, 在册段数(r));
        assertEquals(1, 沉底段数(r));
    }

    // ================= 二次上报与同一刹那 =================

    @Test
    void 同一步骤二次上报_不添第二行() {
        TBcastCaseFlow a = file("SG-20");
        service.moveForward(a.getId(), Integer.valueOf(0), 发现核实齐);
        String first = db.get(a.getId()).getContent();
        // 同一步骤第二遍递上来：卷上留下的还是首次那句，第二回不再添一行
        TBcastCaseFlow r = service.moveForward(a.getId(), Integer.valueOf(0), 发现核实齐);
        assertNotNull(r);
        assertEquals(first, r.getContent());
        assertEquals(Integer.valueOf(1), r.getStage());
        assertEquals(1, 在册段数(r));
    }

    @Test
    void 同一刹那两条_各记各的() {
        TBcastCaseFlow a = file("SG-21");
        service.moveForward(a.getId(), null, 发现核实齐);
        TBcastCaseFlow r = service.moveForward(a.getId(), null, 抢险齐);
        // 两条紧挨着进来：分开各记各的，既不归并也不许漏记
        assertEquals(2, 在册段数(r));
        assertTrue(r.getContent().contains("第0步|"));
        assertTrue(r.getContent().contains("第1步|"));
    }

    // ================= 步次以数出来的为准 =================

    @Test
    void 纸面步次与数出来的对不上_以数出来的为准() {
        // 老单：纸面写到上报结案，卷上只有发现核实那一段
        TBcastCaseFlow legacy = new TBcastCaseFlow();
        legacy.setId(Long.valueOf(1L));
        legacy.setBizNo("SG-22");
        legacy.setStage(Integer.valueOf(3));
        legacy.setStatus(Integer.valueOf(1));
        legacy.setContent("第0步|2024-09-01 10:00:00|台站代号TZ00，中断起于09时，恢复至10时，被影响到的户数300户");
        db.put(legacy.getId(), legacy);
        // 回读：归在第几步以出口数出来的为准，不凭纸面
        assertEquals(Integer.valueOf(1), service.selectTBcastCaseFlowById(legacy.getId()).getStage());
        // 挪步也从数出来的那一步起：递抢险的话，过的是处置抢险的门槛
        TBcastCaseFlow r = service.moveForward(legacy.getId(), null, 抢险齐);
        assertEquals(Integer.valueOf(2), r.getStage());
    }

    // ================= 在册张数 =================

    @Test
    void 在册张数_随立案加一随结案减一() {
        assertEquals(0, service.countOnFile());
        TBcastCaseFlow a = file("SG-30");
        TBcastCaseFlow b = file("SG-31");
        assertEquals(2, service.countOnFile());
        // 中途叫停收了口：在册减一
        service.moveForward(a.getId(), null, 就地终止);
        assertEquals(1, service.countOnFile());
        // 办结收了口：在册再减一
        walkTo(b.getId(), 3);
        service.moveForward(b.getId(), null, 结论落);
        assertEquals(0, service.countOnFile());
    }

    // ================= 卷面批注与撤单 =================

    @Test
    void 在办的_批注动得_段记载碰不着() {
        TBcastCaseFlow a = file("SG-32");
        service.moveForward(a.getId(), null, 发现核实齐);
        String segs = db.get(a.getId()).getContent();
        assertTrue(service.updateContent(a.getId(), "值班批注：已催台站"));
        assertEquals("值班批注：已催台站", db.get(a.getId()).getRemark());
        // 一段一段的记载只由挪步出口落笔：批注动得了，段碰不着
        assertEquals(segs, db.get(a.getId()).getContent());
        // 在办的可撤，名额随即空出来
        assertTrue(service.remove(a.getId()));
        assertFalse(db.containsKey(a.getId()));
    }
}
