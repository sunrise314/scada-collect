package cn.iocoder.yudao.module.scada.dal.dataobject.rule;

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

/**
 * SCADA 告警规则实体
 *
 * <p>三种规则类型共用一张表，未使用的阈值字段留空：
 * THRESHOLD 用 highLimit/lowLimit，BAND 用 highLimit/lowLimit 表示区间，
 * RATE 用 rateLimit。RuleEngine 按规则类型走不同判断逻辑。</p>
 *
 * <p>抖动抑制核心字段：onDelayMs / offDelayMs。
 * on-delay：触发条件持续 N 毫秒才正式产生告警事件，避免瞬时抖动误报。
 * off-delay：复归条件持续 N 毫秒才正式复归，避免短时间内反复触发-复归。</p>
 */
@TableName("scada_alarm_rule")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScadaAlarmRuleDO extends BaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    /** 规则编码 */
    private String code;

    /** 规则名称 */
    private String name;

    /** 关联点位 ID */
    private Long pointId;

    /** 规则类型，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaAlarmRuleType} */
    private Integer ruleType;

    /** 阈值上限，THRESHOLD/BAND 使用 */
    private BigDecimal highLimit;

    /** 阈值下限 */
    private BigDecimal lowLimit;

    /** 变化率阈值，RATE 使用，单位 value/sec */
    private BigDecimal rateLimit;

    /** 触发延迟毫秒，默认 2000 */
    @Builder.Default
    private Integer onDelayMs = 2000;

    /** 复归延迟毫秒，默认 5000 */
    @Builder.Default
    private Integer offDelayMs = 5000;

    /** 死区百分比，避免在阈值附近抖动 */
    @Builder.Default
    private BigDecimal deadBand = BigDecimal.ZERO;

    /** 严重度，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaAlarmSeverity} */
    @Builder.Default
    private Integer severity = 2;

    /** 是否启用 */
    @Builder.Default
    private Boolean enabled = true;

    /** 触发后通知模板 */
    private String notifyTemplate;

    /** 备注 */
    private String remark;
}
