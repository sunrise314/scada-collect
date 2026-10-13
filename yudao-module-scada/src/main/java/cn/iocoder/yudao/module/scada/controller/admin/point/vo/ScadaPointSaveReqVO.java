package cn.iocoder.yudao.module.scada.controller.admin.point.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 点位保存请求 VO
 */
@Schema(description = "管理后台 - SCADA 点位保存 Request VO")
@Data
public class ScadaPointSaveReqVO {

    @Schema(description = "主键 ID，编辑时必填")
    private Long id;

    @Schema(description = "设备 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "设备 ID 不能为空")
    private Long deviceId;

    @Schema(description = "点位标签，租户内唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "点位标签不能为空")
    private String tag;

    @Schema(description = "点位名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "点位名称不能为空")
    private String name;

    @Schema(description = "协议地址", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "协议地址不能为空")
    private String address;

    @Schema(description = "点位类型 1=BOOL/2=INT/3=FLOAT/4=STR", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "点位类型不能为空")
    private Integer pointType;

    @Schema(description = "工程单位")
    private String engUnit;

    @Schema(description = "工程量缩放系数，默认 1")
    private BigDecimal scale;

    @Schema(description = "工程量零点偏移，默认 0")
    private BigDecimal offset;

    @Schema(description = "量程下限")
    private BigDecimal minVal;

    @Schema(description = "量程上限")
    private BigDecimal maxVal;

    @Schema(description = "死区百分比 0-100")
    private BigDecimal deadBand;

    @Schema(description = "扫描周期毫秒，建议 ≥ 1000")
    private Integer scanRateMs;

    @Schema(description = "读写标志 R/W/RW")
    private String rwFlag;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;
}
