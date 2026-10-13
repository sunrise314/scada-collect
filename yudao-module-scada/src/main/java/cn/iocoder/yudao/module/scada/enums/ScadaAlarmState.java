package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 告警状态枚举（ISA-18.2 简化版）
 *
 * <p>ISA-18.2 标准的告警生命周期：</p>
 * <pre>
 *   Normal ──触发──▶ Unack ──确认──▶ Acked ──复归──▶ RTN-Unack ──确认──▶ Normal
 *                                       ◀──复归──┘
 * </pre>
 *
 * <p>这是 ISA-18.2 的核心四态简化版，把 RTN-Acked 合并到 Normal，
 * 因为"已确认且复归"的事件在业务上已经闭环，只需归档。</p>
 *
 * <p>Shelved（人工屏蔽）单独走 disabled 字段，不进状态机。一个 rule 可能被运维临时屏蔽，
 * 此时 {@code rule.enabled=false}，所有该 rule 的 active 告警进入 Shelved 状态。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaAlarmState implements IntArrayValuable {

    NORMAL(0, "正常"),
    UNACK(1, "未确认"),
    ACKED(2, "已确认"),
    RTN_UNACK(3, "复归未确认"),
    SHELVED(9, "已屏蔽");

    private final Integer state;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaAlarmState::getState).toArray();
    }

    public static ScadaAlarmState valueOf(Integer state) {
        for (ScadaAlarmState v : values()) {
            if (v.state.equals(state)) return v;
        }
        throw new IllegalArgumentException("未知告警状态: " + state);
    }
}
