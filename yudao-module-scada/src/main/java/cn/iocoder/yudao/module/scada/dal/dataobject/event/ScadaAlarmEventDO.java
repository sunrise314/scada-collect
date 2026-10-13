package cn.iocoder.yudao.module.scada.dal.dataobject.event;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 告警事件实体
 *
 * <p>一次告警从触发到复归产生一条事件，state 字段走 ISA-18.2 状态机：
 * UNACK → ACKED → RTN_UNACK → NORMAL。</p>
 *
 * <p>peakValue 记录触发期间的极值，便于事后复盘。</p>
 */
@TableName("scada_alarm_event")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScadaAlarmEventDO extends BaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    /** 规则 ID */
    private Long ruleId;

    /** 点位 ID */
    private Long pointId;

    /** 设备 ID（冗余，便于按设备查询） */
    private Long deviceId;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 复归时间 */
    private LocalDateTime clearedAt;

    /** 确认时间 */
    private LocalDateTime ackedAt;

    /** 确认人 */
    private String ackedBy;

    /** 触发时的值 */
    private BigDecimal triggerValue;

    /** 峰值 */
    private BigDecimal peakValue;

    /** 状态，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaAlarmState} */
    @Builder.Default
    private Integer state = 1;

    /** 严重度，冗余自规则 */
    private Integer severity;

    /** 触发消息 */
    private String message;

    /** 是否已通知 */
    @Builder.Default
    private Boolean notified = false;
}
