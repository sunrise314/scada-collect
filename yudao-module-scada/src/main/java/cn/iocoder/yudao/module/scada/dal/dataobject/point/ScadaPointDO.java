package cn.iocoder.yudao.module.scada.dal.dataobject.point;

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
 * SCADA 点位实体（核心元数据）
 *
 * <p>点位是 SCADA 系统的最小数据单元，对应一台设备上的一个传感器/寄存器/标签。
 * 一个设备典型有 100-5000 个点位，超过 5000 建议拆子设备。</p>
 *
 * <p>关键字段：</p>
 * <ul>
 *   <li>tag：点位标签，租户内唯一，用作时序库的子表名</li>
 *   <li>address：协议地址，Modbus 是 "HR/40001"、OPC UA 是 "ns=2;s=Tag1"、S7 是 "DB1.DBX0.0"</li>
 *   <li>pointType：BOOL/INT/FLOAT/STR，决定时序库列类型</li>
 *   <li>scale/offset：工程量转换，原始值 raw → eng = raw * scale + offset</li>
 *   <li>deadBand：死区百分比，0-100，0 表示任何变化都推送</li>
 *   <li>scanRateMs：扫描周期，建议 ≥ 1000，1Hz 是商用 SCADA 的甜点</li>
 *   <li>rwFlag：R 只读 / W 只写 / RW 读写</li>
 * </ul>
 *
 * <p>Tenancy 设计：所有 SCADA 表自动带 tenant_id（来自 BaseDO + 多租户拦截器）。
 * 一台设备不能跨租户共享点位，所以 tag 在 tenant_id + code 维度唯一即可，
 * 不必全局唯一。</p>
 */
@TableName("scada_point")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScadaPointDO extends BaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    /** 设备 ID，每个点位必须挂在设备下 */
    private Long deviceId;

    /** 点位标签，用作 TDengine 子表名，禁止含中文字符，建议 [a-z][a-z0-9_]{2,63} */
    private String tag;

    /** 点位名称 */
    private String name;

    /** 协议地址，格式因 protocolType 而异 */
    private String address;

    /** 点位类型，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaPointType} */
    private Integer pointType;

    /** 工程单位，如 ℃、MPa、rpm */
    private String engUnit;

    /** 工程量缩放系数 */
    @Builder.Default
    private BigDecimal scale = BigDecimal.ONE;

    /** 工程量零点偏移 */
    @Builder.Default
    private BigDecimal offset = BigDecimal.ZERO;

    /** 量程下限 */
    private BigDecimal minVal;

    /** 量程上限 */
    private BigDecimal maxVal;

    /** 死区百分比 0-100，0=任何变化都推送 */
    @Builder.Default
    private BigDecimal deadBand = BigDecimal.ZERO;

    /** 扫描周期毫秒，建议 ≥ 1000 */
    @Builder.Default
    private Integer scanRateMs = 1000;

    /** 读写标志：R / W / RW */
    @Builder.Default
    private String rwFlag = "R";

    /** 是否启用采集 */
    @Builder.Default
    private Boolean enabled = true;

    /** 备注 */
    private String remark;
}
