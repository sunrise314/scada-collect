package cn.iocoder.yudao.module.scada.dal.mysql.event;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * SCADA 告警事件 Mapper
 *
 * <p>事件表是告警引擎与运维看板的桥梁：AlarmEngine 只 insert + 状态更新，
 * 看板按 state/deviceId/triggeredAt 查询。</p>
 */
@Mapper
public interface ScadaAlarmEventMapper extends BaseMapperX<ScadaAlarmEventDO> {

    /** 找某规则当前未复归的事件（state=UNACK 或 ACKED），用于抖动抑制判定 */
    default ScadaAlarmEventDO selectActiveByRule(Long ruleId) {
        return selectOne(new LambdaQueryWrapperX<ScadaAlarmEventDO>()
                .eq(ScadaAlarmEventDO::getRuleId, ruleId)
                .in(ScadaAlarmEventDO::getState, 1, 2)
                .last("LIMIT 1"));
    }

    /** 分页查询未确认告警（看板默认视图） */
    default Long selectUnackedCount() {
        return selectCount(new LambdaQueryWrapperX<ScadaAlarmEventDO>()
                .eq(ScadaAlarmEventDO::getState, 1));
    }

    /**
     * 确认告警：state UNACK→ACKED 或 RTN_UNACK→NORMAL，写 ackedAt/ackedBy。
     * 用条件 update 保证状态机正确性——只有特定前置态才能转移。
     */
    @Update("UPDATE scada_alarm_event SET state = #{toState}, acked_at = #{ackedAt}, " +
            "acked_by = #{ackedBy} WHERE id = #{id} AND state = #{fromState} AND deleted = 0")
    int updateState(@Param("id") Long id, @Param("fromState") Integer fromState,
                    @Param("toState") Integer toState,
                    @Param("ackedAt") LocalDateTime ackedAt,
                    @Param("ackedBy") String ackedBy);

    /** 复归：写 clearedAt + state，触发条件由 AlarmEngine 控制 */
    @Update("UPDATE scada_alarm_event SET state = #{toState}, cleared_at = #{clearedAt}, " +
            "peak_value = #{peakValue} WHERE id = #{id} AND deleted = 0")
    int updateCleared(@Param("id") Long id, @Param("toState") Integer toState,
                      @Param("clearedAt") LocalDateTime clearedAt,
                      @Param("peakValue") java.math.BigDecimal peakValue);
}
