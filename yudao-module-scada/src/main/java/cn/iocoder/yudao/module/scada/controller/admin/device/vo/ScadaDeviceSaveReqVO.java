package cn.iocoder.yudao.module.scada.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备保存请求 VO
 *
 * <p>新增和编辑共用——id 为空表示新增，非空表示编辑。</p>
 *
 * <p>{@code connParamsJson} 是字符串，前端负责序列化；
 * 后端只做格式校验（Driver 层会按 protocolType 反序列化）。</p>
 */
@Schema(description = "管理后台 - SCADA 设备保存 Request VO")
@Data
public class ScadaDeviceSaveReqVO {

    @Schema(description = "主键 ID，编辑时必填")
    private Long id;

    @Schema(description = "设备编码，租户内唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "设备编码不能为空")
    private String code;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "设备名称不能为空")
    private String name;

    @Schema(description = "协议类型 10=ModbusTCP/11=ModbusRTU/20=OPCUA/30=S7-1200/31=S7-1500/40=MQTTSparkplug",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "协议类型不能为空")
    private Integer protocolType;

    @Schema(description = "连接参数 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "连接参数不能为空")
    private String connParamsJson;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
}
