package cn.iocoder.yudao.module.scada.controller.admin.point.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 点位响应 VO
 */
@Schema(description = "管理后台 - SCADA 点位 Response VO")
@Data
public class ScadaPointRespReqVO {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "设备 ID")
    private Long deviceId;

    @Schema(description = "点位标签")
    private String tag;

    @Schema(description = "点位名称")
    private String name;

    @Schema(description = "协议地址")
    private String address;

    @Schema(description = "点位类型")
    private Integer pointType;

    @Schema(description = "工程单位")
    private String engUnit;

    @Schema(description = "工程量缩放系数")
    private BigDecimal scale;

    @Schema(description = "工程量零点偏移")
    private BigDecimal offset;

    @Schema(description = "量程下限")
    private BigDecimal minVal;

    @Schema(description = "量程上限")
    private BigDecimal maxVal;

    @Schema(description = "死区百分比")
    private BigDecimal deadBand;

    @Schema(description = "扫描周期毫秒")
    private Integer scanRateMs;

    @Schema(description = "读写标志")
    private String rwFlag;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
