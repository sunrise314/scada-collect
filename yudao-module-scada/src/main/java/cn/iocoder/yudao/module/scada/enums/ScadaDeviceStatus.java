package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 设备连接状态枚举（断线重连状态机）
 *
 * <p>状态转移图：</p>
 * <pre>
 *   UNKNOWN ──connect()──▶ CONNECTING ──成功──▶ CONNECTED
 *                                  └──失败──▶ RECONNECTING(指数退避 1/2/4/8/30s)
 *                                  ◀──恢复──┘
 *   CONNECTED ──channelInactive/心跳超时──▶ RECONNECTING
 *   任意 ──disable()──▶ DISABLED
 * </pre>
 *
 * <p>关键设计：CONNECTING 与 RECONNECTING 分开，避免"刚启动"和"中途断线"误报成同一种事件。
 * 业务侧的看板只关心 CONNECTED 和 RECONNECTING，前者绿色后者橙色。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaDeviceStatus implements IntArrayValuable {

    DISABLED(0, "已禁用"),
    UNKNOWN(1, "未知"),
    CONNECTING(2, "连接中"),
    CONNECTED(3, "已连接"),
    RECONNECTING(4, "重连中"),
    ERROR(5, "故障");

    private final Integer status;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaDeviceStatus::getStatus).toArray();
    }
}
