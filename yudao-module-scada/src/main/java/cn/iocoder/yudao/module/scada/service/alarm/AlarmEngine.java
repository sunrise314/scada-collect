package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.point.ScadaPointDO;
import cn.iocoder.yudao.module.scada.dal.dataobject.rule.ScadaAlarmRuleDO;
import cn.iocoder.yudao.module.scada.dal.mysql.event.ScadaAlarmEventMapper;
import cn.iocoder.yudao.module.scada.dal.mysql.point.ScadaPointMapper;
import cn.iocoder.yudao.module.scada.dal.mysql.rule.ScadaAlarmRuleMapper;
import cn.iocoder.yudao.module.scada.enums.ScadaAlarmRuleType;
import cn.iocoder.yudao.module.scada.enums.ScadaAlarmState;
import cn.iocoder.yudao.module.scada.framework.scada.alarm.AlarmStateMachine;
import cn.iocoder.yudao.module.scada.framework.scada.protocol.PointValue;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警引擎（抖动抑制 + 状态机核心）
 *
 * <p>两大核心机制：</p>
 *
 * <h3>1. 抖动抑制（on-delay / off-delay）</h3>
 * <p>核心思想：条件瞬时变化不立即触发告警事件，
 * 而是记录"条件满足持续时间"，超过 onDelayMs 才真正产生事件。</p>
 * <p>类比继电器的延时线圈——条件通电 N 毫秒后才吸合，断电 N 毫秒后才释放。</p>
 *
 * <p>好处：避免瞬时干扰（电网波动、传感器跳变）造成告警洪流，
 * 也避免条件在阈值附近抖动时反复触发-复归-触发-复归。</p>
 *
 * <h3>2. ISA-18.2 状态机</h3>
 * <p>见 {@link AlarmStateMachine}：UNACK → ACKED → RTN_UNACK → NORMAL。</p>
 *
 * <p>实现细节：</p>
 * <ul>
 *   <li>规则全量加载到内存，启动时 + 规则变更时刷新</li>
 *   <li>每个 (ruleId, pointId) 维护一个 PendingState，记录条件满足开始时间</li>
 *   <li>{@code onValues} 接收一批点位值，按 pointId 路由到对应规则</li>
 *   <li>触发条件持续到 onDelayMs 且当前无 active 事件 → 新建事件</li>
 *   <li>复归条件持续到 offDelayMs 且有 active 事件 → 复归事件</li>
 * </ul>
 *
 * <p>这是工业 SCADA 引擎的标准做法，simscape / wonderware 都这么干。</p>
 */
@Slf4j
@Component
public class AlarmEngine {

    private final ScadaAlarmRuleMapper ruleMapper;
    private final ScadaAlarmEventMapper eventMapper;
    private final ScadaPointMapper pointMapper;
    private final AlarmNotifyRouter notifyRouter;

    /** 规则缓存：pointId → 该点位的所有规则 */
    private final Map<Long, List<ScadaAlarmRuleDO>> rulesByPoint = new ConcurrentHashMap<>();

    /** pending 状态：(ruleId, pointId) → 状态 */
    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    public AlarmEngine(ScadaAlarmRuleMapper ruleMapper,
                       ScadaAlarmEventMapper eventMapper,
                       ScadaPointMapper pointMapper,
                       AlarmNotifyRouter notifyRouter) {
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.pointMapper = pointMapper;
        this.notifyRouter = notifyRouter;
    }

    @PostConstruct
    public void loadRules() {
        reloadRules();
    }

    /** 全量重载规则，由 AlarmRuleService 在规则变更后调用 */
    public void reloadRules() {
        List<ScadaAlarmRuleDO> all = ruleMapper.selectAllEnabled();
        Map<Long, List<ScadaAlarmRuleDO>> grouped = new HashMap<>();
        for (ScadaAlarmRuleDO r : all) {
            grouped.computeIfAbsent(r.getPointId(), k -> new ArrayList<>()).add(r);
        }
        rulesByPoint.clear();
        rulesByPoint.putAll(grouped);
        log.info("AlarmEngine 加载 {} 条规则覆盖 {} 个点位", all.size(), grouped.size());
    }

    /** 由 CollectTaskExecutor 在每次读完后调用 */
    public void onValues(List<PointValue> values) {
        for (PointValue v : values) {
            if (!v.isGood()) continue;
            if (v.valueNum() == null) continue;
            List<ScadaAlarmRuleDO> rules = rulesByPoint.get(v.pointId());
            if (rules == null || rules.isEmpty()) continue;
            for (ScadaAlarmRuleDO rule : rules) {
                try {
                    evaluateRule(rule, v);
                } catch (Exception e) {
                    log.warn("规则 {} 判定失败: {}", rule.getCode(), e.getMessage());
                }
            }
        }
    }

    /** 单规则判定：抖动抑制 + 状态机 */
    private void evaluateRule(ScadaAlarmRuleDO rule, PointValue value) {
        boolean triggered = isTriggered(rule, value.valueNum());
        String key = rule.getId() + ":" + value.pointId();
        PendingState pending = pendingStates.computeIfAbsent(key, k -> new PendingState());
        long now = System.currentTimeMillis();

        ScadaAlarmEventDO activeEvent = eventMapper.selectActiveByRule(rule.getId());

        if (triggered) {
            // 触发条件满足
            if (activeEvent == null) {
                // 还没事件，看是否满足 on-delay
                if (pending.triggerStartMs == 0) {
                    pending.triggerStartMs = now;
                }
                if (now - pending.triggerStartMs >= rule.getOnDelayMs()) {
                    // 真正触发：插入事件
                    createEvent(rule, value, activeEvent);
                    pending.triggerStartMs = 0;
                    pending.clearStartMs = 0;
                }
            } else {
                // 已有事件：更新峰值，重置复归计时器
                pending.clearStartMs = 0;
                tryUpdatePeak(activeEvent, value.valueNum());
            }
        } else {
            // 触发条件不满足
            if (activeEvent != null) {
                // 看是否满足 off-delay
                if (pending.clearStartMs == 0) {
                    pending.clearStartMs = now;
                }
                if (now - pending.clearStartMs >= rule.getOffDelayMs()) {
                    // 真正复归
                    clearEvent(activeEvent);
                    pending.clearStartMs = 0;
                    pending.triggerStartMs = 0;
                }
            } else {
                // 无事件 + 条件不满足：清掉 pending trigger 计时
                pending.triggerStartMs = 0;
            }
        }
    }

    /** 三种规则类型的核心判定逻辑 */
    private boolean isTriggered(ScadaAlarmRuleDO rule, BigDecimal value) {
        double v = value.doubleValue();
        Integer type = rule.getRuleType();
        if (type.equals(ScadaAlarmRuleType.THRESHOLD.getType())) {
            // THRESHOLD：高于 highLimit 或低于 lowLimit 触发
            boolean highHit = rule.getHighLimit() != null && v > rule.getHighLimit().doubleValue();
            boolean lowHit = rule.getLowLimit() != null && v < rule.getLowLimit().doubleValue();
            return highHit || lowHit;
        }
        if (type.equals(ScadaAlarmRuleType.BAND.getType())) {
            // BAND：值超出 [low, high] 区间触发
            boolean outHigh = rule.getHighLimit() != null && v > rule.getHighLimit().doubleValue();
            boolean outLow = rule.getLowLimit() != null && v < rule.getLowLimit().doubleValue();
            return outHigh || outLow;
        }
        if (type.equals(ScadaAlarmRuleType.RATE.getType())) {
            // RATE：变化率超阈值触发（需要前一次值，本引擎简化为绝对变化率）
            // 商用版应记录前值，这里简化为：直接看是否超 rateLimit
            // 实际实现需要维护 prevValue，参见 v2
            return rule.getRateLimit() != null && Math.abs(v) > rule.getRateLimit().doubleValue();
        }
        return false;
    }

    private void createEvent(ScadaAlarmRuleDO rule, PointValue value, ScadaAlarmEventDO oldEvent) {
        ScadaAlarmEventDO event = ScadaAlarmEventDO.builder()
                .ruleId(rule.getId())
                .pointId(value.pointId())
                .deviceId(null)
                .triggeredAt(LocalDateTime.now())
                .triggerValue(value.valueNum())
                .peakValue(value.valueNum())
                .state(ScadaAlarmState.UNACK.getState())
                .severity(rule.getSeverity())
                .message(buildMessage(rule, value))
                .notified(false)
                .build();
        eventMapper.insert(event);
        log.info("告警触发 rule={} value={} event={}", rule.getCode(), value.valueNum(), event.getId());
        // 路由通知
        try {
            notifyRouter.notify(event, rule);
            event.setNotified(true);
            eventMapper.updateById(event);
        } catch (Exception e) {
            log.warn("告警通知失败 event={}: {}", event.getId(), e.getMessage());
        }
    }

    private void clearEvent(ScadaAlarmEventDO event) {
        Integer toState = AlarmStateMachine.clearTarget(event.getState());
        if (toState == null) return;
        eventMapper.updateCleared(event.getId(), toState, LocalDateTime.now(), event.getPeakValue());
        log.info("告警复归 event={} state {}->{}", event.getId(), event.getState(), toState);
    }

    private void tryUpdatePeak(ScadaAlarmEventDO event, BigDecimal value) {
        if (event.getPeakValue() == null || value.compareTo(event.getPeakValue()) > 0) {
            event.setPeakValue(value);
            // 简化版直接 update by id，商用版应该批量合并更新避免高频写
            eventMapper.updateById(event);
        }
    }

    private String buildMessage(ScadaAlarmRuleDO rule, PointValue value) {
        ScadaPointDO point = pointMapper.selectById(rule.getPointId());
        String pointName = point == null ? rule.getPointId().toString() : point.getName();
        return String.format("点位[%s] 当前值 %s 触发规则[%s]",
                pointName, value.valueNum(), rule.getName());
    }

    /** pending 计时状态 */
    private static class PendingState {
        long triggerStartMs = 0;
        long clearStartMs = 0;
    }
}
