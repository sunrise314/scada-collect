package cn.iocoder.yudao.module.scada.controller.admin.value.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史曲线查询 VO
 *
 * <p>前端曲线组件用：传 tag + 时间范围，返回 [{ts, valueNum}, ...] 数组。</p>
 *
 * <p>limit 默认 5000，超过 5000 建议改走 {@link ScadaPointValueStreamReqVO} 的降采样接口。</p>
 */
@Schema(description = "管理后台 - SCADA 历史曲线 Request VO")
@Data
public class ScadaPointValueHistoryReqVO {

    @Schema(description = "点位标签", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "点位标签不能为空")
    private String tag;

    @Schema(description = "起始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "起始时间不能为空")
    private LocalDateTime from;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime to;

    @Schema(description = "返回点数上限，默认 5000")
    private Integer limit;
}
