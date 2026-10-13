package cn.iocoder.yudao.module.scada.dal.dataobject.device;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.scada.enums.ScadaProtocolType;
import com.baomidou.mybatisplus.annotation.KeyId;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * SCADA 设备实体
 *
 * <p>核心字段 conn_params_json：按 protocol_type 反序列化成不同结构的参数对象。</p>
 * <ul>
 *   <li>Modbus TCP：{host, port=502, slaveId=1}</li>
 *   <li>OPC UA：{endpointUrl, securityPolicy=NONE, username?, password?}</li>
 *   <li>S7：{host, rack=0, slot=1}</li>
 *   <li>MQTT Sparkplug：{brokerUrl, group, edgeNode, clientId}</li>
 * </ul>
 *
 * <p>为什么不用一张子表存连接参数？因为不同协议字段差异极大，做成子表后要么字段稀疏，
 * 要么做 EAV 模型查询性能爆炸。JSON 字段 + Driver 层强校验是最务实的方案。</p>
 */
@TableName("scada_device")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScadaDeviceDO extends BaseDO {

    @TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Long id;

    /** 设备编码，租户内唯一，用于 URL 和 Driver 层日志追踪 */
    private String code;

    /** 设备名称，展示用 */
    private String name;

    /** 协议类型，见 {@link ScadaProtocolType} */
    private Integer protocolType;

    /** 连接参数 JSON，见类注释 */
    private String connParamsJson;

    /** 运行状态，见 {@link cn.iocoder.yudao.module.scada.enums.ScadaDeviceStatus} */
    private Integer status;

    /** 最近一次心跳时间，断线判定的关键依据 */
    private LocalDateTime lastHeartbeatAt;

    /** 最近一次错误信息 */
    private String lastError;

    /** 是否启用，false 时 Driver 层不发起连接 */
    @Builder.Default
    private Boolean enabled = true;

    /** 备注 */
    private String remark;
}
