package cn.iocoder.yudao.module.scada.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备响应 VO
 *
 * <p>响应 VO 不用直接返回 DO——DO 暴露了 deleted/tenantId 等内部字段，
 * 也容易在前端期望字段固定时被后端 DO 改动打破。</p>
 */
@Schema(description = "管理后台 - SCADA 设备 Response VO")
@Data
public class ScadaDeviceRespReqVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "设备编码")
    private String code;

    @Schema(description = "设备名称")
    private String name;

    @Schema(description = "协议类型")
    private Integer protocolType;

    @Schema(description = "连接参数 JSON")
    private String connParamsJson;

    @Schema(description = "运行状态")
    private Integer status;

    @Schema(description = "最近心跳时间")
    private LocalDateTime lastHeartbeatAt;

    @Schema(description = "最近错误")
    private String lastError;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
