package cn.iocoder.yudao.module.scada.enums;

import cn.iocoder.yudao.framework.common.core.IntArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * SCADA 点位数据类型枚举
 *
 * <p>采集到的原始值统一映射为四种基础类型。TDengine 子表列类型按此推导：
 * BOOL→BOOL，INT→BIGINT，FLOAT→DOUBLE，STR→NCHAR(64)。</p>
 *
 * <p>注意 FLOAT 的 scale/offset 在 Driver 层就完成工程量转换，
 * 不要把原始寄存器值塞到时序库里再查询时算，否则每次查询都要走 CAST 重算。</p>
 */
@Getter
@AllArgsConstructor
public enum ScadaPointType implements IntArrayValuable {

    BOOL(1, "开关量"),
    INT(2, "整型"),
    FLOAT(3, "浮点"),
    STR(4, "字符串");

    private final Integer type;
    private final String name;

    @Override
    public int[] array() {
        return Arrays.stream(values()).mapToInt(ScadaPointType::getType).toArray();
    }

    public static ScadaPointType valueOf(Integer type) {
        for (ScadaPointType v : values()) {
            if (v.type.equals(type)) return v;
        }
        throw new IllegalArgumentException("未知点位类型: " + type);
    }
}
