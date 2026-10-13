package cn.iocoder.yudao.module.scada.controller.admin.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 采集任务保存请求 VO
 *
 * <p>{@code pointIdsJson} 是点位 ID 数组的 JSON 字符串，如 {@code [101,102,103]}。
 * 后端不反序列化为 List —— 调度器执行时才解析，避免在 Service 层增加无谓的对象转换。</p>
 */
@Schema(description = "管理后台 - SCADA 采集任务保存 Request VO")
@Data
public class ScadaCollectTaskSaveReqVO {

    @Schema(description = "主键 ID，编辑时必填")
    private Long id;

    @Schema(description = "任务编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务编码不能为空")
    private String code;

    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务名称不能为空")
    private String name;

    @Schema(description = "关联设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    @Schema(description = "任务类型 POLL/SUBSCRIBE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "任务类型不能为空")
    private String taskType;

    @Schema(description = "采集周期毫秒，POLL 模式必填，默认 1000")
    private Integer intervalMs;

    @Schema(description = "关联点位 ID 数组 JSON [101,102,103]", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "点位列表不能为空")
    private String pointIdsJson;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否允许并发执行")
    private Boolean concurrent;
}
