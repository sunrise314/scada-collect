package cn.iocoder.yudao.module.scada.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 采集任务分页查询 VO
 */
@Schema(description = "管理后台 - SCADA 采集任务分页 Request VO")
@Data
public class ScadaCollectTaskPageReqVO {

    @Schema(description = "设备 ID 精确匹配，前端按设备筛选任务时用")
    private Long deviceId;

    @Schema(description = "任务编码 LIKE")
    private String code;

    @Schema(description = "任务名称 LIKE")
    private String name;

    @Schema(description = "任务类型 POLL/SUBSCRIBE")
    private String taskType;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
