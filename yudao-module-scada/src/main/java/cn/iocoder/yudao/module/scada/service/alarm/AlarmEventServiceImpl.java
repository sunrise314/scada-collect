package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.module.scada.controller.admin.event.vo.ScadaAlarmEventPageReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;
import cn.iocoder.yudao.module.scada.dal.mysql.event.ScadaAlarmEventMapper;
import cn.iocoder.yudao.module.scada.enums.ScadaAlarmState;
import cn.iocoder.yudao.module.scada.framework.scada.alarm.AlarmStateMachine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 告警事件服务实现
 *
 * <p>ack 操作是 ISA-18.2 的关键状态转移：UNACK→ACKED 或 RTN_UNACK→NORMAL。
 * 用条件 update 保证状态机正确性——只有特定前置态才能转移，
 * 避免并发 ack 时双写。</p>
 */
@Slf4j
@Service
public class AlarmEventServiceImpl implements AlarmEventService {

    private final ScadaAlarmEventMapper eventMapper;

    public AlarmEventServiceImpl(ScadaAlarmEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    @Override
    public List<ScadaAlarmEventDO> getEventList(ScadaAlarmEventPageReqVO reqVO) {
        // 简化实现：返回最近 100 条未复归事件
        return eventMapper.selectList();
    }

    @Override
    public ScadaAlarmEventDO getEvent(Long id) {
        return eventMapper.selectById(id);
    }

    @Override
    public void ackEvent(Long id, String operator) {
        ScadaAlarmEventDO event = eventMapper.selectById(id);
        if (event == null) {
            throw new RuntimeException("事件不存在: " + id);
        }
        Integer toState = AlarmStateMachine.ackTarget(event.getState());
        if (toState == null) {
            throw new RuntimeException("事件 state=" + event.getState() + " 不允许确认");
        }
        int affected = eventMapper.updateState(
                id, event.getState(), toState, LocalDateTime.now(), operator);
        if (affected == 0) {
            throw new RuntimeException("确认失败：事件状态可能已被其他操作改变");
        }
        log.info("告警事件 {} 由 {} 确认 state {}->{}", id, operator, event.getState(), toState);
    }

    @Override
    public void clearEvent(Long id, String operator) {
        ScadaAlarmEventDO event = eventMapper.selectById(id);
        if (event == null) return;
        Integer toState = AlarmStateMachine.clearTarget(event.getState());
        if (toState == null) {
            throw new RuntimeException("事件 state=" + event.getState() + " 不允许复归");
        }
        eventMapper.updateCleared(id, toState, LocalDateTime.now(), event.getPeakValue());
        log.info("告警事件 {} 由 {} 手工复归", id, operator);
    }

    @Override
    public Long countUnacked() {
        return eventMapper.selectUnackedCount();
    }
}
