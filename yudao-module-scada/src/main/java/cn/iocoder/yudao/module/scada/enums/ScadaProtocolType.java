package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 设备协议类型枚举
 *
 * <p>新建设备时由用户选择，决定 {@code device.conn_params_json} 的字段格式，
 * 同时驱动 {@link cn.iocoder.yudao.module.scada.framework.scada.driver.ScadaPlcDriverManager}
 * 选择对应的 {@link ScadaPlcDriver} 实现。</p>
 *
 * <p>核心取舍：不引入 ThingsBoard 那种"插件式 SPI 加载 + 多传输层 Netty Pipeline"的复杂度，
 * 只覆盖中小制造工厂最常见的三类协议。要扩 OPC DA 或 BACnet 时，新增枚举值 + Driver 实现即可。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaProtocolType implements IntArrayValuable {

    MODBUS_TCP(10, "Modbus TCP"),
    MODBUS_RTU(11, "Modbus RTU"),
    OPC_UA(20, "OPC UA"),
    S7_1200(30, "S7-1200"),
    S7_1500(31, "S7-1500"),
    MQTT_SPARKPLUG(40, "MQTT Sparkplug B"),
    ;

    private final Integer type;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaProtocolType::getType).toArray();
    }

    public static ScadaProtocolType valueOf(Integer type) {
        for (ScadaProtocolType v : values()) {
            if (v.type.equals(type)) return v;
        }
        throw new IllegalArgumentException("未知协议类型: " + type);
    }
}
