package cn.iocoder.yudao.module.scada.controller.admin.rule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 告警规则分页查询 VO
 */
@Schema(description = "管理后台 - SCADA 告警规则分页 Request VO")
@Data
public class ScadaAlarmRulePageReqVO {

    @Schema(description = "点位 ID 精确匹配，前端按点位筛规则时用")
    private Long pointId;

    @Schema(description = "规则编码 LIKE")
    private String code;

    @Schema(description = "规则名称 LIKE")
    private String name;

    @Schema(description = "规则类型")
    private Integer ruleType;

    @Schema(description = "严重度")
    private Integer severity;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
