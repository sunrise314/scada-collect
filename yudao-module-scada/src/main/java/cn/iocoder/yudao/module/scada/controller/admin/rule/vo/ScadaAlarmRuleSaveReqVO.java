package cn.iocoder.yudao.module.scada.controller.admin.rule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 告警规则保存请求 VO
 */
@Schema(description = "管理后台 - SCADA 告警规则保存 Request VO")
@Data
public class ScadaAlarmRuleSaveReqVO {

    @Schema(description = "主键 ID，编辑时必填")
    private Long id;

    @Schema(description = "规则编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "规则编码不能为空")
    private String code;

    @Schema(description = "规则名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "规则名称不能为空")
    private String name;

    @Schema(description = "关联点位 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位 ID 不能为空")
    private Long pointId;

    @Schema(description = "规则类型 1=THRESHOLD/2=BAND/3=RATE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "规则类型不能为空")
    private Integer ruleType;

    @Schema(description = "阈值上限")
    private BigDecimal highLimit;

    @Schema(description = "阈值下限")
    private BigDecimal lowLimit;

    @Schema(description = "变化率阈值，仅 RATE")
    private BigDecimal rateLimit;

    @Schema(description = "触发延迟毫秒，默认 2000")
    private Integer onDelayMs;

    @Schema(description = "复归延迟毫秒，默认 5000")
    private Integer offDelayMs;

    @Schema(description = "死区百分比")
    private BigDecimal deadBand;

    @Schema(description = "严重度 1=LOW/2=MEDIUM/3=HIGH/4=CRITICAL")
    private Integer severity;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "通知模板")
    private String notifyTemplate;

    @Schema(description = "备注")
    private String remark;
}
