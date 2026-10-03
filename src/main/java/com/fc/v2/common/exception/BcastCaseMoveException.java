package com.fc.v2.common.exception;

/**
 * 处置单挪步规矩异常。
 *
 * <p>往前那一步门槛没凑齐（发现核实四样缺了、抢险几笔没填全、恢复后连着正常播的钟点
 * 没走满、省局结论没落），或是对已经收了口的单还想改、想删、想挪，一律由挪步出口抛
 * 这一桩。消息里点名卡在哪、为什么收不下——经全局异常处理落到回执上，照着补。</p>
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BcastCaseMoveException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public BcastCaseMoveException(String message) {
        super(message);
    }
}
