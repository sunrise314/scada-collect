package cn.iocoder.yudao.module.scada.framework.scada.alarm;

import cn.iocoder.yudao.module.scada.enums.ScadaAlarmState;

/**
 * 告警状态机（ISA-18.2 简化版）
 *
 * <p>四态：NORMAL / UNACK / ACKED / RTN_UNACK，外加 SHELVED（由 enabled=false 标记，不进状态机）。</p>
 *
 * <p>状态转移：</p>
 * <pre>
 *   NORMAL  --触发-->  UNACK  (1)
 *   UNACK  --确认-->   ACKED  (2)
 *   UNACK  --复归-->   RTN_UNACK  (3)
 *   ACKED  --复归-->   NORMAL  (4)
 *   RTN_UNACK --确认--> NORMAL  (5)
 * </pre>
 *
 * <p>非法转移直接抛异常——比如 UNACK 想直接跳 NORMAL 是错的，必须先复归。</p>
 *
 * <p>状态机是被 {@link cn.iocoder.yudao.module.scada.service.alarm.AlarmEngine} 调用的纯函数工具，
 * 不持有状态——状态都存在 scada_alarm_event 表的 state 字段。</p>
 */
public final class AlarmStateMachine {

    private AlarmStateMachine() {}

    /** 校验 from→to 是否合法转移 */
    public static boolean canTransit(Integer from, Integer to) {
        if (from == null) return to.equals(ScadaAlarmState.UNACK.getState());
        int f = from;
        int t = to;
        // 同态不允许
        if (f == t) return false;
        // 五条合法转移
        return (f == ScadaAlarmState.NORMAL.getState() && t == ScadaAlarmState.UNACK.getState())
                || (f == ScadaAlarmState.UNACK.getState() && t == ScadaAlarmState.ACKED.getState())
                || (f == ScadaAlarmState.UNACK.getState() && t == ScadaAlarmState.RTN_UNACK.getState())
                || (f == ScadaAlarmState.ACKED.getState() && t == ScadaAlarmState.NORMAL.getState())
                || (f == ScadaAlarmState.RTN_UNACK.getState() && t == ScadaAlarmState.NORMAL.getState());
    }

    /** 确认操作的目标态：UNACK→ACKED, RTN_UNACK→NORMAL, 其他拒绝 */
    public static Integer ackTarget(Integer from) {
        if (from == null) return null;
        if (from.equals(ScadaAlarmState.UNACK.getState())) return ScadaAlarmState.ACKED.getState();
        if (from.equals(ScadaAlarmState.RTN_UNACK.getState())) return ScadaAlarmState.NORMAL.getState();
        return null;
    }

    /** 复归操作的目标态：UNACK→RTN_UNACK, ACKED→NORMAL, 其他拒绝 */
    public static Integer clearTarget(Integer from) {
        if (from == null) return null;
        if (from.equals(ScadaAlarmState.UNACK.getState())) return ScadaAlarmState.RTN_UNACK.getState();
        if (from.equals(ScadaAlarmState.ACKED.getState())) return ScadaAlarmState.NORMAL.getState();
        return null;
    }
}
