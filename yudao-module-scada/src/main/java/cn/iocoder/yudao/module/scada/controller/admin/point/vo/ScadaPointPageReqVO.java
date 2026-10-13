package cn.iocoder.yudao.module.scada.controller.admin.point.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 点位分页查询 VO
 */
@Schema(description = "管理后台 - SCADA 点位分页 Request VO")
@Data
public class ScadaPointPageReqVO {

    @Schema(description = "设备 ID 精确匹配")
    private Long deviceId;

    @Schema(description = "点位标签 LIKE")
    private String tag;

    @Schema(description = "点位名称 LIKE")
    private String name;

    @Schema(description = "点位类型")
    private Integer pointType;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
