package cn.iocoder.yudao.module.scada.controller.admin.event.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警事件响应 VO
 */
@Schema(description = "管理后台 - SCADA 告警事件 Response VO")
@Data
public class ScadaAlarmEventRespReqVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "规则 ID")
    private Long ruleId;

    @Schema(description = "点位 ID")
    private Long pointId;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "触发时间")
    private LocalDateTime triggeredAt;

    @Schema(description = "复归时间")
    private LocalDateTime clearedAt;

    @Schema(description = "确认时间")
    private LocalDateTime ackedAt;

    @Schema(description = "确认人")
    private String ackedBy;

    @Schema(description = "触发时的值")
    private BigDecimal triggerValue;

    @Schema(description = "峰值")
    private BigDecimal peakValue;

    @Schema(description = "状态")
    private Integer state;

    @Schema(description = "严重度")
    private Integer severity;

    @Schema(description = "触发消息")
    private String message;
}
