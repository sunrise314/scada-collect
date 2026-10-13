package cn.iocoder.yudao.module.scada.controller.admin.value.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 实时流订阅 VO
 *
 * <p>前端 EventSource 建立连接时传：pointIds 列表（逗号分隔字符串），
 * 后端返回 SseEmitter，每秒一帧聚合数据。</p>
 *
 * <p>pointIds 是逗号分隔字符串，避免 GET URL 数组参数序列化问题。
 * 调用方需先把 tag 转成 pointId（前端有 tag→pointId 的反查表）。</p>
 */
@Schema(description = "管理后台 - SCADA 实时流订阅 Request VO")
@Data
public class ScadaPointValueStreamReqVO {

    @Schema(description = "点位 ID 列表，逗号分隔", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "101,102,103")
    @NotEmpty(message = "点位 ID 列表不能为空")
    private String pointIds;
}
