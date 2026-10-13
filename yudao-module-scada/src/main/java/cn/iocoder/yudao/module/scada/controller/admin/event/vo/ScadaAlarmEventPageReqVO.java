package cn.iocoder.yudao.module.scada.controller.admin.event.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警事件分页查询 VO
 */
@Schema(description = "管理后台 - SCADA 告警事件分页 Request VO")
@Data
public class ScadaAlarmEventPageReqVO {

    @Schema(description = "设备 ID 精确匹配")
    private Long deviceId;

    @Schema(description = "规则 ID 精确匹配")
    private Long ruleId;

    @Schema(description = "点位 ID 精确匹配")
    private Long pointId;

    @Schema(description = "状态 0=正常/1=未确认/2=已确认/3=复归未确认")
    private Integer state;

    @Schema(description = "严重度")
    private Integer severity;
}
