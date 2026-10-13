package cn.iocoder.yudao.module.scada.service.alarm;

import cn.iocoder.yudao.module.scada.controller.admin.event.vo.ScadaAlarmEventPageReqVO;
import cn.iocoder.yudao.module.scada.dal.dataobject.event.ScadaAlarmEventDO;

import java.util.List;

/**
 * 告警事件服务接口
 *
 * <p>提供事件查询、人工确认（ack）、自动复归等操作。</p>
 */
public interface AlarmEventService {

    /** 分页查询事件，按设备/状态/严重度过滤 */
    List<ScadaAlarmEventDO> getEventList(ScadaAlarmEventPageReqVO reqVO);

    /** 单条详情 */
    ScadaAlarmEventDO getEvent(Long id);

    /** 人工确认：state UNACK→ACKED 或 RTN_UNACK→NORMAL */
    void ackEvent(Long id, String operator);

    /** 人工复归（运维主动结束告警） */
    void clearEvent(Long id, String operator);

    /** 当前未确认告警计数，看板用 */
    Long countUnacked();
}
