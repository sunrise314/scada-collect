package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 告警严重等级
 *
 * <p>四级严重度，对应 ISA-18.2 建议的优先级分层：</p>
 * <ul>
 *   <li>CRITICAL：关键告警，短信+电话+邮件全渠道，5 秒内必须人工介入</li>
 *   <li>HIGH：高优先级，短信+邮件，30 秒响应</li>
 *   <li>MEDIUM：中优先级，邮件，2 小时响应</li>
 *   <li>LOW：低优先级，仅记录，次日汇总推送</li>
 * </ul>
 *
 * <p>严重度影响"通知路由"，{@code AlarmNotifyRouter} 按 severity 决定推送到哪个渠道。
 * 业务侧的告警合并分组 key 必须包含 severity，避免低级别告警把高级别的合并掉。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaAlarmSeverity implements IntArrayValuable {

    LOW(1, "低"),
    MEDIUM(2, "中"),
    HIGH(3, "高"),
    CRITICAL(4, "关键");

    private final Integer level;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaAlarmSeverity::getLevel).toArray();
    }
}
