package cn.iocoder.yudao.module.scada.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集任务响应 VO
 */
@Schema(description = "管理后台 - SCADA 采集任务 Response VO")
@Data
public class ScadaCollectTaskRespReqVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "任务编码")
    private String code;

    @Schema(description = "任务名称")
    private String name;

    @Schema(description = "关联设备 ID")
    private Long deviceId;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "采集周期毫秒")
    private Integer intervalMs;

    @Schema(description = "关联点位 ID 数组 JSON")
    private String pointIdsJson;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否允许并发")
    private Boolean concurrent;

    @Schema(description = "上次执行时间")
    private LocalDateTime lastRunAt;

    @Schema(description = "上次执行耗时毫秒")
    private Long lastDurationMs;

    @Schema(description = "上次错误信息")
    private String lastError;

    @Schema(description = "累计执行次数")
    private Long runCount;

    @Schema(description = "累计错误次数")
    private Long errorCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
