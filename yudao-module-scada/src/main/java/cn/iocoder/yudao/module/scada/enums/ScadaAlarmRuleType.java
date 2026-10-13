package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 告警规则类型枚举
 *
 * <p>三种最基本的告警规则：</p>
 * <ul>
 *   <li>THRESHOLD：阈值告警，PV &gt; high_limit 或 PV &lt; low_limit 任一成立即触发</li>
 *   <li>BAND：区间告警，PV NOT IN (low_limit, high_limit) 触发，常用于"电压合格率"场景</li>
 *   <li>RATE：变化率告警，|ΔPV/Δt| &gt; rate_limit 触发，用于传感器跳变识别</li>
 * </ul>
 *
 * <p>这三种之外不扩展，复杂的"多点位组合告警"建议走事件流去 Flink，
 * SCADA 引擎只做单点告警。如果业务真要组合，让 alarm_rule 关联 point_id，
 * 在 AlarmEngine 里手工写复合条件，不要把引擎做成 DSL。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaAlarmRuleType implements IntArrayValuable {

    THRESHOLD(1, "阈值告警"),
    BAND(2, "区间告警"),
    RATE(3, "变化率告警");

    private final Integer type;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaAlarmRuleType::getType).toArray();
    }
}
